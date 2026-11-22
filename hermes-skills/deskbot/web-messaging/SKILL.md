---
name: deskbot-web-messaging
description: "Phone browser control, messaging apps, email, documents, PDF forms and scanning on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-touch,deskbot-text-input]
---

# Browser, messaging, email and documents

General recipe for any app: `deskbot_app_open` → `deskbot_ui_tree` → act with `deskbot_ui_click/type` → `deskbot_ui_wait_for` the result → verify with `deskbot_ui_tree`. Save the working selectors as a per-app note. **Stop before irreversible steps** (payment, send to many, delete) and confirm with the user.

### 233 Control the phone's browser app
Open `am start -a android.intent.action.VIEW -d <url> -p com.android.chrome`; tabs (`desc=Tab`/count button), address bar (`id=url_bar`), bookmarks/history via menu; private mode: menu > New Incognito tab. Read page text with `deskbot_ui_tree` (Chrome exposes content).

### 234 Fill and submit web forms in the phone browser
Per #76/#77; read the form with `deskbot_ui_tree`, fill with values from memory, **stop at the submit/checkout step** and read back a summary for confirmation before tapping.

### 236 Download from the web
Direct link: Termux `curl -L -o /sdcard/Download/x <url>`; behind a click: open in the browser, tap Download, wait for the notification (`deskbot_notification_history query=Download`), verify with `sha256sum` and `deskbot_file_search`.

### 239 Translate a web page
In Chrome: menu > Translate (`deskbot_ui_click`); or fetch text (`web_extract`) and translate with the model, then speak chunks via `deskbot_tts_say`.

### 240 Browser data and settings
Clear data: Chrome menu > Settings > Privacy > Clear browsing data (`deskbot_ui_*`); or `pm clear com.android.chrome` (wipes everything; confirm). Default search engine in Chrome settings.

### 241 Send a message in an app
WhatsApp: `am start -a android.intent.action.VIEW -d "https://wa.me/<number>?text=<urlencoded>"` then `deskbot_ui_click desc=Send`. Telegram: `tg://msg?text=...`. Other apps: open chat via search (#78), `deskbot_ui_type`, tap Send. Verify the message appears with `deskbot_ui_wait_for`. Confirm recipient + text with the user before sending.

### 242 Read and summarize chats
Open the thread, `deskbot_ui_tree max_nodes=250` (messages are text nodes), scroll up with `deskbot_ui_swipe direction=down` to load more; summarize unread. Notification route: `deskbot_notifications_list app=WhatsApp`.

### 244 Voice messages
Record: long-press the mic button (`deskbot_ui_click long=true`), release by swipe/click Send. Incoming: play via `deskbot_ui_click`; transcribe by recording playback is unreliable → use the app's own transcription if available.

### 245 Post to social apps
Open app, compose via UI, attach media from `/sdcard/Pictures`, **confirm text/image with the user before Post**. Scheduling: app's scheduler UI or Hermes cron to post later.

### 246 Start or join a call
Voice/video in chat apps: open the chat and tap the call icon (`desc=Voice call`/`Video call`); hang up/mute by `deskbot_ui_click`. While in a call the bot stays silent (#333).

### 248 Media in chats
Send photo: attach icon → Gallery → pick (`deskbot_ui_click`). Incoming image: `deskbot_screenshot` and describe with vision.

### 249 Group and channel management
Long-press the chat (`deskbot_ui_click long=true`) → mute/pin/leave/mark read from the menu; verify the state changed.

### 250 Presence and status
App Settings > Profile/Privacy via `deskbot_ui_*`; set status text with `deskbot_ui_type`.

### 251 Triage email
Open the mail app (or `deskbot_notifications_list app=Gmail`), read the inbox via `deskbot_ui_tree`, rank by sender/subject, list unsubscribe candidates (bulk senders). Never open links in suspicious mails.

### 252 Draft and send email
Compose via `am start -a android.intent.action.SENDTO -d "mailto:<addr>?subject=<s>&body=<b>"`, attach via UI, **show the draft for confirmation before tapping Send**. Scheduling via the app's menu.

### 253 Search mail
Tap search (#78), use operators (`from:`, `subject:`, `has:attachment`, `after:`), read results via `deskbot_ui_tree`.

### 254 Extract from mail
Open the message, read text (`deskbot_ui_tree`), extract receipts/tickets/tracking numbers/dates with the model; store in memory or add to calendar (#213).

### 258 Fill PDF forms
Termux: `pdftk`/`python pypdf` to read fields (`PdfReader.get_fields()`), fill from memory, save to `/sdcard/Documents/filled.pdf`; unknown values → ask the user.

### 259 Scan paper to a document
`deskbot_camera_capture facing=back` (hold steady; retake if blurry), OCR with vision/tesseract, `img2pdf` for a PDF, save under `/sdcard/Documents/` and report the path.
