package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatus
import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatusReader

class FakeBattery(var status: BatteryStatus = BatteryStatus(50, false)) : BatteryStatusReader {
    override fun read() = status
}
