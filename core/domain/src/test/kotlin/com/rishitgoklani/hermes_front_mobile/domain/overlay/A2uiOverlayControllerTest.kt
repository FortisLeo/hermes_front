package com.rishitgoklani.hermes_front_mobile.domain.overlay

import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface
import com.rishitgoklani.hermes_front_mobile.testing.FakeOverlayPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class A2uiOverlayControllerTest {
    private val card = Json.parseToJsonElement("""{"type":"Text","text":"hi"}""").jsonObject
    private var now = 0L
    private val store = A2uiStore { now }
    private val port = FakeOverlayPort()

    private fun TestScope.controller() = A2uiOverlayController(store, port, backgroundScope, 1_000).also { it.start(); runCurrent() }

    @Test fun showsOverlaySurfaceInWindow() = runTest {
        val c = controller(); store.show(Surface.Overlay, card, "hi"); runCurrent()
        assertEquals(1, port.shown.size); assertTrue(port.visible); assertFalse(c.fallback.value)
    }

    @Test fun otherSurfacesDoNotOpenTheWindow() = runTest {
        controller(); store.show(Surface.Desk, card); store.show(Surface.Full, card); runCurrent(); assertTrue(port.shown.isEmpty())
    }

    @Test fun clearingHidesWindow() = runTest {
        controller(); store.show(Surface.Overlay, card); runCurrent(); store.clear(Surface.Overlay); runCurrent()
        assertEquals(1, port.hides); assertFalse(port.visible)
    }

    @Test fun userDismissRemovesScreenAndHides() = runTest {
        controller(); store.show(Surface.Overlay, card); runCurrent(); port.userDismiss(); runCurrent()
        assertTrue(store.screens.value[Surface.Overlay] == null); assertFalse(port.visible)
    }

    @Test fun newScreenReplacesContent() = runTest {
        controller(); store.show(Surface.Overlay, card); runCurrent(); store.show(Surface.Overlay, card, "again"); runCurrent()
        assertEquals(2, port.shown.size); assertEquals("again", port.shown.last().speak)
    }

    @Test fun missingPermissionFallsBackToInAppCard() = runTest {
        port.allowed = false; val c = controller(); store.show(Surface.Overlay, card); runCurrent()
        assertTrue(port.shown.isEmpty()); assertTrue(c.fallback.value)
        store.clear(Surface.Overlay); runCurrent(); assertFalse(c.fallback.value)
    }

    @Test fun windowFailureFallsBackWithoutCrashing() = runTest {
        port.failing = true; val c = controller(); store.show(Surface.Overlay, card); runCurrent(); assertTrue(c.fallback.value)
    }

    @Test fun expiredScreenIsSweptAndHidden() = runTest {
        controller(); store.show(Surface.Overlay, card, ttlMs = 5_000); runCurrent()
        now = 6_000; advanceTimeBy(1_500); runCurrent()
        assertFalse(port.visible); assertEquals(1, port.hides)
    }
}
