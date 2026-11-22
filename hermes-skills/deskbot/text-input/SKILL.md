---
name: deskbot-text-input
description: "Type, edit, dictate, autofill and navigate forms on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-touch,deskbot-screen]
---

# Keyboard and text input

### 71 Type into the focused field
`deskbot_ui_type text=…` (accessibility set-text; replaces content). Fallbacks: `deskbot_shell cmd="input text 'hello%sworld'"` (ASCII, `%s` = space), then paste: `deskbot_clipboard_set` + `deskbot_shell cmd="input keyevent KEYCODE_PASTE"`.

### 72 Type non-Latin text and emoji
`deskbot_clipboard_set text=…` then `input keyevent KEYCODE_PASTE` (or long-press > Paste via `deskbot_ui_click long=true`). `input text` cannot send non-ASCII.

### 73 Edit text
Select all: `input keyevent KEYCODE_CTRL_LEFT KEYCODE_A` (on 14+ use `input keycombination CTRL_LEFT A`); copy/cut/paste `KEYCODE_COPY/CUT/PASTE`; delete a word via repeated `KEYCODE_DEL`; undo `keycombination CTRL_LEFT Z`.

### 74 Switch or configure the keyboard
List: `ime list -s`; set: `ime set <id>`; enable: `ime enable <id>`; layouts/handwriting/voice typing through `am start -a android.settings.INPUT_METHOD_SETTINGS`.

### 75 Dictate into any field
Focus the field (`deskbot_ui_click`), then speak: the bot transcribes and `deskbot_ui_type`s the text. App-side push-to-dictate is planned.

### 76 Autofill forms
`deskbot_ui_tree` -> find edit fields and their labels/hints; fill each by clicking it then `deskbot_ui_type` with values from memory. Never guess secrets; ask the user for missing values.

### 77 Navigate forms
Next field: `input keyevent KEYCODE_TAB` or click the next field; dropdown: `deskbot_ui_click` the field then the option text; checkbox: `deskbot_ui_click` (check `checked` flag in `ui_tree`); date picker: click the date then the day number; submit: click the button by text, then verify the result text with `deskbot_ui_wait_for`.

### 78 Search inside an app
Click the search icon (`desc=Search`), `deskbot_ui_type`, press `KEYCODE_ENTER`, then `deskbot_ui_tree` to read results.

### 79 Enter secrets and one-time codes
OTP: `deskbot_otp_latest` then `deskbot_ui_type`. Passwords: only from a vault the user created; never store or speak them; prefer the app's autofill. Clear the clipboard after use: `deskbot_clipboard_clear`.

### 80 Text snippets
Keep snippets in memory (name -> text). To insert: `deskbot_ui_type text=<snippet>` or clipboard paste for multi-line.
