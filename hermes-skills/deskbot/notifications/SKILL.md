---
name: deskbot-notifications
description: "Read, summarize, dismiss, act on, post and automate notifications on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-audio-voice]
---

# Notifications

Core tools: `deskbot_notifications_list`, `deskbot_notification_dismiss`, `deskbot_notification_act`, `deskbot_notification_post`, `deskbot_notification_history`, `deskbot_otp_latest`.

### 101 Read notifications
`deskbot_notifications_list [app] [query]`: each has app, title, text, time, actions, key. Read aloud as "<app>: <title>, <text>" (max 3, offer the rest).

### 102 Act on a notification
`deskbot_notification_act key=<key> action=<button title>`; inline reply: add `reply=<text>`. Snooze/mark-read: act with the button of that name. If the action is missing, tap the notification from the shade: `deskbot_ui_global notifications` + `deskbot_ui_click`.

### 103 Dismiss notifications
`deskbot_notification_dismiss key=…` | `app=…` | `keyword=…` | `all=true`. Confirm before `all`.

### 104 Filter and prioritize
Fetch `deskbot_notifications_list`, rank by importance (people > calendar/alerts > promos), summarize in two sentences, offer to silence noisy apps: `appops set <pkg> POST_NOTIFICATION deny` (see #98).

### 105 Speak notifications aloud
On request or per-rule, speak new ones via `deskbot_tts_say`. Respect quiet hours/DND (`settings get global zen_mode` != 0 → stay silent). Rules live in memory: `{app, quiet_hours}`.

### 106 Post a notification
`deskbot_notification_post title text [actions]` for reminders/alerts/status.

### 107 Notification-triggered automations
Poll `deskbot_notification_history since_minutes=1` from a cron job (every minute) and match rules in memory ("message from X contains Y" → do Z). Event-driven triggers are planned.

### 108 Capture one-time codes
`deskbot_otp_latest max_age_minutes=10` returns `data.code`. Offer to type it (`deskbot_ui_type`) rather than speaking it unless asked.

### 109 Notification history
`deskbot_notification_history query=… app=… since_minutes=…`; daily digest `digest=true since_minutes=1440` (counts per app) + summarize top items. History holds the last 500 non-ongoing notifications in memory (cleared on app restart).

### 110 Channel settings and lights
`cmd notification` has no channel editor; use the UI route in #98, or `dumpsys notification --noredact | grep -A8 "<pkg>"` to read channel importance, then drive Settings via `deskbot_ui_*`.
