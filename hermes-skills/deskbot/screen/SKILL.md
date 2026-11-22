---
name: deskbot-screen
description: "Read, summarize, search, and watch the phone screen using the accessibility tree, OCR and vision."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-touch]
---

# Screen understanding

Ladder: `deskbot_ui_tree` (cheapest, exact) > `deskbot_screenshot` + vision model (apps that hide their tree, charts, games). Always report what you saw in one spoken sentence.

### 41 Understand the current screen
1) `deskbot_ui_tree`; if it has few labelled nodes, 2) `deskbot_screenshot` and describe/answer with vision. Then read, summarize, translate or explain per the request. Say the foreground app first (`deskbot_screen_state`).

### 42 Structured screen dump
`deskbot_ui_tree max_nodes=200` returns `[i] Class "label" id=… (cx,cy) flags`. For raw XML with bounds fall back to `deskbot_shell cmd="uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml"`.

### 43 OCR of screen text
`deskbot_screenshot` then ask the vision model to transcribe the text. For batch OCR in Termux: `tesseract` (pkg install tesseract) on a saved screenshot (`screencap -p /sdcard/s.png`).

### 44 Locate an element
By text/id/desc: `deskbot_ui_click` selectors find it (use `ui_tree` to inspect first). By visual description ("the blue button"): `deskbot_screenshot`, locate with vision, then `deskbot_ui_click x= y=` (scale coords: screenshot is ≤960px wide, use `deskbot_screen_state` width/height to convert).

### 45 Detect screen state
`deskbot_screen_state` -> package, keyboard open?, size. Dialog/loading: `deskbot_ui_tree` look for "OK"/"Cancel"/progress classes; current activity: `deskbot_shell cmd="dumpsys activity activities | grep -E 'ResumedActivity|mResumedActivity'"`.

### 46 Wait for or watch a change
`deskbot_ui_wait_for text=<t> [gone=true] timeout_ms=…` for text. To watch a region/page: loop `deskbot_screenshot` every few seconds and diff with vision; announce when changed (cap the loop; use cron for long watches).

### 47 Read charts and tables on screen
`deskbot_screenshot` + vision: "convert the table to CSV" / "describe the chart trend". Save CSV with `deskbot_file_write`.

### 48 Foreground app detection
`deskbot_screen_state` (package). Details: `dumpsys window | grep -E "mCurrentFocus|mFocusedApp"`; split-screen: `dumpsys activity activities | grep -i "windowing mode"`.

### 49 Understand a screen recording
Record: `screenrecord --time-limit 20 /sdcard/rec.mp4` (via `deskbot_shell`). Extract frames in Termux: `ffmpeg -i rec.mp4 -vf fps=1 f_%03d.jpg`, then describe frames with vision and summarize the steps. Heavy: needs `pkg install ffmpeg`.

### 50 Learn an app layout
Walk the app: for each screen run `deskbot_ui_tree`, record `(screen id → clickable labels → next screen)` as a small graph in a skill note `app-<package>.md` (selectors first, coordinates last). Re-validate after app updates (store `versionName` from `deskbot_app_info`).
