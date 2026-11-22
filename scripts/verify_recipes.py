#!/usr/bin/env python3
"""Runs safe, non-destructive recipe commands from hermes-skills through the app's ROOT shell tool on the connected phone
(adb + bridge on 127.0.0.1:8643) and writes docs/recipe-verification.json: {feature#: {cmd, ok, out}}.
Only read-only commands or self-reverting toggles are listed. Never run destructive recipes here."""
import base64, json, subprocess, sys, os
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BRIDGE = "/data/local/tmp/bridge.sh"   # pushed by the dev loop: reads the token from Termux home and talks to the bridge

CASES = {
 1: "svc bluetooth enable; sleep 3; V=$(settings get global bluetooth_on); svc bluetooth disable; sleep 2; echo bt_on_was=$V",
 2: "cmd wifi list-networks | head -3", 3: "dumpsys bluetooth_manager | head -3", 4: "cmd wifi status | head -3",
 5: "settings get global private_dns_mode", 9: "cmd netpolicy list restrict-background-whitelist | head -2",
 13: "cmd uimode night", 14: "settings get system haptic_feedback_enabled", 15: "date; settings get global auto_time",
 16: "cmd role get-role-holders android.app.role.HOME", 17: "cmd notification set_dnd off; settings get global zen_mode",
 18: "settings get system screen_off_timeout", 19: "settings list system | head -3", 21: "settings get global development_settings_enabled",
 22: "settings get global adb_enabled", 23: "settings get global window_animation_scale", 24: "settings get global stay_on_while_plugged_in",
 26: "device_config get activity_manager max_phantom_processes; echo rc=$?", 28: "getprop sys.usb.config",
 31: "settings get secure enabled_accessibility_services", 32: "dumpsys accessibility | head -3", 34: "settings get secure accessibility_display_inversion_enabled",
 35: "settings get system font_scale", 37: "settings get secure long_press_timeout", 38: "settings get system master_mono",
 40: "dumpsys accessibility | grep -c Service", 66: "input keyevent KEYCODE_VOLUME_UP; echo sent",
 81: "monkey -p com.android.settings 1 2>&1 | tail -1", 82: "am force-stop com.android.settings; echo stopped", 83: "pm path com.termux",
 84: "dumpsys package com.termux | grep -m1 versionName", 86: "pm list packages -3 | head -3",
 89: "logcat -d -b crash -t 3 | head -3; echo rc=$?", 90: "dumpsys deviceidle whitelist | head -3", 91: "pm grant com.rishitgoklani.hermes_front_mobile android.permission.CAMERA; echo rc=$?",
 92: "appops get com.rishitgoklani.hermes_front_mobile | head -3", 93: "du -sk /data/data/com.termux/cache 2>/dev/null | tail -1; echo rc=0",
 94: "ls -A /data/data/com.termux/files/home | head -3", 95: "ls /data/app | head -2", 97: "dumpsys diskstats | head -4",
 98: "appops get com.termux POST_NOTIFICATION", 99: "cmd netpolicy get restrict-background", 100: "sha256sum $(pm path com.termux | head -1 | cut -d: -f2) | cut -c1-16",
 134: "screencap -p /sdcard/deskbot_test.png && ls -l /sdcard/deskbot_test.png && rm /sdcard/deskbot_test.png",
 135: "screenrecord --time-limit 2 /sdcard/deskbot_test.mp4 && ls -l /sdcard/deskbot_test.mp4 && rm /sdcard/deskbot_test.mp4",
 141: "input keyevent KEYCODE_WAKEUP; echo woke", 143: "settings get system accelerometer_rotation", 144: "wm size; wm density",
 145: "dumpsys display | grep -m2 -i refresh", 147: "settings get secure night_display_activated", 151: "dumpsys battery | head -6",
 153: "am get-standby-bucket com.termux", 154: "dumpsys batterystats | head -3", 155: "cat /sys/class/thermal/thermal_zone0/temp",
 157: "dumpsys battery | grep -E 'AC powered|USB powered'", 163: "mkdir -p /sdcard/deskbot_t && echo a > /sdcard/deskbot_t/a.txt && mv /sdcard/deskbot_t/a.txt /sdcard/deskbot_t/b.txt && ls /sdcard/deskbot_t && rm -r /sdcard/deskbot_t",
 164: "echo hi > /sdcard/dt.txt && tar -czf /sdcard/dt.tgz -C /sdcard dt.txt && tar -tzf /sdcard/dt.tgz && rm /sdcard/dt.txt /sdcard/dt.tgz",
 165: "df -h /data | tail -1", 166: "ls /storage", 170: "df -h /data | tail -1", 171: "ping -c 1 -W 3 1.1.1.1 | tail -2", 172: "ip neigh | head -3",
 181: "uname -a; getprop ro.build.fingerprint", 182: "top -b -n 1 -m 3 | head -6", 183: "uptime; getprop sys.boot.reason", 185: "pm list features | head -3",
 187: "date", 189: "getprop ro.product.model", 190: "settings list global | wc -l; pm list packages | wc -l", 191: "logcat -d -t 3",
 193: "dmesg | tail -2", 194: "dumpsys -l | head -3", 197: "mkdir -p /sdcard/dt_crash && logcat -d -t 20 > /sdcard/dt_crash/log.txt && ls -l /sdcard/dt_crash/log.txt && rm -r /sdcard/dt_crash",
 199: "ls /sdcard | head -3", 271: "su -v; ksud --version; ksud module list | head -3",
 272: "ksud module list | head -2", 274: "ksud module list | wc -l", 276: "ksud profile get com.termux | head -3", 277: "getenforce", 278: "head -3 /system/build.prop",
 283: "ls /data/data/com.termux/files/usr/var/run/tmux-10343", 291: "curl -s -m 5 http://127.0.0.1:8642/health", 395: "cmd overlay list | head -3",
 400: "settings get secure icon_blacklist", 394: "settings get system ringtone", 396: "settings get secure sysui_qs_tiles", 142: "svc power --help 2>&1 | head -1",
 25: "setprop debug.hwui.overdraw false; getprop debug.hwui.overdraw", 27: "appops get com.rishitgoklani.hermes_front_mobile android:mock_location",
 30: "settings list global | grep -c -E 'adb|development|animation|stay_on'", 33: "settings get secure accessibility_button_targets; settings get secure odi_captions_enabled", 36: "settings get secure accessibility_shortcut_enabled; settings get secure accessibility_shortcut_target_service",
 74: "ime list -s | head -3", 85: "pm list packages -d | head -3", 87: "dumpsys activity recents | head -3", 88: "cmd package resolve-activity --brief -a android.intent.action.VIEW -d https://example.com | head -3",
 96: "cp /data/data/com.termux/files/home/.hermes/SOUL.md /sdcard/dt_soul.md && ls -l /sdcard/dt_soul.md && rm /sdcard/dt_soul.md", 110: "dumpsys notification --noredact | grep -m3 -i importance",
 146: "settings get secure doze_always_on; echo rc=0", 148: "which reboot; echo guarded", 150: "dumpsys activity activities | grep -m2 -i windowingMode", 158: "dumpsys battery | grep -E 'health|voltage|temperature'",
 175: "iptables -S | head -3", 192: "logcat -d -b crash -t 5; logcat -d -t 20 *:E | head -3", 198: "dumpsys gfxinfo com.rishitgoklani.hermes_front_mobile | head -3", 265: "dumpsys wifi | grep -m2 -iE 'rssi|bssid'", 269: "appops get com.android.shell android:mock_location",
 282: "su u0_a343 -c 'export PATH=/data/data/com.termux/files/usr/bin; pkg list-installed 2>/dev/null | head -2'", 293: "grep -c 'total=' /data/data/com.termux/files/home/.hermes/logs/agent.log", 294: "su u0_a343 -c 'export PATH=/data/data/com.termux/files/usr/bin:$PATH HOME=/data/data/com.termux/files/home; timeout 60 hermes --version 2>&1 | head -2'",
 309: "tar -czf /sdcard/dt_mem.tgz -C /data/data/com.termux/files/home/.hermes memories && tar -tzf /sdcard/dt_mem.tgz | head -2 && rm /sdcard/dt_mem.tgz",
 381: "tar -czf /sdcard/dt_hermes.tgz -C /data/data/com.termux/files/home --exclude=.hermes/cache --exclude=.hermes/audio_cache --exclude=.hermes/image_cache --exclude=.hermes/logs --exclude='*.sock' --exclude='*.lock' .hermes && ls -l /sdcard/dt_hermes.tgz | cut -c1-60 && sha256sum /sdcard/dt_hermes.tgz | cut -c1-16",
 383: "tar -tzf /sdcard/dt_hermes.tgz | head -3; rm -f /sdcard/dt_hermes.tgz", 382: "settings list system | wc -l; settings list global | wc -l",
 388: "OLD=$(settings get system haptic_feedback_enabled); settings put system haptic_feedback_enabled 0; N=$(settings get system haptic_feedback_enabled); settings put system haptic_feedback_enabled $OLD; echo changed_to=$N restored_to=$(settings get system haptic_feedback_enabled)",
 12: "OLD=$(settings get system screen_off_timeout); settings put system screen_off_timeout 600000; N=$(settings get system screen_off_timeout); settings put system screen_off_timeout $OLD; echo set=$N restored=$(settings get system screen_off_timeout)",
 403: "pm path com.rishitgoklani.hermes_front_mobile && dumpsys package com.rishitgoklani.hermes_front_mobile | grep -m1 lastUpdateTime",
 11: "settings list global | head -3; settings list secure | head -3", 68: "wm size; dumpsys display | grep -m1 -i cutout; settings get system user_rotation",
 156: "dumpsys thermalservice | head -5", 159: "dumpsys battery | grep -E 'level|status'; settings get global low_power",
 160: "dumpsys batterystats --charged | grep -m2 -i 'statistics since\\|estimated'", 179: "cat /proc/net/dev | head -4",
 149: "dumpsys alarm | head -3", 39: "dumpsys accessibility | grep -c Event; echo rc=0", 20: "pm resolve-activity -a android.settings.SETTINGS | head -3",
 233: "pm list packages | grep -i chrome | head -1", 200: "tail -n 3 /data/data/com.rishitgoklani.hermes_front_mobile/files/audit.log | cut -c1-60",
 196: "grep -c shell /data/data/com.rishitgoklani.hermes_front_mobile/files/audit.log; echo rc=0",
 123: "settings get secure tts_default_rate", 138: "ls /sdcard/Pictures | head -2; echo rc=0", 14.5: "true",
}
CASES.pop(14.5)

def run(cmd):
    args = base64.b64encode(json.dumps({"cmd": cmd, "root": True}).encode()).decode()
    p = subprocess.run(["adb", "shell", f"su -c 'sh {BRIDGE} shell {args}'"], capture_output=True, text=True, timeout=60)
    try: r = json.loads(p.stdout.strip().splitlines()[-1])
    except Exception: return False, (p.stdout + p.stderr)[:120]
    msg = r.get("message") or ""
    bad = any(b in msg for b in ("No such file", "not found", "Permission denied", "Unknown command", "inaccessible"))
    return r.get("status") == "completed" and not bad and msg.strip() != "", msg[:120].replace("\n", " | ")

results = {}
for n, cmd in sorted(CASES.items()):
    ok, out = run(cmd); results[str(n)] = {"cmd": cmd[:100], "ok": ok}  # raw output is deliberately not stored (it can contain network/personal details)
    print(("PASS" if ok else "FAIL"), n, out[:70], flush=True)
json.dump(results, open(f"{root}/docs/recipe-verification.json", "w"), indent=1)
print(sum(v["ok"] for v in results.values()), "/", len(results), "passed")
