---
name: deskbot-root-termux
description: "Root and module management, Termux packages/tmux/boot jobs, Hermes start/stop/update/self-diagnosis on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-monitor]
---

# Root, Termux and Hermes self-management

Danger zone: modules and boot-time changes can brick the phone. The bootguard module must exist before installing any other module. Ask the user first for anything that reboots or edits `/data/adb`.

### 271 Check root state
`su -v`, `ksud --version`, `ksud module list`; app root grant: `ksud profile get <pkg>` (allow_su true/false). Report KernelSU Next version and active modules.

### 272 Install, enable, disable modules
`ksud module install <zip>`, `ksud module enable|disable <id>`, `ksud module uninstall <id>`, `ksud module list`. Reboot needed. Fallback: open the KernelSU Next manager (`deskbot_app_open`) and use the UI.

### 273 Module scouting
Skill flow: search GitHub for repos with `module.prop`; vet `customize.sh`, `service.sh`, `post-fs-data.sh`, `sepolicy.rule` for `dd`, block writes, `curl | sh`, `setenforce`; pin the commit; fork locally; zip; install one module at a time; reboot; smoke test; write a capability file (id, source, commit, enables, detect, test, rollback). Skip tuners/overclockers.

### 274 Module health tests
Per module run its `detect`/`test` command from the capability file and report pass/fail; disable on failure (`ksud module disable <id>`).

### 275 Bootguard control
Own module: counter file under `/data/adb/bootguard/`; Hermes clears it after healthy start (`echo 0 > …`). Status `cat /data/adb/bootguard/count`; disable-all trigger touches `disable` in each agent-installed module dir. Not yet installed (planned module).

### 276 Superuser grants
`ksud profile get <pkg>`; set with `ksud profile set '<json>'` (use the JSON printed by `get`, change `allow_su`). Granting root to a new app: only with the user's explicit consent. Fallback: KernelSU manager UI.

### 277 SELinux inspection
`getenforce`; denials `logcat -d | grep "avc: denied" | tail`; `dmesg | grep avc`. Suggest a targeted rule in a module's `sepolicy.rule`; **never** `setenforce 0`.

### 278 System partition read
`cat /system/build.prop`, `ls /vendor/etc`, overlays `cmd overlay list`; read-only. Do not remount partitions without a module path.

### 279 Systemless overrides
Hosts: module with `system/etc/hosts`; properties via `system.prop` in the module; fonts via a font module. Create using the module-scout flow, test with reboot.

### 280 Boot image backup
`dd` is on the deny-list for the shell tool; do it manually or with `magiskboot`/`ksud boot-info` guidance: identify slot `getprop ro.boot.slot_suffix`, copy `/dev/block/by-name/init_boot<slot>` using Termux-side tooling the user runs; verify `sha256sum`; store off-phone. Ask the user to run the final copy.

### 282 Manage Termux packages
`pkg update && pkg install <p>`, `pip install <p>`, `npm i -g <p>`; pin `pip install p==ver`; list `pkg list-installed`.

### 283 Manage tmux sessions
`tmux ls`, `tmux new -d -s <name>`, `tmux capture-pane -p -t <s>`, dead windows: `tmux list-panes -a -F '#{pane_dead}'`; restart a window `tmux respawn-pane -k -t <s:w>`. Sessions: `hermes` (windows `deskbot-gateway`, `watchdog`).

### 286 Build small web tools
Flask/FastAPI app in `~/tools/<name>`, run in a tmux window, expose on the LAN port, document in memory; keep them stateless and token-protected.

### 287 Boot and OS-level jobs
Termux:Boot scripts in `~/.termux/boot/` (see `scripts/termux/boot.sh`); Android job scheduler via `cmd jobscheduler run -f <pkg> <jobId>`; cron: Hermes `cronjob` tool.

### 289 Containers and chroot
`pkg install proot-distro; proot-distro install debian; proot-distro login debian`. Root chroot: mount bind and `chroot` is risky; prefer proot.

### 290 Debug the bot's own code
Run tests (`./gradlew test` on the PC, `pytest` in Termux), read tracebacks, propose a patch with `patch`, re-run; for the app see self-improvement skills (401-410).

### 291 Start, stop, restart Hermes in tmux
Gateway window `hermes:deskbot-gateway`: `tmux send-keys -t hermes:deskbot-gateway C-c` then `tmux send-keys -t hermes:deskbot-gateway "hermes gateway run" Enter`. Health: `curl -s http://127.0.0.1:8642/health`. **Restarting interrupts the current conversation: ask first.**

### 293 Track token and cost use
Hermes logs/`state.db`; query token counters from the provider dashboard or Hermes usage command; alert at the daily cap via `deskbot_notification_post`. Spending cap lives with the provider.

### 294 Update Hermes
`hermes update` (or reinstall per docs); before: back up `~/.hermes` (#381); after: `/health` version check; roll back by restoring the backup and previous version.

### 300 Self-diagnosis
Check: `curl /health`, bridge list (`deskbot_device_info` works), recent audit failures, `~/.hermes/logs` tail, latency of the last turns. Explain the failing component and the fix; apply only safe fixes automatically (restart bridge/app service).
