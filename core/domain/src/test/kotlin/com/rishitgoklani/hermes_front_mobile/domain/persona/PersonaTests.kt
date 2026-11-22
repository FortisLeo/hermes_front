package com.rishitgoklani.hermes_front_mobile.domain.persona

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.faceCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.testing.FakePersonaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTests {
    private val ok = Persona("my-pack", "Mine", license = PersonaLicense("self", "me", "MIT", "personal"))
    private fun a(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun validPackPasses() = assertNull(PersonaValidator.check(ok))
    @Test fun rippedRefused() = assertTrue(PersonaValidator.check(ok.copy(license = ok.license.copy(ripped = true)))!!.contains("ripped"))
    @Test fun missingLicenseRefused() { assertNotNull(PersonaValidator.check(ok.copy(license = PersonaLicense()))); assertNotNull(PersonaValidator.check(ok.copy(license = ok.license.copy(license = "Unknown")))) }
    @Test fun badIdColorRangeRefused() {
        assertNotNull(PersonaValidator.check(ok.copy(id = "Bad Id"))); assertNotNull(PersonaValidator.check(ok.copy(eyeColorHex = "red"))); assertNotNull(PersonaValidator.check(ok.copy(eyeSize = 3f)))
        assertNotNull(PersonaValidator.check(ok.copy(voiceRate = 5f))); assertNotNull(PersonaValidator.check(ok.copy(blinkRateSec = 0.1f)))
    }
    @Test fun colorParsing() { assertEquals(0xFF00FF00, PersonaValidator.parseColor("#00FF00")); assertEquals(0xFF123456, PersonaValidator.parseColor("123456")); assertNull(PersonaValidator.parseColor("#12")) }
    @Test fun builtinsAreValidAndHaveDefault() { assertTrue(BuiltinPersonas.all.any { it.id == "default" }); BuiltinPersonas.all.forEach { assertNull(it.id, PersonaValidator.check(it)) } }
    @Test fun personaToTweaksAndMood() { val p = BuiltinPersonas.all.first { it.id == "sleepy-green" }; assertEquals(0xFF4CAF50, p.tweaks().colorHex); assertEquals(Mood.Sleepy, p.mood()) }

    private fun caps(repo: FakePersonaRepository = FakePersonaRepository(), applied: MutableList<Persona> = mutableListOf(), face: FaceController = FaceController(CoroutineScope(Dispatchers.Unconfined))) =
        faceCapabilities(face, repo) { applied += it }.associateBy { it.name }

    @Test fun faceSetMoodReactionAndGaze() = runBlocking {
        val face = FaceController(CoroutineScope(Dispatchers.Unconfined)); val c = caps(face = face)
        assertEquals("completed", c["face_set"]!!.executor.execute(a("""{"mood":"happy","reaction":"wink","intensity":0.6,"look_x":0.5}""")).status)
        assertEquals(Mood.Happy, face.state.value.mood); assertEquals(Reaction.Wink, face.state.value.reaction); assertEquals(0.5f, face.state.value.lookX)
    }
    @Test fun faceSetUnknownMoodListsValidOnes() = runBlocking { val r = caps()["face_set"]!!.executor.execute(a("""{"mood":"glorp"}""")); assertEquals("failed", r.status); assertTrue(r.message.contains("happy")) }
    @Test fun faceSetNeedsSomething() = runBlocking { assertEquals("failed", caps()["face_set"]!!.executor.execute(a("{}")).status) }
    @Test fun faceTweakAppliesAndValidates() = runBlocking {
        val face = FaceController(CoroutineScope(Dispatchers.Unconfined)); val c = caps(face = face)
        c["face_tweak"]!!.executor.execute(a("""{"color":"#00FF00","size":1.2,"blink_rate_s":2}""")); assertEquals(0xFF00FF00, face.state.value.tweaks.colorHex); assertEquals(1.2f, face.state.value.tweaks.sizeScale)
        assertEquals("failed", c["face_tweak"]!!.executor.execute(a("""{"color":"green"}""")).status); assertEquals("failed", c["face_tweak"]!!.executor.execute(a("""{"size":5}""")).status)
        c["face_tweak"]!!.executor.execute(a("""{"reset":true}""")); assertNull(face.state.value.tweaks.colorHex)
    }
    @Test fun personaSetSwitchesAppliesAndPreviousWorks() = runBlocking {
        val applied = mutableListOf<Persona>(); val repo = FakePersonaRepository(); val c = caps(repo, applied)
        c["persona_set"]!!.executor.execute(a("""{"id":"night-owl"}""")); c["persona_set"]!!.executor.execute(a("""{"id":"previous"}"""))
        assertEquals(listOf("night-owl", "default"), applied.map { it.id }); assertEquals("failed", c["persona_set"]!!.executor.execute(a("""{"id":"nope"}""")).status)
    }
    @Test fun personaSaveValidatesLicenseAndListShowsIt() = runBlocking {
        val repo = FakePersonaRepository(); val c = caps(repo)
        assertEquals("denied", c["persona_save"]!!.executor.execute(a("""{"persona":{"id":"rip","name":"Rip","license":{"license":"MIT","allowedUse":"x","ripped":true}}}""")).status)
        assertEquals("completed", c["persona_save"]!!.executor.execute(a("""{"persona":{"id":"mine","name":"Mine","eyeColorHex":"#112233","license":{"source":"me","license":"MIT","allowedUse":"personal"}}}""")).status)
        assertTrue(c["persona_list"]!!.executor.execute(a("{}")).message.contains("mine"))
        assertEquals("completed", c["persona_delete"]!!.executor.execute(a("""{"id":"mine"}""")).status); assertEquals("failed", c["persona_delete"]!!.executor.execute(a("""{"id":"default"}""")).status)
    }
    @Test fun personaSaveGarbageJson() = runBlocking { assertEquals("failed", caps()["persona_save"]!!.executor.execute(a("""{"persona":{"id":5}}""")).status) }
}
