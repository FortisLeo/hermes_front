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

Every camera tool takes `facing=front|back` and defaults to **front** (the robot faces the user); use `back` for things on the desk or a page held to the rear lens. `deskbot_camera_capture [facing=front|back]` returns `data.image_base64` (JPEG ≤140 KB). Attach it to your vision analysis; keep answers short and speakable. Do not identify people by face; describe only what is visible (counts, clothing colors, objects).

### 111 Capture a photo
`deskbot_camera_capture facing=back` for the room/objects, `front` (default) faces the user. Save a copy: `deskbot_file_write` is text-only, so use `deskbot_shell` with base64 decode: `echo <b64> | base64 -d > /sdcard/Pictures/x.jpg`. Burst: repeat the call; flash: `deskbot_torch on=true` first.

### 112 Describe what the camera sees
Capture, then describe the scene/object in hand. Count people only (no identity). Mention colors, text, and notable objects.

### 113 Read text through the camera
Capture (back camera for documents), transcribe text exactly, note uncertain parts. For handwriting say confidence is lower.

### 114 Scan codes and documents
QR/barcode: `deskbot_scan_code [facing=front|back] [source=screen] [attempts=3]` decodes on-device (ZXing) and returns `data.codes [{format,text,kind}]`; read out `kind` first ("a link", "Wi-Fi details") and never open URLs or join Wi-Fi without the user's go-ahead. Document: `deskbot_scan_document [facing=back]` captures a high-resolution frame, OCRs it (`data.text`) and saves the JPEG (`data.path`, under Deskbot/scans); make a PDF with `img2pdf` in Termux if asked.

### 115 Recognize and count objects
`deskbot_count_objects [facing=front|back] [label=cup]` detects and counts objects on-device (`data.total`, `data.counts`, whole-frame `data.labels` such as plant/desk). Categories are coarse, so for a specific product or plant species also `deskbot_camera_capture` and name it with vision. For inventory over time store counts in memory with the date.

### 116 Visual change watcher
`deskbot_visual_watch action=start [facing=front|back] [period_s=60] [sensitivity=low|normal|high]` compares small frames on-device and fires the routine event `visual_change` when the scene changes (alerts: attach a routine that speaks or posts a notification). `action=status` shows checks, events and the last diff score; `action=stop` ends it. Front is the default; use `facing=back` to watch the desk. Cap the rate (min 10 s) to protect battery.

### 117 Presence from the camera
`deskbot_presence_check [facing=front|back]`; answer "is someone there?" (person visible yes/no, no identity). Use for arrival/departure events (#331). Avoid capturing when the user asked for privacy/sleep mode.

### 118 Hand gesture commands
Capture a short sequence (2-3 frames 400 ms apart) and ask vision to classify wave/thumbs-up. Experimental; confirm before acting on destructive gestures.

### 119 Record video or time-lapse
Time-lapse: `deskbot_time_lapse action=start [facing=front|back] interval_s=10 count=60 name=desk` saves `f_0000.jpg...` under Deskbot/timelapse/<name> (`action=status|stop`), then `ffmpeg -framerate 10 -i f_%04d.jpg out.mp4` in Termux. Video: `deskbot_video_record [facing=front|back] [duration_s=10] [audio=true]` records a clip to Deskbot/videos and returns `data.path`. Motion-triggered recording: start `deskbot_visual_watch`, and from the `visual_change` routine call `deskbot_video_record`. Ask before recording audio.

### 120 Camera controls
Zoom/focus/exposure/lens are not exposed by the tool yet; use the stock camera UI (`am start -a android.media.action.IMAGE_CAPTURE`) with `deskbot_ui_*`. Torch: `deskbot_torch on=true|false`.
