package com.rishitgoklani.hermes_front_mobile.domain.a2ui

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.LocalTime
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.a2uiCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.timeNowCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiTests {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val ok = j("""{"type":"Column","children":[{"type":"Text","text":"Hi"},{"type":"Button","label":"Go","action":"x"}]}""")

    @Test fun validTreePasses() = assertTrue(A2uiValidator.validate(ok).isEmpty())
    @Test fun unknownComponentAndMissingPropsReported() {
        val e = A2uiValidator.validate(j("""{"type":"Column","children":[{"type":"Blink"},{"type":"Button","label":"x"}]}"""))
        assertTrue(e.any { it.contains("unknown component 'Blink'") }); assertTrue(e.any { it.contains("missing 'action'") })
    }
    @Test fun nonContainerCannotHaveChildren() = assertTrue(A2uiValidator.validate(j("""{"type":"Text","text":"x","children":[{"type":"Text","text":"y"}]}""")).any { it.contains("cannot have children") })
    @Test fun depthAndSizeLimits() {
        var s = """{"type":"Text","text":"leaf"}"""; repeat(10) { s = """{"type":"Column","children":[$s]}""" }
        assertTrue(A2uiValidator.validate(j(s)).any { it.contains("nesting") })
        val many = (1..250).joinToString(",") { """{"type":"Divider"}""" }; assertTrue(A2uiValidator.validate(j("""{"type":"Column","children":[$many]}""")).any { it.contains("more than") })
    }
    @Test fun childrenMustBeArrayOfObjects() { assertTrue(A2uiValidator.validate(j("""{"type":"Column","children":"nope"}""")).isNotEmpty()); assertTrue(A2uiValidator.validate(j("""{"type":"Column","children":[1]}""")).isNotEmpty()) }
    @Test fun everyTemplateValidatesAgainstCatalog() {
        val data = j("""{"title":"Morning","items":["Meeting at 9","Rain at 5"],"label":"Tea","personas":[{"id":"default","name":"Default"}]}""")
        A2uiTemplates.names.forEach { n -> assertNull(n, null); val t = A2uiTemplates.build(n, data); assertNotNull(n, t); assertEquals(n, emptyList<String>(), A2uiValidator.validate(t!!)) }
        assertNull(A2uiTemplates.build("nope", data))
    }
    @Test fun deviceToggleActionsAreRealToolsOrNull() {
        assertEquals("""tool:torch {"on":true}""", DeviceToggles.action("torch", true)); assertEquals("""tool:screen_dim {"percent":0}""", DeviceToggles.action("dim", false)); assertNull(DeviceToggles.action("fusion-reactor", true))
        val known = java.io.File("../../hermes-skills/tools.txt").readLines().toSet()
        DeviceToggles.supported.forEach { d -> val a = DeviceToggles.action(d, true)!!; assertTrue(a, ParsedAction.parse(a).let { it is ParsedAction.Tool && it.name in known }) }
    }
    @Test fun storeShowClearAndValues() {
        val s = A2uiStore(); s.show(Surface.Desk, ok, "hi"); s.show(Surface.Desk, ok); assertEquals(2L, s.screens.value[Surface.Desk]!!.version)
        s.setValue("wifi", JsonPrimitive(true)); assertEquals(JsonPrimitive(true), s.value("wifi")); assertEquals("""{"wifi":true}""", s.snapshotValues().toString())
        s.clear(Surface.Desk); assertTrue(s.screens.value.isEmpty())
    }
    @Test fun parseActions() {
        assertEquals(ParsedAction.Dismiss, ParsedAction.parse("dismiss"))
        assertEquals(ParsedAction.Tool("torch", j("""{"on":true}""")), ParsedAction.parse("""tool:torch {"on":true}"""))
        assertEquals(ParsedAction.Tool("battery", JsonObject(emptyMap())), ParsedAction.parse("tool:battery"))
        assertEquals(ParsedAction.ToHermes("tool:torch {bad"), ParsedAction.parse("tool:torch {bad")); assertEquals(ParsedAction.ToHermes("confirm"), ParsedAction.parse("confirm"))
    }

    private val reg = CapabilityRegistry(listOf(Capability("torch", "t") { a -> ActionResult.completed(a.toString()) }))
    @Test fun uiShowValidatesStoresSpeaksAndNotifies() = runBlocking {
        val store = A2uiStore(); val tts = FakeSpeechSynthesizer(); val shown = mutableListOf<Surface>(); val cap = a2uiCapabilities(store, tts) { shown += it }.first()
        val r = cap.executor.execute(j("""{"surface":"full","screen":${ok},"speak":"Here you go"}"""))
        assertEquals("completed", r.status); assertEquals(listOf(Surface.Full), shown); assertEquals(listOf("Here you go"), tts.spoken); assertNotNull(store.screens.value[Surface.Full])
    }
    @Test fun uiShowRejectsInvalidAndUnknownTemplate() = runBlocking {
        val cap = a2uiCapabilities(A2uiStore(), FakeSpeechSynthesizer()) {}.first()
        assertTrue(cap.executor.execute(j("""{"screen":{"type":"Blink"}}""")).message.startsWith("invalid screen")); assertEquals("failed", cap.executor.execute(j("""{"template":"zzz"}""")).status)
        assertEquals("failed", cap.executor.execute(j("{}")).status); assertEquals("failed", cap.executor.execute(j("""{"surface":"moon","screen":${ok}}""")).status)
    }
    @Test fun uiShowTemplateWorks() = runBlocking { assertEquals("completed", a2uiCapabilities(A2uiStore(), FakeSpeechSynthesizer()) {}.first().executor.execute(j("""{"template":"status"}""")).status) }
    @Test fun actionRoutingToolDismissAndHermes() = runBlocking {
        val store = A2uiStore().also { it.show(Surface.Desk, ok) }; val gw = FakeHermesGateway(listOf("Done. ", "<expr happy/>Anything else?")); val tts = FakeSpeechSynthesizer()
        val h = HandleUiAction(store, RunCapability(reg), gw, tts)
        assertEquals("torch: completed", h("""tool:torch {"on":true}""")); assertEquals("sent to Hermes", h("confirm")); assertTrue(gw.sent.single().startsWith("[ui-action confirm")); assertEquals(listOf("Done.", "Anything else?"), tts.spoken)
        assertEquals("dismissed", h("dismiss")); assertTrue(store.screens.value.isEmpty())
    }
}

class WebEmbedValidationTest {
    @Test fun webEmbedNeedsHtml() {
        val ok = Json.parseToJsonElement("""{"type":"WebEmbed","html":"<b>x</b>"}""").jsonObject; assertTrue(A2uiValidator.validate(ok).isEmpty())
        assertTrue(A2uiValidator.validate(Json.parseToJsonElement("""{"type":"WebEmbed"}""").jsonObject).any { it.contains("missing 'html'") })
    }
}

class A2uiExpiryAndTimeTests {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val ok = j("""{"type":"Text","text":"hi"}""")

    @Test fun screensExpireAfterTtl() { var t = 0L; val s = A2uiStore { t }; s.show(Surface.Desk, ok, ttlMs = 5000); t = 4999; assertTrue(!s.sweep()); t = 5000; assertTrue(s.sweep()); assertTrue(s.screens.value.isEmpty()) }
    @Test fun zeroTtlNeverExpires() { var t = 0L; val s = A2uiStore { t }; s.show(Surface.Full, ok); t = 10_000_000; assertTrue(!s.sweep()); assertEquals(1, s.screens.value.size) }
    @Test fun actionHidesScreensUnlessKept() {
        val s = A2uiStore(); s.show(Surface.Desk, ok); s.show(Surface.Full, ok, keepOnAction = true); s.onActionDone(); assertEquals(setOf(Surface.Full), s.screens.value.keys)
    }
    @Test fun handledActionsHideTheScreen() = runBlocking {
        val store = A2uiStore().also { it.show(Surface.Desk, ok) }; val reg = CapabilityRegistry(listOf(Capability("torch", "t") { ActionResult.completed("ok") }))
        HandleUiAction(store, RunCapability(reg), FakeHermesGateway(listOf("ok.")), FakeSpeechSynthesizer())("""tool:torch {"on":true}"""); assertTrue(store.screens.value.isEmpty())
        store.show(Surface.Desk, ok); HandleUiAction(store, RunCapability(reg), FakeHermesGateway(listOf("ok.")), FakeSpeechSynthesizer())("confirm"); assertTrue(store.screens.value.isEmpty())
    }
    @Test fun uiShowSetsDefaultAndCustomTtlAndKeep() = runBlocking {
        var t = 1000L; val store = A2uiStore { t }; val cap = a2uiCapabilities(store, FakeSpeechSynthesizer()) {}.first()
        cap.executor.execute(j("""{"screen":$ok}""")); assertEquals(1000L + 90_000, store.screens.value[Surface.Desk]!!.expiresAt)
        cap.executor.execute(j("""{"screen":$ok,"ttl_s":0,"keep":true,"surface":"full"}""")); assertEquals(null, store.screens.value[Surface.Full]!!.expiresAt); assertTrue(store.screens.value[Surface.Full]!!.keepOnAction)
        cap.executor.execute(j("""{"screen":$ok,"ttl_s":999999}""")); assertEquals(1000L + 3_600_000, store.screens.value[Surface.Desk]!!.expiresAt)
    }
    @Test fun timeToolDescribesLocalTime() = runBlocking {
        val fixed = java.time.ZonedDateTime.of(2026, 11, 23, 0, 42, 5, 0, java.time.ZoneId.of("Asia/Kolkata"))
        val r = timeNowCapability { fixed }.executor.execute(JsonObject(emptyMap()))
        assertEquals("Monday 23 November 2026, 00:42 (Asia/Kolkata, UTC+05:30)", r.message); assertEquals("night", r.data!!["part_of_day"]!!.jsonPrimitive.content); assertEquals("2026-11-23", r.data!!["date"]!!.jsonPrimitive.content)
        assertEquals("morning", LocalTime.partOfDay(9)); assertEquals("evening", LocalTime.partOfDay(18))
    }
}
