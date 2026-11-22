---
name: deskbot-system-settings
description: "Read or change Android settings, display, sound, locale, defaults, DND, lock screen, settings backup on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-connectivity]
---

# System settings

Prefer `deskbot_settings_get` / `deskbot_settings_put` (no root, read-back verified) for any `settings` key; use `deskbot_shell` for `cmd`/`svc`/`wm` commands. Never touch provisioning keys (the tool refuses them).

### 11 Read any system setting
`deskbot_settings_get table=system|global|secure key=<k>`. Full dump: `deskbot_shell cmd="settings list system; settings list global; settings list secure"`. Search: pipe to `grep -i <word>`.

### 12 Change a setting by key
`deskbot_settings_put table key value`; it reads the value back and reports `failed` if it did not apply. If it fails on `global`/`secure`, retry with `deskbot_shell cmd="settings put <table> <key> <value>"`.

### 13 Display look
Brightness `deskbot_brightness level=<0-100>`; auto-brightness `settings put system screen_brightness_mode 1|0`; font scale `settings put system font_scale 1.15`; display size `wm density <dpi>` (reset `wm density reset`); dark mode `cmd uimode night yes|no|auto`. Verify with the matching `settings get`/`cmd uimode night`.

### 14 Sound and haptics
`deskbot_stream_volume stream=music|ring|alarm|notification|system|call level=<0-100> [mute]`; vibrate on touch `settings put system haptic_feedback_enabled 1|0`; touch sounds `settings put system sound_effects_enabled 1|0`; ringtone `settings put system ringtone <content-uri>`; test haptics `deskbot_vibrate`.

### 15 Language, region, time zone
Time zone `cmd time_zone_detector set_time_zone_id <Area/City>` (fallback `service call alarm 3 s16 <id>`); 24 h `settings put system time_12_24 24`; auto time `settings put global auto_time 1`; locale `cmd locale set-app-locales` is per-app; system locale needs `settings put system system_locales en-GB` + reboot. Verify with `date` and `settings get`.

### 16 Default apps
`cmd role add-role-holder android.app.role.HOME <pkg> 0` (launcher); BROWSER, DIALER, SMS, ASSISTANT use the same `android.app.role.<NAME>`. List holders: `cmd role get-role-holders android.app.role.HOME`. Fallback UI: `am start -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS`.

### 17 Do Not Disturb and focus modes
`cmd notification set_dnd on|off|priority|alarms|none`; schedules via `cmd notification` rules or Settings UI. Verify with `settings get global zen_mode` (0 off, 1 priority, 2 none, 3 alarms).

### 18 Lock screen and security settings
Lock timeout `settings put secure lock_screen_lock_after_timeout <ms>`; screen timeout `settings put system screen_off_timeout <ms>`; notifications on lock `settings put secure lock_screen_show_notifications 1|0`. Changing lock *type/PIN*: `cmd lock_settings set-pin <pin>` / `clear` only with the user's explicit confirmation (it can lock the owner out).

### 19 Back up and restore settings
Backup: `for t in system global secure; do settings list $t > /sdcard/deskbot-backup/settings_$t_$(date +%F).txt; done` (use `deskbot_file_write` to store notes). Diff against baseline with `diff`. Restore: loop `settings put <table> <key> <value>` for changed keys only; skip protected keys.

### 20 Find a setting by name
Deep-link: `am start -a android.settings.<SECTION>` (e.g. WIFI_SETTINGS, DISPLAY_SETTINGS, SOUND_SETTINGS, APPLICATION_DETAILS_SETTINGS -d package:<pkg>). Search: open Settings, `deskbot_ui_click text="Search settings"`, `deskbot_ui_type`, then `deskbot_ui_click` the result.
