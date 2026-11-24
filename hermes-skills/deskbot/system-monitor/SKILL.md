---
name: deskbot-system-monitor
description: "Device info, CPU/memory, sensors, health score, logs, dumpsys, diagnostics, crash bundles on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-battery-thermal]
---

# System info, monitoring, logs and diagnostics

### 181 Device info report
`deskbot_system_report sections=["kernel"]` gives kernel and build fingerprint; `deskbot_device_info` gives model and Android version. Root: `ksud --version; su -v` (KernelSU Next present).

### 182 CPU, memory, and process monitor
One call: `deskbot_system_report sections=["cpu","memory"]` (top processes and free RAM/swap). Kill a hog: `am force-stop <pkg>` (ask first unless it is the bot itself).

### 183 Uptime and reboot reasons
`deskbot_system_report sections=["uptime"]`. Reboot reasons (shell): `getprop sys.boot.reason; getprop ro.boot.bootreason`; crash history `ls /data/system/dropbox | tail`, pstore `ls /sys/fs/pstore`.

### 184 Sensor inventory and reads
`deskbot_sensor_list`, `deskbot_sensor_read name=light|accelerometer|proximity|magnetometer|step_counter`. Full list: `dumpsys sensorservice | head -60`.

### 185 Hardware capability check
`pm list features`; cameras `dumpsys media.camera | grep -i "camera id"`; codecs `dumpsys media.codec | head`; GPU `dumpsys SurfaceFlinger | grep -i GLES`; NFC presence `pm list features | grep nfc`.

### 186 Health score
Combine: battery % and temp (`deskbot_battery`), free storage (`df /data`), network online (`deskbot_network_info`), recent errors (`logcat -d -b crash | wc -l`), bridge tool failures (audit log). Output a 0-100 score and the top issue.

### 187 Clock sync
`deskbot_time_now` returns the phone's local date, time, weekday, zone and part of day (also sent to you as context on every voice/chat turn). `date`; NTP: `cmd time_detector`; set auto time `settings put global auto_time 1`; drift check `curl -sI https://www.google.com | grep -i ^date` vs `date -u`.

### 188 Scheduled health checks
Hermes cron hourly: run #186, store in memory, alert via `deskbot_notification_post` or voice when thresholds break (battery temp, storage < 10%, offline).

### 189 Property inspection
`getprop | grep -i <word>`; `getprop ro.product.model`; vendor props `getprop | grep vendor`. Read-only unless asked; `setprop` only for debug flags.

### 190 Environment snapshot
Save a snapshot file with: `getprop`, `settings list system/global/secure`, `pm list packages -f`, `dumpsys battery`, `df`, `ip addr`. Store as `/sdcard/deskbot-backup/env_<date>.txt`; diff two snapshots after a change.

### 191 Read logcat
`deskbot_device_logs kind=recent|errors|crash lines=20`. For a package filter use shell `logcat -d --pid=$(pidof <pkg>)`.

### 192 Explain an error
Fetch the stack/error lines (#191), search the web for the exact message if unknown (`web_search`), and give cause + fix in two sentences. Check Deskbot audit log first for tool-call failures.

### 193 Kernel messages
`deskbot_device_logs kind=kernel lines=30`.

### 194 Dump system services
`dumpsys <service> | head -200` (activity, window, battery, wifi, connectivity, notification, package). List services with `dumpsys -l`. Always truncate output.

### 195 Bot action audit log
App log `files/audit.log` in the Deskbot app dir: `deskbot_shell cmd="tail -n 50 /data/data/com.rishitgoklani.hermes_front_mobile/files/audit.log"` (tab-separated time, tool, status, args). Summarize failures by tool.

### 196 Replay a failure
Read the failing call from the audit log, re-issue it with `deskbot_shell`/tool call, adding `logcat -c` before and `logcat -d` after to capture traces; report the diff.

### 197 Crash report bundle
`mkdir /sdcard/deskbot-crash/<ts>; logcat -d > log.txt; dumpsys > dump.txt; screencap -p shot.png; getprop > props.txt; cp audit.log`; zip it and tell the user the path.

### 198 Performance profiling
Frames `dumpsys gfxinfo <pkg> framestats`; startup `am start -W -n <pkg>/<act>`; memory over time `dumpsys meminfo <pkg>` sampled by cron.

### 199 Log rotation and cleanup
Audit log rotates automatically at 512 KB. For others: `logcat -c`, `find /sdcard/deskbot-* -mtime +14 -delete`; compress with `gzip`.

### 200 Remote log viewing
Serve logs: `python -m http.server 8081 --directory /sdcard/deskbot-logs` (LAN) or from the PC `adb logcat`; stop the server afterwards.
