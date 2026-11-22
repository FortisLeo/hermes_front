---
name: deskbot-memory-skills
description: "Personal knowledge base, journal, object memory, memory hygiene, and skill learning/registry/regression for the Deskbot bot."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-safety-recovery]
---

# Memory, knowledge and skill learning

Use Hermes `memory` for facts and `session_search` for past chats. Skills live under `~/.hermes/skills/deskbot/`.

### 303 Personal knowledge base
Add notes/links/docs: save text to `/sdcard/deskbot/kb/<slug>.md` (`deskbot_file_write`) and a one-line index entry in memory. Ask later by searching the index first, then `deskbot_file_search path=/sdcard/deskbot/kb text=<q>`. Heavy RAG (embeddings) only if the KB exceeds ~200 files.

### 304 Remember what was on screen
When asked "remember this page": `deskbot_screenshot` + `deskbot_ui_tree`, store a 3-line summary + app + timestamp in memory. Recall by keyword. Do this only on request (privacy).

### 305 Daily journal
Cron 21:30: summarize the day's conversations, completed tasks, failures and learned skills into `/sdcard/deskbot/journal/<date>.md`; speak a 2-sentence recap if the user is present.

### 306 People and relationship notes
Memory entries `person:<name>` with preferences, last topic, birthday. Update after relevant chats; never store sensitive data (health, finances) unless asked.

### 307 Object memory
On "where are my keys?" capture `deskbot_camera_capture facing=back`, describe objects/locations, store `object:<name> → place, time`. Answer from the latest sighting and say how old it is.

### 308 Memory hygiene
Weekly cron: merge duplicates, expire stale facts (>90 days, unconfirmed), and ask the user about conflicts before overwriting.

### 309 Export and import memory
`tar -czf /sdcard/deskbot-backup/hermes-memory_<date>.tgz -C ~/.hermes memories state.db skills` (Termux); import by extracting into the new device's `~/.hermes` with Hermes stopped.

### 310 Lists and purchase memory
Named lists in memory (`list:shopping`), add/remove/read aloud; purchases with date/price; reorder reminders via cron based on average interval.

### 314 Method promotion
When a UI-driven step has a command alternative (found via `dumpsys`, `cmd`, `settings`), rewrite the skill to try the command first and keep the UI as fallback; record `tested_on: Android <ver> <date>`.

### 315 Per-app UI recipes
After a successful multi-step UI task, save `app-<package>.md` in the skill dir: screens, selectors (text/id first), quirks, app `versionName`. On a failed selector, re-run `deskbot_ui_tree`, update, and retest.

### 316 Regression test runner
Nightly cron runs the ~10 scripted tasks (toggle Wi-Fi, take photo, open settings, read notification, tap a known button, battery, timer, http check, screenshot, file roundtrip), logs pass/fail to `/sdcard/deskbot/regression/<date>.md`, disables a skill that fails twice (rename `SKILL.md`→`SKILL.md.disabled`), and reports.

### 317 Capability registry
Maintain `~/.hermes/skills/deskbot/REGISTRY.md`: capability → last verified date → success rate → method used. Update it from #316 and from live use.

### 318 Skill versioning and rollback
Before editing a skill: `cp -r <skill> ~/.hermes/skill-snapshots/<name>_<ts>`; diff with `diff -ru`; revert by copying the snapshot back. Keep the last 10.

### 319 Learn from corrections
On "that was wrong"/"good job": update the relevant skill note (what failed, what worked), bump its version patch number, and snapshot first (#318).

### 320 Teach by demonstration
Experimental: the user performs a task while you poll `deskbot_screen_state` + `deskbot_ui_tree` (every ~2 s) and log screens/clicks (visible in accessibility events); then draft a skill from the log and replay it once to verify.
