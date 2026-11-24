package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatus
import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatusReader

/** Sticky ACTION_BATTERY_CHANGED read: no receiver, no polling cost beyond the call. */
class AndroidBatteryStatusReader(private val context: Context) : BatteryStatusReader {
    override fun read(): BatteryStatus {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryStatus(-1, false)
        return BatteryStatus(BatteryMath.percent(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)), BatteryMath.isCharging(i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)))
    }
}
