---
name: deskbot-life-admin
description: "Games, entertainment, shopping lists, expenses, bills and health reminders on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-media]
---

# Games, entertainment and life admin

### 361 Play a turn-based game
Read the board with `deskbot_screenshot` + vision (or `ui_tree` for text-based games), decide the move, tap it (`deskbot_ui_click`), wait for the opponent (`deskbot_ui_wait_for`), repeat. Announce moves briefly.

### 363 Game automation
Repetitive tasks (daily rewards) with limits: max N actions per run, stop on unexpected screens, no purchases ever. Respect the game's terms; prefer official features.

### 364 Board-state help
Describe the position from a screenshot or camera frame and suggest moves/ideas; for chess convert to FEN and (optionally) use an engine in Termux (`pkg install stockfish`).

### 365 Mood music
Pick music from mood/time/weather (model judgement), play per #132.

### 369 Watch-party helper
Pause on request `deskbot_media_key action=pause`; find a scene by scrubbing (`deskbot_ui_swipe`/seek bar clicks) and verifying with a screenshot; subtitles toggle through the player UI.

### 370 Identify what is shown
Screenshot or camera frame → vision → `web_search` to confirm movie/song/object; give the title and one line. No lyrics or copyrighted text.

### 371 Shopping lists and ordering
Maintain the list (#310); open the store app, search each item (#78), add to cart, **stop before payment** and read the cart total to the user.

### 373 Expense logging
From a receipt photo (`deskbot_camera_capture facing=back`), a payment notification (`deskbot_notification_history`), or voice: extract merchant/amount/date/category and append to `/sdcard/deskbot/expenses.csv`.

### 374 Bill and subscription reminders
Detect bills from email/notifications (#254), store due dates in memory, cron reminders 3 days before and on the day.

### 380 Health reminders
Cron: water every 90 min, stretch hourly, posture, 20-20-20 eye breaks (`deskbot_notification_post` + short `deskbot_tts_say`); respect focus/quiet mode; let the user snooze ("later").
