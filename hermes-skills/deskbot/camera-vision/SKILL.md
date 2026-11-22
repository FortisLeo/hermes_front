---
name: deskbot-camera-vision
description: "Use the phone camera: photos, describing scenes, reading text, codes, counting, presence and time-lapse."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-media]
---

# Camera and vision

`deskbot_camera_capture [facing=front|back]` returns `data.image_base64` (JPEG ≤140 KB). Attach it to your vision analysis; keep answers short and speakable. Do not identify people by face; describe only what is visible (counts, clothing colors, objects).

### 111 Capture a photo
`deskbot_camera_capture facing=back` for the room/objects, `front` (default) faces the user. Save a copy: `deskbot_file_write` is text-only, so use `deskbot_shell` with base64 decode: `echo <b64> | base64 -d > /sdcard/Pictures/x.jpg`. Burst: repeat the call; flash: `deskbot_torch on=true` first.

### 112 Describe what the camera sees
Capture, then describe the scene/object in hand. Count people only (no identity). Mention colors, text, and notable objects.

### 113 Read text through the camera
Capture (back camera for documents), transcribe text exactly, note uncertain parts. For handwriting say confidence is lower.

### 114 Scan codes and documents
QR/barcode: capture and decode via vision; for exact decoding use Termux `zbarimg` (pkg install zbar) on the saved image. Document scan: capture, crop/rotate with vision hints, OCR, save as text/PDF (`img2pdf` in Termux).

### 115 Recognize and count objects
Capture and count desk items/plants/products; give a short list. For inventory over time store counts in memory with the date.

### 116 Visual change watcher
Cron job every N minutes: capture, compare to the previous description/image stored in memory, and speak/notify only when something appeared, moved or disappeared (`deskbot_notification_post`). Cap capture rate to protect battery.

### 117 Presence from the camera
Capture front frame; answer "is someone there?" (person visible yes/no, no identity). Use for arrival/departure events (#331). Avoid capturing when the user asked for privacy/sleep mode.

### 118 Hand gesture commands
Capture a short sequence (2-3 frames 400 ms apart) and ask vision to classify wave/thumbs-up. Experimental; confirm before acting on destructive gestures.

### 119 Record video or time-lapse
Time-lapse: loop `deskbot_camera_capture` + save frames, then `ffmpeg -framerate 10 -i f_%03d.jpg out.mp4` in Termux. Video: `am start -a android.media.action.VIDEO_CAPTURE` then `deskbot_ui_click` the shutter.

### 120 Camera controls
Zoom/focus/exposure/lens are not exposed by the tool yet; use the stock camera UI (`am start -a android.media.action.IMAGE_CAPTURE`) with `deskbot_ui_*`. Torch: `deskbot_torch on=true|false`.
