package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.DenyList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Decorator: refuses a call whose command text violates the deny-list, before the real executor sees it. */
class DenyListExecutor(
    private val delegate: ActionExecutor,
    private val denyList: DenyList,
    private val commandArg: String = "cmd",
) : ActionExecutor {
    override suspend fun execute(args: JsonObject): ActionResult {
        val command = args[commandArg]?.jsonPrimitive?.contentOrNull.orEmpty()
        denyList.violation(command)?.let { return ActionResult(ActionResult.DENIED, "Blocked by deny-list: $it") }
        return delegate.execute(args)
    }
}

/** Decorator: records every call and its final status. */
class AuditingExecutor(
    private val tool: String,
    private val delegate: ActionExecutor,
    private val log: AuditLog,
) : ActionExecutor {
    override suspend fun execute(args: JsonObject): ActionResult {
        val result = try { delegate.execute(args) } catch (e: Exception) {
            log.record(tool, args.toString(), "exception:${e::class.simpleName}"); throw e
        }
        log.record(tool, args.toString(), result.status)
        return result
    }
}
