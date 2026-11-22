---
name: deskbot-touch
description: "Tap, swipe, scroll, drag, key events, navigation and gesture macros on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-screen,deskbot-text-input]
---

# Touch and gesture injection

Ladder: accessibility (`deskbot_ui_click`, `deskbot_ui_swipe`, `deskbot_ui_global`) > root `input` via `deskbot_shell`. After every action verify with `deskbot_ui_wait_for` or `deskbot_ui_tree`.

### 61 Tap
By element: `deskbot_ui_click text=…` (or `id`, `desc`, `index`). By coordinate: `deskbot_ui_click x= y=`. Long press: add `long=true`. Double tap: call twice quickly or `deskbot_shell cmd="input tap X Y; input tap X Y"`.

### 62 Swipe and scroll
`deskbot_ui_swipe direction=up|down|left|right` (up = finger moves up = content scrolls down) or explicit `x1 y1 x2 y2 duration_ms`. Scroll-to-find: loop swipe + `deskbot_ui_tree` until the label appears (max ~8 tries). Edge swipe: start at x=1 or width-1.

### 63 Multi-touch gestures
Accessibility single-path gestures only; for pinch use root: `deskbot_shell cmd="input -d 0 swipe ..."` is single-touch, so prefer app zoom buttons or double-tap zoom. Pinch via `sendevent` is device-specific (avoid unless needed).

### 64 Drag and drop
`deskbot_shell cmd="input draganddrop X1 Y1 X2 Y2 800"` (Android 12+) or `deskbot_ui_swipe x1 y1 x2 y2 duration_ms=800` with a long press start (long-press first via `ui_click long=true`).

### 65 System navigation actions
`deskbot_ui_global action=back|home|recents|notifications|quick_settings|lock|power_dialog`.

### 66 Hardware key events
`deskbot_shell cmd="input keyevent KEYCODE_VOLUME_UP"`; media keys `deskbot_media_key`; power `KEYCODE_POWER` (wake/sleep); camera `KEYCODE_CAMERA`; assistant `KEYCODE_ASSIST`.

### 67 Record and replay a gesture macro
Record: `getevent -t` is raw; prefer logging the sequence of Deskbot tool calls (the audit log at `files/audit.log`/skill steps). Save the sequence as a skill with parameters; replay by re-issuing the calls with verification between steps.

### 68 Coordinate calibration
`deskbot_screen_state` (width/height) + `deskbot_shell cmd="wm size; wm density; dumpsys window displays | grep -E 'rotation|cutout'"`. Screenshots are downscaled; convert with `x = sx * width / shot_width`.

### 69 Human-like input timing
Add short random waits between steps (`deskbot_ui_wait_for` on the next expected text instead of fixed sleeps), eased swipes via `duration_ms=250-500`, and retry a failed tap once after re-reading `deskbot_ui_tree`.

### 70 Haptic confirmation
`deskbot_vibrate duration_ms=80` for ack, `pattern=[0,80,60,80]` for success, `[0,400]` for error.
