package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VrmInfo(val title: String, val author: String, val license: String, val version: String)

sealed interface VrmCheck {
    data class Ok(val info: VrmInfo) : VrmCheck
    data class Rejected(val reason: String) : VrmCheck
}

/** Reads the VRM metadata out of the GLB and accepts only permissive model licenses (CC0, CC-BY, CC-BY-SA, VRM "everyone"). */
object VrmLicense {
    const val MAX_BYTES = 50 * 1024 * 1024
    private val allowed0 = setOf("cc0", "cc_by", "cc_by_sa")

    /** [enforceLicense] = false (the user's own files): still requires a readable VRM, but any license is accepted. */
    fun check(glb: ByteArray, enforceLicense: Boolean = true): VrmCheck {
        if (glb.size > MAX_BYTES) return VrmCheck.Rejected("model larger than 50 MB")
        if (glb.size < 20 || String(glb, 0, 4, Charsets.US_ASCII) != "glTF") return VrmCheck.Rejected("not a GLB/VRM file")
        fun le(o: Int) = (glb[o].toInt() and 255) or (glb[o + 1].toInt() and 255 shl 8) or (glb[o + 2].toInt() and 255 shl 16) or (glb[o + 3].toInt() and 255 shl 24)
        val len = le(12); if (len <= 0 || 20 + len > glb.size) return VrmCheck.Rejected("corrupt GLB header")
        val root = runCatching { Json.parseToJsonElement(String(glb, 20, len, Charsets.UTF_8)).jsonObject }.getOrNull() ?: return VrmCheck.Rejected("unreadable model metadata")
        val ext = root["extensions"] as? JsonObject
        (ext?.get("VRMC_vrm") as? JsonObject)?.let { return v1(it, enforceLicense) }
        (ext?.get("VRM") as? JsonObject)?.let { return v0(it, enforceLicense) }
        return VrmCheck.Rejected("no VRM extension: not a VRM model")
    }

    private fun v0(vrm: JsonObject, enforce: Boolean): VrmCheck {
        val meta = vrm["meta"] as? JsonObject ?: if (enforce) return VrmCheck.Rejected("VRM has no license metadata") else JsonObject(emptyMap())
        val lic = meta["licenseName"]?.jsonPrimitive?.contentOrNull ?: if (enforce) return VrmCheck.Rejected("VRM has no licenseName") else "unspecified"
        if (enforce && lic.lowercase() !in allowed0) return VrmCheck.Rejected("license '$lic' is not permissive (need CC0, CC_BY or CC_BY_SA)")
        return VrmCheck.Ok(VrmInfo(meta["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), meta["author"]?.jsonPrimitive?.contentOrNull.orEmpty(), lic, "0"))
    }

    private fun v1(vrm: JsonObject, enforce: Boolean): VrmCheck {
        val meta = vrm["meta"] as? JsonObject ?: if (enforce) return VrmCheck.Rejected("VRM has no license metadata") else JsonObject(emptyMap())
        val url = meta["licenseUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val everyone = meta["avatarPermission"]?.jsonPrimitive?.contentOrNull == "everyone"
        val cc = "creativecommons.org/publicdomain/zero" in url || "creativecommons.org/licenses/by/" in url || "creativecommons.org/licenses/by-sa/" in url
        if (enforce && !everyone && !cc) return VrmCheck.Rejected("license not permissive (avatarPermission must be everyone, or a CC0/CC-BY url)")
        val authors = (meta["authors"] as? kotlinx.serialization.json.JsonArray)?.joinToString { it.jsonPrimitive.content }.orEmpty()
        return VrmCheck.Ok(VrmInfo(meta["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), authors, url.ifEmpty { "everyone" }, "1"))
    }
}

/** avatar_import: stage a .vrm from a phone path and install it as the avatar. It is the user's own file, so only the VRM structure is checked, not the license. */
fun avatarImportCapability(stage: suspend (String) -> ByteArray?, install: suspend (ByteArray) -> Unit) = Capability(
    "avatar_import", "Import a VRM avatar model from a file path on the phone (any license; the file must be a valid VRM).",
    """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""",
) { a ->
    val path = a["path"]?.jsonPrimitive?.contentOrNull ?: return@Capability ActionResult.failed("path required")
    val bytes = stage(path) ?: return@Capability ActionResult.failed("cannot read $path")
    when (val c = VrmLicense.check(bytes, enforceLicense = false)) {
        is VrmCheck.Rejected -> ActionResult.failed("Model rejected: ${c.reason}")
        is VrmCheck.Ok -> { install(bytes); ActionResult.completed("Imported '${c.info.title}' by ${c.info.author} (${c.info.license})") }
    }
}
