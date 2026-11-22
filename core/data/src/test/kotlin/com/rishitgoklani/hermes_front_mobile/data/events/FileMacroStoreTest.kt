package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.macro.MacroRecorder
import com.rishitgoklani.hermes_front_mobile.domain.routines.ToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileMacroStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun macrosSurviveRestartAndDeleteIsPersisted() {
        val f = File(tmp.root, "m/macros.json"); val store = FileMacroStore(f)
        val rec = MacroRecorder(onChange = store::save); rec.start("flash"); rec.record("torch", JsonObject(mapOf("on" to JsonPrimitive(true)))); rec.stop()
        val again = MacroRecorder(onChange = store::save).also { it.load(FileMacroStore(f).load()) }; assertEquals(listOf("flash"), again.names()); assertEquals("torch", again.get("flash")!!.single().tool)
        assertTrue(again.delete("flash")); assertEquals(emptyMap<String, List<ToolCall>>(), FileMacroStore(f).load())
    }
    @Test fun corruptFileLoadsEmpty() { val f = tmp.newFile("bad.json"); f.writeText("{{{"); assertEquals(emptyMap<String, List<ToolCall>>(), FileMacroStore(f).load()) }
}
