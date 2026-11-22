---
name: deskbot-self-improvement
description: "Edit, build, install, health-check and roll back the Deskbot Android app itself; changelog, UI self-tuning, crash-driven fixes."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-safety-recovery]
---

# App self-improvement

Source repo: `~/deskbot-app` (git). Package `com.rishitgoklani.hermes_front_mobile`. Rules: **one change per build**, tests first, always keep a last-good APK, recovery scripts live outside the editable tree.

### 401 Edit the app's own source
`git -C ~/deskbot-app checkout -b feat/<slug>`; patch Kotlin/layouts/manifest with `patch`; keep changes small; follow `CLAUDE.md` (module rules, tests per class).

### 402 Build the APK
Remote PC build (preferred): `ssh pc "cd ~/deskbot-app && ./gradlew testDebugUnitTest assembleDebug"` then copy `app/build/outputs/apk/debug/app-debug.apk` back. On-device Gradle needs JDK 17 + arm64 `aapt2`; try only if the PC is unavailable; expect slowness and OOM.

### 403 Install and restart itself
`cp app-debug.apk /sdcard/deskbot-backup/last-good.apk` (of the *current* install first via `pm path`), then `pm install -r -g app-debug.apk`; `sh grant_all.sh`; relaunch: `am start-foreground-service -n <pkg>/.BotService`.

**After any update that adds or renames bridge tools, restart the Hermes gateway** (`tmux send-keys -t hermes:deskbot-gateway C-c` then `hermes gateway run`, after asking the user): the deskbot-phone plugin registers `deskbot_*` tools only at gateway start, so new tools are invisible until then. Verify with `deskbot_audit_log`/a test call of the new tool.

### 404 Post-update health check
Within 60 s: bridge answers (`deskbot_device_info`), camera (`deskbot_camera_capture` size > 0), speech (`deskbot_tts_say` "systems check"), accessibility (`deskbot_ui_tree` completes), Hermes chat round-trip. Any failure → #405.

### 405 Roll back a bad build
`pm install -r -d /sdcard/deskbot-backup/last-good.apk` (needs a debuggable build for -d downgrade), `git revert <commit>`, write a skill note explaining the cause, notify the user.

### 406 Hot-loaded features
Hermes-side Python tools/skills reload with a gateway restart; the face parameters reload through `deskbot_face_tweak`/persona packs (no rebuild). Script-plugin loading in the app is a future design item.

### 407 Feature request loop
When a skill fails because the app lacks an ability: write the gap into `~/deskbot-app/docs/gaps.md`, propose the change to the user, then run #401-#404 on approval.

### 408 App changelog
`git -C ~/deskbot-app log --oneline -20` and tags (`git tag v1.<n>`); "what's new" = summarize commits since the last announced version.

### 409 UI self-tuning by voice
Face/theme tweaks via `deskbot_face_tweak` and `deskbot_persona_set` (see persona skill); layout changes need a rebuild (#401).

### 410 Crash-driven fixes
`logcat -d -b crash | grep -A20 <pkg>`; locate the file:line, write a failing test reproducing it, fix, rebuild per #402, health-check #404.
