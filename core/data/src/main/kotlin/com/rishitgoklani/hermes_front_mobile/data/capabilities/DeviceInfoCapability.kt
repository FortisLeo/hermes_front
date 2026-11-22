package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.os.Build
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun deviceInfoCapability() = Capability("device_info", "Phone model, Android version and CPU architecture.") {
    ActionResult.completed("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}", buildJsonObject {
        put("model", Build.MODEL); put("android", Build.VERSION.RELEASE); put("sdk", Build.VERSION.SDK_INT)
        put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
    })
}
