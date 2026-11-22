package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class BatteryReading(val percent: Int, val charging: Boolean, val tempC: Double)

object BatteryMath {
    fun percent(level: Int, scale: Int) = if (level < 0 || scale <= 0) -1 else level * 100 / scale
    fun tempC(tenths: Int) = tenths / 10.0
    fun isCharging(status: Int) = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

fun batteryCapability(context: Context) = Capability("battery", "Report battery percent, charging state and temperature.") {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return@Capability ActionResult.failed("Battery info unavailable")
    val r = BatteryReading(
        BatteryMath.percent(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)),
        BatteryMath.isCharging(i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
        BatteryMath.tempC(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)),
    )
    ActionResult.completed("Battery ${r.percent}%${if (r.charging) ", charging" else ""}", buildJsonObject {
        put("percent", r.percent); put("charging", r.charging); put("temp_c", r.tempC)
    })
}
