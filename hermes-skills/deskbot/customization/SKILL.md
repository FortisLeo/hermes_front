---
name: deskbot-customization
description: "Launcher, themes, wallpapers, sounds, navigation style, quick tiles, shortcuts, fonts, boot animation and status bar tuning on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-settings]
---

# Customization

### 391 Launcher and home screen
Switch launcher per #16; edit layout (move icons, folders, widgets) with `deskbot_ui_click long=true` + `deskbot_ui_swipe` drag; verify with a screenshot. Layout varies by launcher: save a per-launcher recipe.

### 392 Themes and icon packs
Dark/light schedule `cmd uimode night auto`; accent via the launcher/theme app UI; fonts per #398. Persona themes apply inside the Deskbot app (#425).

### 393 Wallpaper automation
Cron picks an image by time/weather and sets it per #138; generated art via `image_generate`.

### 394 Sound packs and ringtones
`settings put system ringtone|notification_sound|alarm_alert content://media/internal/audio/media/<id>`; list sounds `content query --uri content://media/internal/audio/media --projection _id:_display_name`; per-contact via Contacts UI.

### 395 Navigation style
`cmd overlay enable com.android.internal.systemui.navbar.gestural` (gesture) / `...threebutton`; verify `cmd overlay list | grep navbar`.

### 396 Quick Settings tiles
Reorder/add via the Quick Settings edit mode (`deskbot_ui_global quick_settings` → pencil → drag). A custom Deskbot tile is a planned app feature.

### 397 Shortcuts and widgets
App shortcuts: long-press icon; widgets via launcher UI. A Deskbot status widget is planned.

### 398 Font and display tuning
Font size/weight via #35/#13; custom fonts need a font module (module-scout flow, #273) and reboot.

### 399 Boot animation and splash
Module with `system/media/bootanimation.zip` (replace via KernelSU module, #279); keep the original; one change per boot test.

### 400 Status bar and system UI tuning
`settings put secure icon_blacklist rotate,alarm_clock` (hide icons), clock seconds `settings put secure clock_seconds 1`, battery percent `settings put system status_bar_show_battery_percent 1`; vendor ROM keys vary; use System UI Tuner-like UI where present.
