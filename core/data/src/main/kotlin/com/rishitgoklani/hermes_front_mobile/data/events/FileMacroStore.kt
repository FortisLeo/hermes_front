package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.routines.ToolCall
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/** Persists recorded macros as one JSON file; corrupt files load as empty. */
class FileMacroStore(private val file: File) {
    private val ser = MapSerializer(String.serializer(), ListSerializer(ToolCall.serializer()))
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    fun load(): Map<String, List<ToolCall>> = runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyMap())
    @Synchronized fun save(m: Map<String, List<ToolCall>>) { runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(ser, m)) } }
}
