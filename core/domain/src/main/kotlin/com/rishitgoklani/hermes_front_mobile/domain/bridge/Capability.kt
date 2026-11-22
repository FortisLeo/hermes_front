package com.rishitgoklani.hermes_front_mobile.domain.bridge

import kotlinx.serialization.json.JsonObject

/** Outcome of one phone action. Status strings match what the Hermes adapter treats as success. */
data class ActionResult(val status: String, val message: String = "", val data: JsonObject? = null) {
    companion object {
        const val COMPLETED = "completed"
        const val ACCEPTED = "accepted"
        const val FAILED = "failed"
        const val DENIED = "denied"
        const val UNKNOWN_TOOL = "unknown_tool"
        fun completed(message: String = "", data: JsonObject? = null) = ActionResult(COMPLETED, message, data)
        fun failed(message: String) = ActionResult(FAILED, message)
    }
}

/** Strategy: every executor honours the same contract so decorators and ladders can swap them. */
fun interface ActionExecutor {
    suspend fun execute(args: JsonObject): ActionResult
}

/** A tool Hermes can call. [inputSchemaJson] is a JSON Schema object serialised as text. */
data class Capability(
    val name: String,
    val description: String,
    val inputSchemaJson: String = """{"type":"object","properties":{}}""",
    val executor: ActionExecutor,
)
