---
name: deskbot-accessibility-admin
description: "Manage Android accessibility services and features (TalkBack, color, text size, touch timing) on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-settings]
---

# Accessibility service management

Deskbot's own service auto-enables at app start. Use `deskbot_shell` for others.

### 31 Enable or disable an accessibility service
`settings put secure enabled_accessibility_services "<pkg>/<service>:<existing>"` then `settings put secure accessibility_enabled 1`. Always **append** to the existing colon-separated list (read it first with `deskbot_settings_get`). Verify the service shows in `dumpsys accessibility | grep -i "bound services"`.

### 32 Keep services alive
Check: `deskbot_settings_get table=secure key=enabled_accessibility_services` includes `com.rishitgoklani.hermes_front_mobile/...DeskbotAccessibilityService`; if missing, the app re-adds it on start (`deskbot_app_open name=Deskbot`) or run `settings put secure ...` via shell. After reboot the BootReceiver restarts the app, which re-enables.

### 33 Built-in accessibility features
TalkBack `com.google.android.marvin.talkback/.TalkBackService`; Select to Speak `com.google.android.accessibility.selecttospeak/.SelectToSpeakService`; Live Caption `settings put secure odi_captions_enabled 1`. Enable via #31 or the UI (`am start -a android.settings.ACCESSIBILITY_SETTINGS`). Warning: TalkBack changes touch behavior; disable it again unless asked.

### 34 Color and contrast
Inversion `settings put secure accessibility_display_inversion_enabled 1`; color correction `accessibility_display_daltonizer_enabled 1` + `accessibility_display_daltonizer 0..2`; grayscale `accessibility_display_daltonizer 0` (monochrome mode); high-contrast text `high_text_contrast_enabled 1` (all `secure` table).

### 35 Text and magnification
`settings put system font_scale 1.3`; bold text `settings put secure font_weight_adjustment 300`; magnification `accessibility_display_magnification_enabled 1`; cursor size via UI.

### 36 Accessibility shortcut and button
`settings put secure accessibility_button_targets <pkg>/<svc>`; shortcut `accessibility_shortcut_target_service`; volume-key shortcut `accessibility_shortcut_enabled 1`.

### 37 Touch timing
Touch-and-hold delay `settings put secure long_press_timeout 1000`; tap duration/ignore repeated via `accessibility_bounce_keys` (version dependent); verify with `deskbot_settings_get`.

### 38 Audio accessibility
Mono audio `settings put system master_mono 1`; balance `settings put system master_balance 0.0`; captions `accessibility_captioning_enabled 1`; sound notifications via UI.

### 39 Accessibility event stream
The app already receives window-state events (foreground package tracking). For triggers, poll `deskbot_screen_state` / `deskbot_ui_wait_for`; event-driven routines are planned (see routines, #225).

### 40 Audit service permissions
`dumpsys accessibility | grep -E "Service\[|packageNames|capabilities"` lists services and what each can retrieve/perform. Flag any not on the known list (Deskbot, TalkBack, Select to Speak) and report to the user.
