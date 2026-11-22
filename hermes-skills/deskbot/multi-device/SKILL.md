---
name: deskbot-multi-device
description: "Control other Android devices over ADB, fleet status, rollouts, PC bridge from the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-root-termux]
---

# Multi-device ADB

Termux has `adb` (`pkg install android-tools`). Always confirm the target device with the user for anything beyond read-only.

### 351 Connect to other Android devices
`adb pair <ip>:<pairport> <code>` (once), `adb connect <ip>:<port>`; saved devices in memory (`name → ip:port`); list `adb devices -l`.

### 352 Run commands on remote devices
`adb -s <serial> shell <cmd>`; the same recipes as this phone's skills apply (settings, am, pm, input). Prefix each result with the device name.

### 353 Mirror or control another screen
`scrcpy` needs a PC; on-phone alternative: `adb -s <s> exec-out screencap -p > shot.png` + vision, then `adb -s <s> shell input tap X Y` to act.

### 354 Move files across devices
`adb -s <s> push|pull`, or `scp`/`rsync` for PCs; verify with `sha256sum` on both ends.

### 355 Fleet status
Loop devices: battery `dumpsys battery | grep level`, `getprop ro.build.version.release`, online state from `adb devices`. Present a table.

### 356 Install an app on many devices
`for d in $(adb devices | awk 'NR>1 && $2=="device"{print $1}'); do adb -s $d install -r app.apk; done`; report per-device success.

### 357 Sync settings across devices
Export settings from the source (#19), apply selected keys to each target with `settings put`, skip device-specific keys (serials, accounts, provisioning).

### 358 Family device assist
Only with the device owner's consent: app blocks `pm suspend`, time limits via cron enabling/disabling `cmd package suspend`, and a visible notification on the device explaining the rules.

### 359 Cross-device test runs
`adb -s <s> shell am instrument -w <pkg>.test/androidx.test.runner.AndroidJUnitRunner` per device in parallel (background jobs), collect results.

### 360 PC bridge
Hand heavy tasks (builds, ML, long downloads) to a PC over SSH (`ssh pc "cmd"`), poll for completion, copy results back, speak a summary. Use key-based auth.
