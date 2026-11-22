package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.content.Intent
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object AppMatcher {
    /** Picks a package by exact package name, then exact label, then label prefix/contains (case-insensitive). */
    fun match(query: String, apps: Map<String, String>): String? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        apps.keys.firstOrNull { it.lowercase() == q }?.let { return it }
        val byLabel = apps.entries.filter { it.value.lowercase() == q }
        if (byLabel.size == 1) return byLabel.first().key
        val starts = apps.entries.filter { it.value.lowercase().startsWith(q) }
        if (starts.size == 1) return starts.first().key
        val contains = apps.entries.filter { it.value.lowercase().contains(q) }
        return contains.singleOrNull()?.key
    }
}

fun appOpenCapability(context: Context) = Capability(
    "app_open", "Open an installed app by name or package.",
    """{"type":"object","properties":{"name":{"type":"string","description":"App label or package name"}},"required":["name"]}""",
) { args ->
    val query = args["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val pm = context.packageManager
    val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    val pkg = AppMatcher.match(query, launchable) ?: return@Capability ActionResult.failed("No unique app matches '$query'")
    val intent = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: return@Capability ActionResult.failed("$pkg has no launcher activity")
    context.startActivity(intent)
    ActionResult.completed("Opened ${launchable[pkg]}")
}
