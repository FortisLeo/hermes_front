---
name: deskbot-apps
description: "Launch, stop, install, update, uninstall, inventory apps; grant permissions, app ops, data and backups on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-settings]
---

# App lifecycle, data and permissions

Package names via `deskbot_app_list query=<text> user_only=false`. Root commands via `deskbot_shell`.

### 81 Launch an app
`deskbot_app_open name=<label or package>`; deep link `am start -a android.intent.action.VIEW -d "<uri>"`; shortcut `am start -n <pkg>/<activity>`; `monkey -p <pkg> 1` as fallback.

### 82 Stop, freeze, or restart an app
Force-stop `am force-stop <pkg>`; soft `deskbot_app_stop`; freeze `pm disable-user --user 0 <pkg>` (undo `pm enable <pkg>`); suspend `cmd package suspend <pkg>`; restart = force-stop then `deskbot_app_open`.

### 83 Install an app
APK: `pm install -r -g <path>` (split: `pm install-multiple -r -g a.apk b.apk`). URL: `deskbot_http_request` cannot save binaries; use Termux `curl -L -o /sdcard/x.apk <url>` then `pm install`. Play Store: open `market://details?id=<pkg>` and tap Install via `deskbot_ui_click`. Verify with `deskbot_app_info`.

### 84 Update, downgrade, or pin a version
Versions: `deskbot_app_info` (version_code). Downgrade: `pm install -r -d <apk>`. Pin: `pm set-install-location`/disable auto-update in the store UI. Check store versions through web research (`web_search`).

### 85 Uninstall or hide an app
`pm uninstall <pkg>` (user apps); system apps for user 0: `pm uninstall --user 0 <pkg>` (reversible with `cmd package install-existing <pkg>`); hide: `pm hide <pkg>`. Confirm with the user before removing anything they use.

### 86 Inventory installed apps
`deskbot_app_list user_only=true`; sizes/last used: `dumpsys usagestats | grep -E "package=|totalTimeInForeground"`; install source `pm list packages -i | grep <pkg>`; sizes `dumpsys diskstats`.

### 87 Switch between apps
Recents: `deskbot_ui_global recents` then `deskbot_ui_click` the card; bring to front `deskbot_app_open`; split screen: `am start -n <pkg>/<act> --windowingMode 3` (varies).

### 88 App defaults and links
`pm get-app-links <pkg>`; `pm set-app-links --package <pkg> 0 <domain>`; handlers: `cmd package resolve-activity --brief -a android.intent.action.VIEW -d <uri>`; open by default UI: `am start -a android.settings.APP_OPEN_BY_DEFAULT_SETTINGS -d package:<pkg>`.

### 89 Detect an app crash or freeze
`logcat -d -b crash -t 100`; `dumpsys activity processes | grep -i "crashing\|notResponding"`; ANR traces `/data/anr/`. Restart per #82 and report the exception line.

### 90 Keep an app running
`dumpsys deviceidle whitelist +<pkg>`; `appops set <pkg> RUN_IN_BACKGROUND allow`; HyperOS autostart `appops set <pkg> 10008 allow`; auto-relaunch: Termux watchdog (`scripts/termux/watchdog.sh`) or a cron job checking `pidof <pkg>`.

### 91 Grant or revoke permissions
`pm grant <pkg> android.permission.X` / `pm revoke ...`; bulk: loop over `dumpsys package <pkg> | grep "permission\."`; audit: `deskbot_app_info package=<pkg>` (granted list).

### 92 Manage app ops
`appops set <pkg> SYSTEM_ALERT_WINDOW|WRITE_SETTINGS|GET_USAGE_STATS|SCHEDULE_EXACT_ALARM|MANAGE_EXTERNAL_STORAGE allow|deny|ignore`; read `appops get <pkg>`.

### 93 Clear cache or data
Data+cache `pm clear <pkg>`; cache only: `deskbot_shell cmd="rm -rf /data/data/<pkg>/cache/* /sdcard/Android/data/<pkg>/cache/*"`; all caches: `pm trim-caches 4G`. Warn before clearing data (logs the user out).

### 94 Read app private data
Root: `ls /data/data/<pkg>`; SQLite `sqlite3 /data/data/<pkg>/databases/x.db ".tables"` (Termux `pkg install sqlite`); prefs `cat /data/data/<pkg>/shared_prefs/*.xml`. Treat as sensitive; summarize, do not read tokens aloud.

### 95 Back up and restore app data
`tar -czf /sdcard/deskbot-backup/<pkg>.tgz -C /data/data <pkg>` + `pm path <pkg>` APK copy; restore `tar -xzf` then `restorecon -R /data/data/<pkg>` and `chown` to the app uid (`stat -c %u /data/data/<pkg>`).

### 96 Export or import app settings
Copy config files between app dirs and Termux with `cp` (root) into `/sdcard/...`; Termux reads `/sdcard` after `termux-setup-storage`. Record the paths in the skill note for that app.

### 97 App storage report
`dumpsys diskstats` (app/data/cache sizes) or `du -sk /data/data/* | sort -n | tail -20`. Present the top 10.

### 98 App notification settings
`cmd notification set_importance`-style: `cmd notification disallow_listener`... For channels use UI: `am start -a android.settings.APP_NOTIFICATION_SETTINGS --es android.provider.extra.APP_PACKAGE <pkg>`; block all: `cmd appops set <pkg> POST_NOTIFICATION deny`.

### 99 Per-app network access
Block: `cmd netpolicy add restrict-background-blacklist <uid>`; firewall chain: `cmd connectivity set-uid-firewall-rule`-style or `iptables -I OUTPUT -m owner --uid-owner <uid> -j REJECT`. Usage: `dumpsys netstats --uid <uid>`. Undo each rule explicitly and record it.

### 100 Verify an app
`pm path <pkg>` then `sha256sum <apk>`; signature `dumpsys package <pkg> | grep -A3 "signatures"` or `apksigner verify --print-certs`. Compare with a known-good hash stored in memory.
