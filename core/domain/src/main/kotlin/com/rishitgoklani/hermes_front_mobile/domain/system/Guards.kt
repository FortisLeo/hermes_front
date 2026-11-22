package com.rishitgoklani.hermes_front_mobile.domain.system

import java.io.File

/** Restricts file tools to allowed roots and rejects traversal. Paths are canonicalised before checking. */
class FileSandbox(private val roots: List<File>) {
    fun resolve(path: String): File? {
        if (path.isBlank() || path.contains('\u0000')) return null
        val f = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return f.takeIf { c -> roots.any { r -> val root = r.canonicalFile; c == root || c.path.startsWith(root.path + File.separator) } }
    }
}

/** Settings keys that can break provisioning or lock the owner out; refused by settings_put. */
object SettingsGuard {
    private val protectedKeys = setOf("device_provisioned", "user_setup_complete", "secure_frp_mode", "lock_screen_lock_after_timeout_disabled")
    val tables = setOf("system", "global", "secure")
    fun isProtected(key: String) = key.lowercase() in protectedKeys
}

/** Only http(s) URLs; blocks credentials-in-URL tricks and non-web schemes. */
object UrlGuard {
    fun check(url: String): String? {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return "invalid URL"
        if (u.scheme?.lowercase() !in setOf("http", "https")) return "only http/https allowed"
        if (u.host.isNullOrEmpty()) return "missing host"
        if (u.userInfo != null) return "credentials in URL not allowed"
        return null
    }
}
