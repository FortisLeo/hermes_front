package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.testing.FakeSkillSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    private val screenText = "---\nname: deskbot-screen\ndescription: \"Read, summarize and watch the phone screen.\"\nversion: 1.0.0\n---\n# Screen\nUse ui_tree."
    private val source = FakeSkillSource(mapOf("screen" to screenText, "apps" to "---\nname: deskbot-apps\ndescription: Launch and manage apps.\n---\nbody"))
    private fun args(name: String?) = if (name == null) JsonObject(emptyMap()) else buildJsonObject { put("name", JsonPrimitive(name)) }

    @Test fun theHeaderGivesTheNameAndDescriptionWithoutQuotes() {
        val i = SkillParser.parse("screen", screenText)
        assertEquals("deskbot-screen", i.name); assertEquals("Read, summarize and watch the phone screen.", i.description); assertEquals("screen", i.id)
    }
    @Test fun aSkillWithoutAHeaderFallsBackToItsFolderName() = assertEquals(SkillInfo("x", "x", ""), SkillParser.parse("x", "just text"))
    @Test fun unquotedDescriptionsWork() = assertEquals("Launch and manage apps.", source.list().first { it.id == "apps" }.description)

    @Test fun readsByFolderIdByFullNameOrWithoutThePrefix() {
        val reader = ReadSkill(source)
        listOf("screen", "deskbot-screen", "Screen", " DESKBOT-SCREEN ").forEach { assertTrue(it, "Use ui_tree" in reader(args(it)).message) }
    }
    @Test fun anUnknownSkillListsWhatExists() {
        val r = ReadSkill(source)(args("teleport"))
        assertEquals("failed", r.status); assertTrue("screen" in r.message && "apps" in r.message)
    }
    @Test fun aMissingNameAlsoListsTheSkills() = assertTrue("Available" in ReadSkill(source)(args(null)).message)
    @Test fun veryLongGuidesAreCutWithAMarker() {
        val r = ReadSkill(FakeSkillSource(mapOf("big" to "---\nname: deskbot-big\n---\n" + "x".repeat(20_000))), maxChars = 100)(args("big"))
        assertTrue(r.message.length < 200 && r.message.endsWith("[skill truncated]"))
    }

    @Test fun theLiveToolIsNamedReadSkillAndReturnsTheGuide() = runTest {
        val t = LiveSkillTool.create(source)
        assertEquals("read_skill", t.decl.name); assertTrue(t.decl.parametersJson!!.contains("\"required\":[\"name\"]"))
        assertEquals("completed", t.handler(args("apps")).status)
    }

    @Test fun thePromptListsEverySkillAndTellsItHowToTranslateToolNames() {
        val p = LivePrompt.build("Bot", skills = source.list())
        assertTrue("- screen: Read, summarize and watch the phone screen" in p && "- apps: Launch and manage apps" in p)
        assertTrue("deskbot_" in p && "shell tool" in p && "read_skill" in p)
    }
    @Test fun withoutSkillsThePromptMentionsNone() = assertFalse("Available skills" in LivePrompt.build("Bot"))
}
