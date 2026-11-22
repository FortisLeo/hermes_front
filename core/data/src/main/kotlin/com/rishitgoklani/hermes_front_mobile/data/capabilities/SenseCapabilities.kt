package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

object SensorNames {
    val aliases = mapOf(
        "accelerometer" to Sensor.TYPE_ACCELEROMETER, "gyroscope" to Sensor.TYPE_GYROSCOPE, "light" to Sensor.TYPE_LIGHT,
        "proximity" to Sensor.TYPE_PROXIMITY, "pressure" to Sensor.TYPE_PRESSURE, "barometer" to Sensor.TYPE_PRESSURE,
        "magnetometer" to Sensor.TYPE_MAGNETIC_FIELD, "gravity" to Sensor.TYPE_GRAVITY, "humidity" to Sensor.TYPE_RELATIVE_HUMIDITY,
        "temperature" to Sensor.TYPE_AMBIENT_TEMPERATURE, "step_counter" to Sensor.TYPE_STEP_COUNTER,
    )
    fun typeOf(name: String): Int? = aliases[name.trim().lowercase().replace(' ', '_')]
}

fun sensorCapabilities(context: Context): List<Capability> {
    val sm = context.getSystemService(SensorManager::class.java)
    return listOf(
        Capability("sensor_list", "List available sensors (names usable with sensor_read).") {
            val have = SensorNames.aliases.filter { sm.getDefaultSensor(it.value) != null }.keys
            ActionResult.completed(have.joinToString(", "), buildJsonObject { put("count", sm.getSensorList(Sensor.TYPE_ALL).size) })
        },
        Capability("sensor_read", "Read one sample from a sensor (accelerometer, gyroscope, light, proximity, pressure, magnetometer, gravity, humidity, temperature, step_counter).",
            """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""") { a ->
            val type = SensorNames.typeOf(a.str("name").orEmpty()) ?: return@Capability ActionResult.failed("Unknown sensor")
            val sensor = sm.getDefaultSensor(type) ?: return@Capability ActionResult.failed("Sensor not present")
            val values = withTimeoutOrNull(3000) {
                suspendCancellableCoroutine<FloatArray> { cont ->
                    val l = object : SensorEventListener {
                        override fun onSensorChanged(e: SensorEvent) { sm.unregisterListener(this); if (cont.isActive) cont.resume(e.values.copyOf()) }
                        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
                    }
                    sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    cont.invokeOnCancellation { sm.unregisterListener(l) }
                }
            } ?: return@Capability ActionResult.failed("No reading within 3 s")
            ActionResult.completed(values.joinToString(", ") { "%.2f".format(it) }, buildJsonObject { put("values", buildJsonArray { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }); put("unit_hint", sensor.name) })
        },
    )
}

@SuppressLint("MissingPermission")
fun locationCapability(context: Context) = Capability("location_get", "Current location (lat, lon, accuracy). Uses last known if a fresh fix is not quickly available.") {
    val lm = context.getSystemService(LocationManager::class.java)
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val fresh: Location? = withTimeoutOrNull(6000) {
        providers.firstOrNull()?.let { p ->
            suspendCancellableCoroutine<Location?> { cont -> lm.getCurrentLocation(p, null, context.mainExecutor) { cont.resume(it) } }
        }
    }
    val loc = fresh ?: providers.firstNotNullOfOrNull { lm.getLastKnownLocation(it) } ?: lm.getProviders(true).firstNotNullOfOrNull { lm.getLastKnownLocation(it) }
    if (loc == null) ActionResult.failed("No location available (is location on and permission granted?)")
    else ActionResult.completed("%.5f, %.5f (±%.0f m)".format(loc.latitude, loc.longitude, loc.accuracy), buildJsonObject {
        put("lat", loc.latitude); put("lon", loc.longitude); put("accuracy_m", loc.accuracy); put("age_ms", System.currentTimeMillis() - loc.time)
    })
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission", "HardwareIds")
fun networkInfoCapability(context: Context) = Capability("network_info", "Connectivity: transport, Wi-Fi SSID/RSSI/IP, Bluetooth on/off, metered flag.") {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    val transport = when {
        caps == null -> "none"; caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"; caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"; else -> "other"
    }
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    val info = wifi.connectionInfo
    val ip = info?.ipAddress ?: 0
    val ipStr = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    val bt = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
    ActionResult.completed("$transport, wifi ${if (wifi.isWifiEnabled) "on" else "off"} ${info?.ssid ?: ""} $ipStr", buildJsonObject {
        put("transport", transport); put("online", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        put("metered", cm.isActiveNetworkMetered); put("wifi_enabled", wifi.isWifiEnabled); put("ssid", info?.ssid?.trim('"') ?: ""); put("rssi", info?.rssi ?: 0)
        put("ip", ipStr); put("bluetooth_enabled", bt ?: false)
    })
}
