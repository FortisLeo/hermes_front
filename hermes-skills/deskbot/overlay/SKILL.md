---
name: deskbot-overlay
description: "Show captions, dimmers, floating status and full-screen displays on the Deskbot phone screen."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-screen]
---

# Overlay and on-screen display

Overlay tools need the draw-over-apps permission (already granted by `grant_all.sh`).

### 51 Text overlay anywhere
`deskbot_overlay_text text=… duration_ms=5000 position=top|bottom` (click-through). Use for captions, subtitles, translations. Clear with `deskbot_overlay_clear`.

### 52 Floating widget
Planned app feature (face bubble). Until then use `deskbot_overlay_text` for short status, and the Face tab/full-screen mode for the bot face.

### 53 Highlight an element
Find the element with `deskbot_ui_tree` (gives centre coords), then `deskbot_overlay_text` near it as a label; a box overlay is planned. Alternative: tell the user where to look ("top right").

### 54 Full-screen display
Open the Deskbot app (`deskbot_app_open name=Deskbot`); its Face tab is the face-only mode; clock/photo-frame/dashboard surfaces are rendered by the app (A2UI planned). Keep awake with `settings put global stay_on_while_plugged_in 7`.

### 55 Picture-in-picture output
Use apps that support PiP (video players): start playback, press home (`deskbot_ui_global home`); the app enters PiP. Camera/bot-answer PiP is planned.

### 56 Toasts and heads-up messages
Quick status: `deskbot_notification_post title text` (heads-up). For a transient line without a notification use `deskbot_overlay_text duration_ms=3000`.

### 57 Click-through overlays
`deskbot_overlay_text` and `deskbot_screen_dim` are click-through by design (not touchable). Remove them with `deskbot_overlay_clear`.

### 58 Screen dimmer and filters
`deskbot_screen_dim percent=40` (extra dim up to 90%), blue-light tint `tint=FF8800 percent=30`; remove with `percent=0`. Combine with `deskbot_brightness` for night use.

### 59 Lock-screen display
`deskbot_lock_display action=on|off|status` makes the face (and any A2UI card) show above the lock screen and wake the display; best on a dedicated, data-free phone. Notifications on the lock screen: `deskbot_notification_post` (visibility follows the lock-screen setting, see #18). Always-on display content: vendor feature `settings put secure doze_always_on 1`; custom AOD content needs the app (planned).

### 60 Draw on screen
Planned (needs an annotation overlay view). For now use `deskbot_overlay_text` arrows like "↗ tap here" positioned top/bottom.
