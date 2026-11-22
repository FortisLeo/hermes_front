---
name: deskbot-clipboard-sharing
description: "Clipboard, sharing, quick capture, contacts, calendar, tasks, alarms, timers, routines on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-notifications]
---

# Clipboard, sharing, contacts, calendar, tasks, alarms

### 201 Read and write the clipboard
`deskbot_clipboard_get` (Android 10+ may block background reads; if it fails, bring the app to front with `deskbot_app_open name=Deskbot` and retry), `deskbot_clipboard_set text=…`. Images/history: the Gboard clipboard UI via `deskbot_ui_*`.

### 202 Clipboard watcher
Cron poll `deskbot_clipboard_get` every 30 s; when it changes, classify (URL, address, phone, code) and offer an action ("open link?"). Event-driven watcher planned.

### 203 Share content to an app
`am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<text>" -p <pkg>` (direct), or chooser then `deskbot_ui_click` the target. Named contact: choose the chat app, search the contact (#78).

### 204 Receive shared content
Planned app feature (share target "Send to Deskbot"). Today: ask the user to copy it and say "read my clipboard".

### 205 Quick capture to memory
Phrase "remember this": take the text (or `deskbot_clipboard_get`, or `deskbot_screenshot` + vision summary) and store with the `memory` tool plus a date; confirm briefly.

### 206 Cross-device clipboard
Termux: `termux-clipboard-get/set` + `kdeconnect-cli --share-text` or an `ssh` pipe to the PC `xclip`. Needs Termux:API/KDE Connect; say so if absent.

### 207 Copy text out of images
`deskbot_screenshot` or `deskbot_camera_capture` → vision transcription → `deskbot_clipboard_set`. Offline: `tesseract` in Termux.

### 208 Convert on paste
Take `deskbot_clipboard_get`, convert (strip formatting, Markdown↔JSON) with the model, `deskbot_clipboard_set` the result.

### 209 Drag content between apps
Split screen (#150) then `deskbot_shell cmd="input draganddrop X1 Y1 X2 Y2 1000"`; fallback copy/paste via clipboard.

### 210 Clipboard hygiene
After sensitive use: `deskbot_clipboard_clear`. Auto-clear: cron/timer `deskbot_timer_start name=clip duration=60s` then clear on fire. Redact before storing: never save clipboard contents that look like secrets.

### 211 Manage contacts
`deskbot_contacts_search query=…`, `deskbot_contacts_add name phone email`. Edit/merge/VCF: Contacts app UI or `content` provider: `content query --uri content://com.android.contacts/data/phones`; export: Contacts > Export via `deskbot_ui_*`.

### 212 Read the calendar
`deskbot_calendar_events days=1|7`. Free time/conflicts: compute from the returned start/end times.

### 213 Create and edit events
`deskbot_calendar_add_event title start_ms duration_min location`. Recurrence/attendees: use the calendar app UI (`am start -a android.intent.action.INSERT -d content://com.android.calendar/events` then fill the form).

### 214 Daily briefing
Compose: `deskbot_calendar_events days=1`, weather via `web_search`, reminders from memory/cron, notification digest (`deskbot_notification_history digest=true since_minutes=720`). Speak in ≤ 6 sentences with `deskbot_tts_say`.

### 216 Birthday and anniversary reminders
Store dates in memory (person, date); Hermes cron daily 08:00 checks and speaks/notifies. Pull from contacts events if present.

### 217 Meeting prep
10 minutes before an event (cron from `deskbot_calendar_events`): fetch agenda/notes from memory and web (`web_extract` for links), summarize attendees' last topic, speak a short brief.

### 218 Leave-by alerts
`deskbot_location_get` + event location → estimate travel with web search/maps ETA; schedule a notification at start − ETA − buffer.

### 219 Task lists
Use Hermes `memory` (list per name) or a file `/sdcard/deskbot/tasks.md` via `deskbot_file_write append`. Add, check off, due dates; recurring via cron. Read aloud on request.

### 220 Schedule awareness
Calendar holidays appear as events (`deskbot_calendar_events`); work hours/quiet mode from memory rules; apply with DND (`cmd notification set_dnd`) via cron.

### 221 Set an alarm
`deskbot_alarm_set hour minute label days`; verify: Clock app list (`deskbot_ui_tree` after `am start -a android.intent.action.SHOW_ALARMS`). Ringtone via the Clock UI.

### 222 Timers and stopwatch
`deskbot_timer_start name duration` ("90s", "5 min", "1h 30m"), `deskbot_timer_list`, `deskbot_timer_cancel`, `deskbot_stopwatch action=start|stop`. The bot speaks and notifies when a timer ends. Pomodoro: chain 25m work + 5m break timers.

### 223 Reminders by place or condition
Timed: Hermes cron. By place: cron every few minutes comparing `deskbot_location_get` to a saved point (planned geofence events). On charger: poll `deskbot_battery`. Nag until done: repeating cron + `deskbot_tts_say` until the user confirms.

### 225 Event-triggered routines
Poll-based today (cron + `deskbot_battery`, `deskbot_network_info`, `deskbot_screen_state`, `deskbot_notification_history`). Native event triggers are a planned app feature.

### 226 Wake-up routine
Cron at alarm time: raise brightness gradually (`deskbot_brightness` stepping 10%/min), play music (#132), speak the briefing (#214).

### 227 Bedtime routine
Cron at bedtime: `deskbot_screen_dim percent=50`, `cmd notification set_dnd priority`, read tomorrow's first events, confirm the alarm (#221).

### 228 Focus session
`deskbot_timer_start name=focus duration=25m`, DND on (`cmd notification set_dnd priority`), block apps `pm suspend`/`cmd package suspend <pkg>`; on fire: undo and announce.
