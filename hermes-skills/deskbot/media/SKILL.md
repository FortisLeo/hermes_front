---
name: deskbot-media
description: "Playback control, screenshots, screen recording, media files, wallpapers, books and podcasts on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-audio-voice]
---

# Media playback and capture

### 131 Control playback
`deskbot_media_key action=play_pause|play|pause|next|previous|stop|rewind|forward`. Seek/queue/repeat: open the player (`deskbot_app_open`) and use `deskbot_ui_click`. Verify via the media notification (`deskbot_notifications_list`).

### 132 Play specific content
Open the app (`deskbot_app_open name=YouTube Music`), search per #78, click the first matching result, verify the notification shows the title. For deep links: `am start -a android.intent.action.VIEW -d "spotify:search:<query>"`.

### 133 Per-stream volume
`deskbot_stream_volume stream=music|ring|alarm|notification|system|call level=<0-100> mute=true|false`. Mute all: set each to 0.

### 134 Take screenshots
`deskbot_screenshot` (JPEG ≤140 KB, ≤960 px) for the model; full-resolution file: `deskbot_shell cmd="screencap -p /sdcard/Pictures/shot.png"`. Region/scrolling: crop in Termux (`convert`), scrolling = repeated swipe+screencap.

### 135 Record the screen
`deskbot_shell cmd="screenrecord --time-limit 30 /sdcard/Movies/rec.mp4"` (add `--bit-rate 6000000`; audio is not captured by screenrecord). If it fails with "unable to start codec (err=-12)" retry once (transient hardware-codec contention). Run in background `&` and stop with `pkill -INT screenrecord`.

### 136 Handle media files
Termux `ffmpeg` (`pkg install ffmpeg`): trim `-ss 5 -to 20 -c copy`, extract audio `-vn -acodec copy out.m4a`, thumbnails `-vf thumbnail -frames:v 1`. Rename/move with `deskbot_file_move`.

### 137 Gallery search
List recent photos: `deskbot_file_search path=/sdcard/DCIM name=.jpg`; describe candidates with vision by encoding a few (base64 into an image analysis call). Index results in memory (path → description).

### 138 Wallpapers
Set: `cmd wallpaper set-image <path>`-style is unavailable; use `am start -a android.intent.action.ATTACH_DATA -d file:///sdcard/Pictures/w.jpg -t image/jpeg` and confirm via `deskbot_ui_click`. Rotate daily via cron. AI-generated: `image_generate`, save to `/sdcard/Pictures`.

### 139 E-books and documents
Open with the reader app (`am start -a android.intent.action.VIEW -d file://… -t application/epub+zip`); read aloud by extracting text (Termux `pandoc`/`pdftotext`) and `deskbot_tts_say` chunks; page turn `deskbot_ui_swipe direction=left`.

### 140 Podcasts and radio
Open the podcast app, search/subscribe via UI (#78), resume with `deskbot_media_key action=play`, sleep timer: Hermes cron job that calls `deskbot_media_key action=pause` after N minutes.
