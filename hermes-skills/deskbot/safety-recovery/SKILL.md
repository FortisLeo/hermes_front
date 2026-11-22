---
name: deskbot-safety-recovery
description: "Backups, restore, watchdog, kill switch, destructive-command guard, spending guard, rollback, safe mode, incident reports for the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-root-termux]
---

# Backup, recovery and safety

### 381 Back up Hermes state
Termux: `tar -czf /sdcard/deskbot-backup/hermes_$(date +%F).tgz -C ~ .hermes --exclude=.hermes/cache --exclude=.hermes/audio_cache --exclude='*.sock' --exclude='*.lock'`; keep 7 dailies; off-device copy with `rclone copy` / `scp` to a PC. Verify with `tar -tzf | head` and `sha256sum`. Cron nightly 03:30.

### 382 Back up phone data
Selected apps/files/settings per #19, #95, plus `/sdcard/DCIM` via `rsync`/`rclone`; list what was included and the destination in the confirmation.

### 383 Restore from backup
Stop Hermes (ask first), extract into `~`, `chown` to the Termux uid (`stat -c %u ~`), restart, verify `/health` and a memory lookup. Restore settings/apps per #19/#95 with verification reads.

### 384 Watchdog and auto-recovery
`scripts/termux/watchdog.sh` (every 30 s): restarts the gateway when `/health` fails and starts the app service when port 8643 is closed; logs to `~/.hermes/logs/watchdog.log`. Escalate (notification + spoken alert) after 3 restarts in 10 min.

### 385 Kill switch
Voice "emergency stop"/"kill switch" (app) stops speech/tools and sets a flag; hardware combo planned. From Termux: `touch ~/.hermes/KILL` (the bridge refuses calls while it exists: planned), or stop the gateway window. After a kill, restart only when the user says so.

### 386 Destructive-command guard
The shell tool refuses `dd`, `mkfs`, `fastboot`, `rm -rf /`, partition tools, bootloader/recovery reboots, wipe/factory-reset patterns and block-device writes. Dry-run: for risky commands first print what would change (`ls`, `echo`, `--dry-run`) and ask for confirmation. Every call is audit-logged.

### 387 Spending guard
Per-session tool-call cap and daily token budget: stop and ask when 80% is reached; circuit-break repeated failing loops (3 identical failures → stop, explain). Provider-side spending cap stays enabled.

### 388 Roll back a change
Settings: restore from the #19 backup for the changed keys; skills: snapshot revert (#318); modules: `ksud module disable <id>` + reboot; app: reinstall last-good APK (#405). Always say what was reverted and verify.

### 389 Safe mode
After repeated crashes (app crash loop in `logcat -b crash`, or bootguard trip): disable agent-installed modules, start the app with only core tools (voice+face), and tell the user. Exit by explicit request after the cause is fixed. App safe-mode flag planned.

### 390 Incident report
After a failure: write `/sdcard/deskbot/incidents/<ts>.md` (what happened, evidence from logs/audit, root cause, fix, prevention) and give a 2-sentence spoken summary.
