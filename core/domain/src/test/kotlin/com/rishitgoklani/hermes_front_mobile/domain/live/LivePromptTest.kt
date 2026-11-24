package com.rishitgoklani.hermes_front_mobile.domain.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePromptTest {
    @Test fun namesTheBotAndAsksForShortSpokenReplies() {
        val p = LivePrompt.build("Banana")
        assertTrue("You are Banana" in p); assertTrue("short" in p && "no markdown" in p)
    }
    @Test fun mentionsTheEndConversationTool() = assertTrue(RunLiveSession.END_TOOL in LivePrompt.build())
    @Test fun addsToneAndDescriptionOnlyWhenPresent() {
        val plain = LivePrompt.build(); val styled = LivePrompt.build("X", tone = "gruff and slow", description = "a devil banana")
        assertFalse("speaking style" in plain); assertTrue("gruff and slow" in styled && "devil banana" in styled)
    }
    @Test fun theHotToolSetStaysSmall() = assertTrue(LivePrompt.HOT_TOOLS.size in 10..60)
}
