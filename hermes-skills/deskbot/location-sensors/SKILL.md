---
name: deskbot-location-sensors
description: "Location, navigation, geofence routines, motion, ambient light and sound, mock location, sensor logging on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-monitor]
---

# Maps, location, and sensors

### 261 Get location
`deskbot_location_get` (fresh fix with last-known fallback; includes accuracy and age). Continuous: cron polling; geofence enter/exit: compare to saved points (haversine) each poll.

### 262 Navigate
`am start -a android.intent.action.VIEW -d "google.navigation:q=<address>"` then read the next turn with `deskbot_ui_tree` (maps exposes the banner) or the navigation notification (`deskbot_notifications_list app=Maps`).

### 264 Geofence routines
Save named places (home, desk, away) in memory with lat/lon/radius; cron every 2-5 min runs `deskbot_location_get`; on transition run the routine (DND, brightness, announcements).

### 265 Room inference from Wi-Fi
Record RSSI signatures per place: `deskbot_network_info` (rssi, ssid) and `dumpsys wifi | grep -i "bssid\|rssi"`; compare to stored fingerprints with a nearest-match; mark as approximate.

### 266 Motion events
`deskbot_sensor_read name=accelerometer` repeatedly (~5 Hz in a short cron burst) and detect magnitude changes: pick-up (sustained change), tilt (gravity axis shift), knock (short spike). Native event stream planned.

### 267 Ambient light and sound level
Light: `deskbot_sensor_read name=light` → adapt brightness (`<10 lux` night: `deskbot_brightness level=10`). Sound level: planned tool; until then infer quiet hours from the clock.

### 269 Mock or fixed location
See #27: enable a mock-location app, then feed fixed coordinates; verify with `deskbot_location_get` (accuracy/age fields change).

### 270 Sensor logging
Cron: append `deskbot_sensor_read` values with timestamp to `/sdcard/deskbot/sensors.csv` via `deskbot_file_write append=true`; plot with Termux `python -c "import matplotlib"`; alert when outside baseline ±3σ.
