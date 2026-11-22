---
name: deskbot-battery-thermal
description: "Battery status, charge limit, doze, power use, thermals, low-battery behavior on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-display-power]
---

# Battery and thermal

### 151 Battery status and history
`deskbot_battery` (percent, charging, temp). Health/cycles/current: `dumpsys battery`; `cat /sys/class/power_supply/battery/{cycle_count,health,current_now,voltage_now}`. History: `dumpsys batterystats --checkin | head`.

### 152 Charge limit control
Needs the ACC module (KernelSU): `acc -s mc 80` (max charge %), `acc -s pc 70` (resume); schedule via cron; pause `acc -p`. Install per module-scout skill; verify `acc -i`. Without ACC, warn the user about 24/7 charging at 100%.

### 153 Doze and standby control
`dumpsys deviceidle whitelist +<pkg>`; bucket `am set-standby-bucket <pkg> active|working_set|rare`; battery saver `settings put global low_power 1|0`; schedule `low_power_trigger_level <pct>`.

### 154 Power use by app
`dumpsys batterystats | grep -E "Estimated power use|Uid"`; wakelocks `dumpsys power | grep -i "wake lock"`. Report the top 5 offenders.

### 155 Thermal monitoring
`deskbot_battery` temp_c; CPU/skin: `cat /sys/class/thermal/thermal_zone*/{type,temp}`; `dumpsys thermalservice`. Alert if battery > 42 °C or skin > 45 °C.

### 156 Throttle response
When hot: `deskbot_brightness level=20`, `deskbot_screen_dim percent=40`, pause heavy cron jobs, tell the user, and recheck in 5 min. Resume when < 38 °C.

### 157 Charger and dock detection
`dumpsys battery | grep -E "AC powered|USB powered|Wireless powered"`; `deskbot_battery` charging flag. Event-driven triggers: planned (battery-changed receiver).

### 158 Abnormal battery warning
Flag: temp > 45 °C, voltage drop while charging, `dumpsys battery | grep health` != 2, rapid % swings, or device back-cover bulge reported by the user. Stop charging (`acc -p` or ask the user to unplug) and notify.

### 159 Low-battery behavior
At ≤ 20% and not charging: speak once, `settings put global low_power 1`, lower brightness, reduce cron frequency; ≤ 10%: save state (backup #381) and announce shutdown risk.

### 160 Energy report
Daily cron: sample `deskbot_battery` hourly into a log (`deskbot_file_write append`), summarize charge cycles/time on battery in the morning briefing.
