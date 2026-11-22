---
name: deskbot-display-power
description: "Screen on/off, wake lock, rotation, density, refresh rate, power menu, scheduled power events on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-battery-thermal]
---

# Screen, display, and power

### 141 Wake, sleep, and unlock the screen
Wake `input keyevent KEYCODE_WAKEUP`; sleep `input keyevent KEYCODE_SLEEP` or `deskbot_ui_global lock`; unlock no-security: `wm dismiss-keyguard`; PIN: `input text <pin>` + `KEYCODE_ENTER` only if the user stored it for this purpose (never speak it).

### 142 Keep the screen awake
`settings put global stay_on_while_plugged_in 7`; `svc power stayon true|false`; per-app rules need the app's own keep-screen-on setting.

### 143 Rotation and orientation
`settings put system accelerometer_rotation 0` then `settings put system user_rotation 0|1|2|3` (landscape=1). Verify `deskbot_screen_state` width/height. Note: this phone currently reports landscape-rotated UI.

### 144 Resolution and density
`wm size 1080x2400`, `wm density 400`; reset `wm size reset; wm density reset`. Keep a note of originals before changing.

### 145 Refresh rate and HDR
`settings put system peak_refresh_rate 120; settings put system min_refresh_rate 60` (vendor keys vary); verify `dumpsys display | grep -i refresh`.

### 146 Always-on display behavior
`settings put secure doze_always_on 1`; content/schedule/burn-in shift are vendor UI (`am start -a android.settings.DISPLAY_SETTINGS`).

### 147 Night mode and color temperature
`cmd uimode night yes`; night light `settings put secure night_display_activated 1`, schedule `night_display_auto_mode 1`; temperature `night_display_color_temperature <K>`; grayscale at night: #34 + cron.

### 148 Power menu actions
Reboot `reboot`; recovery `reboot recovery`; shutdown `reboot -p`; (bootloader/fastboot reboots are blocked by the deny-list on purpose). Always ask the user first; the bot goes offline during reboot (Termux:Boot restarts it).

### 149 Scheduled power events
Hermes cron: nightly `reboot` at 04:00 (after backup), timed screen-off `input keyevent KEYCODE_SLEEP`; verify uptime next day (`uptime`).

### 150 Multi-window and display modes
Split: `deskbot_ui_global recents` + tap the app icon > Split; freeform/desktop mode: `settings put global enable_freeform_support 1` (reboot), `am start --windowingMode 5 -n <pkg>/<act>`. Secondary display: `dumpsys display`.
