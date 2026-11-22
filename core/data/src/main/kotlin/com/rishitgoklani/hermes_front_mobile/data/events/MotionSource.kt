package com.rishitgoklani.hermes_front_mobile.data.events

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.sense.AccelSample
import com.rishitgoklani.hermes_front_mobile.domain.sense.MotionDetector
import com.rishitgoklani.hermes_front_mobile.domain.sense.MotionKind

/** Feeds the accelerometer into [MotionDetector] (~5 Hz, negligible battery) and reports events. */
class MotionSource(private val context: Context, private val onMotion: (MotionKind) -> Unit) {
    private val detector = MotionDetector()
    private var listener: SensorEventListener? = null
    val recent = java.util.concurrent.ConcurrentLinkedDeque<Pair<Long, MotionKind>>()

    fun start() {
        if (listener != null) return
        val sm = context.getSystemService(SensorManager::class.java); val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                detector.feed(AccelSample(e.values[0], e.values[1], e.values[2], System.currentTimeMillis()))?.let { k ->
                    recent.addFirst(System.currentTimeMillis() to k); while (recent.size > 20) recent.removeLast(); onMotion(k)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() { listener?.let { context.getSystemService(SensorManager::class.java).unregisterListener(it) }; listener = null }
    fun toEvent(k: MotionKind) = DeviceEvent.Motion(k.name.lowercase())
}
