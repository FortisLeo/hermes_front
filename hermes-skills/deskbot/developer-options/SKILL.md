---
name: deskbot-developer-options
description: "Developer options, USB/wireless debugging, animation scales, process limits, bug reports on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-settings]
---

# Developer options and debugging

Mostly `deskbot_settings_put` (global table) or `deskbot_shell`. Root is available; treat debugging flags as persistent state and record the baseline before changing (feature 30).

### 21 Enable developer options
`settings put global development_settings_enabled 1`. Fallback: Settings > About phone > tap "OS version" 7 times via `deskbot_ui_click` (repeat), then verify the "Developer options" entry exists.

### 22 USB and wireless debugging
`settings put global adb_enabled 1`; wireless: `settings put global adb_wifi_enabled 1`; fixed TCP port: `setprop service.adb.tcp.port 5555; stop adbd; start adbd`. Pairing-code flow is UI-only (Developer options > Wireless debugging > Pair): drive with `deskbot_ui_*` and read the code with `deskbot_ui_tree`. After boot, re-enable if the flag reset.

### 23 Animation scales
`settings put global window_animation_scale 0.0; settings put global transition_animation_scale 0.0; settings put global animator_duration_scale 0.0` (restore to `1.0`). Use 0 before long UI automation runs.

### 24 Stay-awake and touch overlays
Stay awake charging: `settings put global stay_on_while_plugged_in 7` (0 off); show taps `settings put system show_touches 1`; pointer location `settings put system pointer_location 1`.

### 25 Rendering flags
Force dark `settings put secure ui_night_mode 2`; overdraw `setprop debug.hwui.overdraw show` / `false`; profile GPU `setprop debug.hwui.profile visual_bars`; restart UI by relaunching the app (`am force-stop`).

### 26 Background process limits
Phantom killer off (Android 12+): `settings put global settings_enable_monitor_phantom_procs false` and `device_config put activity_manager max_phantom_processes 2147483647` (use `device_config set_sync_disabled_for_tests persistent` first on 14+). Background limit `settings put global background_process_limit`-style options vary: use `am set-standby-bucket <pkg> active`. Verify: `dumpsys activity settings | grep -i phantom`.

### 27 Mock location app
`appops set <pkg> android:mock_location allow`; select it: `settings put secure mock_location 1`. Feed coordinates via the mock app (e.g. Appium Settings: `am broadcast -a io.appium.settings.location --es longitude <x> --es latitude <y>`). Verify with `deskbot_location_get`.

### 28 USB configuration default
`svc usb setFunctions mtp|ptp|rndis|midi|none` (add `true` to persist as default). Verify `getprop sys.usb.state`.

### 29 Bug reports and traces
`bugreportz -p` (zip in `/bugreports`), or `dumpsys > file`; perfetto: `perfetto -o /data/misc/perfetto-traces/trace -t 10s sched freq idle am wm gfx view` then pull to Termux. Store with `deskbot_file_*` or copy to `/sdcard`.

### 30 Developer option audit
Snapshot: `settings list global | grep -E "adb|development|animation|stay_on|show_touches|debug"`. Keep a baseline in memory/`/sdcard/deskbot-backup/devopts.txt`; diff each run and repair drift with `deskbot_settings_put`.
