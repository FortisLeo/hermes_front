package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.SettingsGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun read(context: Context, table: String, key: String): String? = when (table) {
    "system" -> Settings.System.getString(context.contentResolver, key)
    "global" -> Settings.Global.getString(context.contentResolver, key)
    "secure" -> Settings.Secure.getString(context.contentResolver, key)
    else -> null
}

fun settingsCapabilities(context: Context): List<Capability> = listOf(
    Capability("settings_get", "Read a system/global/secure setting by key.",
        """{"type":"object","properties":{"table":{"type":"string","enum":["system","global","secure"]},"key":{"type":"string"}},"required":["table","key"]}""") { a ->
        val t = a.str("table").orEmpty(); val k = a.str("key").orEmpty()
        if (t !in SettingsGuard.tables) return@Capability ActionResult.failed("table must be system, global or secure")
        ActionResult.completed(read(context, t, k) ?: "null", buildJsonObject { put("value", read(context, t, k)) })
    },
    Capability("settings_put", "Write a setting and read it back to verify. Some provisioning keys are refused.",
        """{"type":"object","properties":{"table":{"type":"string","enum":["system","global","secure"]},"key":{"type":"string"},"value":{"type":"string"}},"required":["table","key","value"]}""") { a ->
        val t = a.str("table").orEmpty(); val k = a.str("key").orEmpty(); val v = a.str("value").orEmpty()
        if (t !in SettingsGuard.tables) return@Capability ActionResult.failed("table must be system, global or secure")
        if (SettingsGuard.isProtected(k)) return@Capability ActionResult(ActionResult.DENIED, "$k is protected")
        val ok = runCatching {
            when (t) {
                "system" -> Settings.System.putString(context.contentResolver, k, v)
                "global" -> Settings.Global.putString(context.contentResolver, k, v)
                else -> Settings.Secure.putString(context.contentResolver, k, v)
            }
        }.getOrElse { return@Capability ActionResult.failed(it.message ?: "write refused") }
        val back = read(context, t, k)
        if (ok && back == v) ActionResult.completed("$t/$k = $v") else ActionResult.failed("Write not applied (now: $back)")
    },
)

fun appCapabilities(context: Context): List<Capability> = listOf(
    Capability("app_list", "List installed apps: label, package, version; filter by text; user_only (default true) hides system apps.",
        """{"type":"object","properties":{"query":{"type":"string"},"user_only":{"type":"boolean"},"limit":{"type":"integer"}}}""") { a ->
        val pm = context.packageManager
        val userOnly = a.bool("user_only") ?: true
        val q = a.str("query")
        val apps = pm.getInstalledPackages(0).filter { !userOnly || (it.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(it.packageName) != null && (it.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .map { Triple(it.applicationInfo!!.loadLabel(pm).toString(), it.packageName, it.versionName.orEmpty()) }
            .filter { q == null || it.first.contains(q, true) || it.second.contains(q, true) }.sortedBy { it.first.lowercase() }.take(a.int("limit") ?: 80)
        ActionResult.completed(apps.joinToString("\n") { "${it.first} (${it.second}) ${it.third}" }, buildJsonObject { put("count", apps.size) })
    },
    Capability("app_info", "Details for one package: version, install/update time, size of code, enabled state, permissions granted.",
        """{"type":"object","properties":{"package":{"type":"string"}},"required":["package"]}""") { a ->
        val pm = context.packageManager; val pkg = a.str("package").orEmpty()
        val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) }.getOrNull() ?: return@Capability ActionResult.failed("Not installed")
        val granted = info.requestedPermissions.orEmpty().filterIndexed { i, _ -> (info.requestedPermissionsFlags?.getOrNull(i) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
        ActionResult.completed("${info.applicationInfo!!.loadLabel(pm)} ${info.versionName}", buildJsonObject {
            put("package", pkg); put("version", info.versionName.orEmpty()); put("version_code", info.longVersionCode)
            put("first_install", info.firstInstallTime); put("last_update", info.lastUpdateTime)
            put("enabled", info.applicationInfo!!.enabled); put("source_dir", info.applicationInfo!!.sourceDir)
            put("granted", buildJsonArray { granted.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        })
    },
    Capability("app_stop", "Kill an app's background processes (soft stop). Use shell `am force-stop` for a hard stop.",
        """{"type":"object","properties":{"package":{"type":"string"}},"required":["package"]}""") { a ->
        context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(a.str("package").orEmpty()); ActionResult.completed("Requested stop")
    },
)

fun clipboardCapabilities(context: Context): List<Capability> {
    val cm = context.getSystemService(ClipboardManager::class.java)
    return listOf(
        Capability("clipboard_get", "Read the clipboard text (Android may block background reads; works while the app/accessibility is active).") {
            val t = withContext(Dispatchers.Main) { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }
            if (t == null) ActionResult.failed("Clipboard empty or not readable") else ActionResult.completed(t, buildJsonObject { put("text", t) })
        },
        Capability("clipboard_set", "Put text on the clipboard.", """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""") { a ->
            withContext(Dispatchers.Main) { cm.setPrimaryClip(ClipData.newPlainText("deskbot", a.str("text").orEmpty())) }; ActionResult.completed("Copied")
        },
        Capability("clipboard_clear", "Clear the clipboard (hygiene).") { withContext(Dispatchers.Main) { cm.clearPrimaryClip() }; ActionResult.completed("Cleared") },
    )
}
