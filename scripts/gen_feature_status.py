#!/usr/bin/env python3
"""Generates docs/Feature status.md from docs/Feature catalog.md. Every catalog feature gets exactly one status.

Statuses (strongest first):
  BUILT   implemented in the app/bridge and verified on the device (tool names listed)
  PARTIAL part is built (listed), the rest is a skill recipe or needs a follow-up
  VERIFIED recipe key command executed OK on the phone via the root shell
  SKILL   Hermes skill recipe written (hermes-skills/deskbot/...); runs on existing tools or the Hermes terminal
  BLOCKED cannot be done on this phone or needs something not installed (reason given)
"""
import re, sys, os
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cat = open(f"{root}/docs/Feature catalog.md").read().split("## Removed")[0].splitlines()
feat = {}
group = ""
for l in cat:
    g = re.match(r"^## (\d+)\. (.+)", l)
    if g: group = g.group(2)
    m = re.match(r"^\| (\d{1,3}) \| (.+?) \| (.*?) \|$", l)
    if m: feat[int(m.group(1))] = (m.group(2).replace("**", ""), group)
assert len(feat) == 384, len(feat)

BUILT = {
 423: "avatar_action + AvatarView (three-vrm in a sandboxed WebView); CoolBanana.vrm (CC0) renders, idles, dances on device (canvas snapshot)",
 424: "TTS word progress -> MouthShaper -> mouth on face (device-verified, screenshot)",
 41: "ui_tree, screenshot", 42: "ui_tree", 44: "ui_click, ui_tree", 45: "screen_state", 46: "ui_wait_for", 48: "screen_state",
 51: "overlay_text", 53: "overlay_highlight", 54: "Face tab full-screen, ui_show full", 56: "notification_post, overlay_text", 57: "overlay_text, screen_dim", 58: "screen_dim",
 61: "ui_click", 62: "ui_swipe", 65: "ui_global", 70: "vibrate",
 71: "ui_type", 72: "clipboard_set", 73: "ui_type",
 81: "app_open", 86: "app_list, app_info",
 101: "notifications_list", 102: "notification_act", 103: "notification_dismiss", 105: "notifications + tts_say", 106: "notification_post", 107: "routine_add (notification event)", 108: "otp_latest", 109: "notification_history",
 111: "camera_capture", 112: "camera_capture + vision (also auto-attached on voice 'what is this')", 117: "presence_check", 120: "torch",
 121: "wake_control (Vosk)", 122: "STT (SpeechRecognizer)", 123: "tts_say", 124: "ConversationLoop barge-in 'stop'", 130: "vibrate, tts_say",
 131: "media_key", 133: "stream_volume", 134: "screenshot",
 151: "battery", 155: "battery (temp_c)", 157: "routine_add (charger events)",
 171: "network_info, http_request", 173: "http_request",
 181: "device_info", 184: "sensor_list, sensor_read", 195: "audit_log, Debug tab", 
 201: "clipboard_get/set", 204: "Share target 'Send to Deskbot'", 210: "clipboard_clear",
 211: "contacts_search/add", 212: "calendar_events", 213: "calendar_add_event", 221: "alarm_set", 222: "timer_*, stopwatch",
 225: "routine_add/list/remove", 261: "location_get", 266: "MotionDetector + routine motion events", 267: "ambient_mode, sensor_read",
 321: "face_set, 15 moods/18 reactions", 323: "idle behaviors", 324: "persona_set (voice/tone)", 327: "night sleep mood", 328: "tap/long-press face", 329: "quick_actions + panel", 330: "Debug tab",
 385: "kill_switch (voice 'emergency stop', volume-down x3, bridge guard)", 386: "DenyList + shell guard + audit",
 404: "self_test", 411: "ui_show", 417: "WebEmbed (sandboxed WebView, network blocked, verified)", 412: "ui_show Toggle/Slider bound", 413: "ui_show DeviceToggle/StatusGauge", 415: "ui_show templates", 416: "ui_show actions -> tools/Hermes",
 419: "default eyes (FaceView)", 420: "face_tweak", 421: "persona_set/previous", 425: "persona theme", 426: "persona_save (+skill flow)", 427: "persona_gallery template", 428: "PersonaValidator license record",
 202: "clipboard events -> routines (clipboard copy fired routine clip-note on device)", 64: "ui_drag (root `input draganddrop`)", 67: "macro_record/play/list/delete", 100: "file_hash + app_info", 125: "sound_watch + SpikeDetector: loud peak raised sound_spike and fired a routine on device", 126: "audio_record", 129: "audio_route + mic_mute", 137: "media_search",
 164: "archive_zip/unzip", 169: "serve_folder", 170: "file_hash", 171: "network_info, port_check, dns_lookup, http_request", 172: "lan_scan", 174: "serve_folder", 176: "wake_on_lan (+ http_request for smart plugs)",
 178: "download_file (resume + sha256)", 186: "health_score", 203: "intent_start (share/SEND)", 205: "quick_note, notes_search", 236: "download_file", 262: "intent_start (google.navigation)", 270: "sensor_log", 341: "mqtt_publish (unit-tested vs fake broker; device: clean failure with no broker)",
 349: "intent_start (print/SEND)", 389: "safe_mode + crash-loop guard: engaged by 4 real rapid restarts, exited from Debug tab",
 429: "15 moods, 18 reactions, fallbacks", 430: "effects layer", 431: "<expr/> tags in chat+voice",
}
PARTIAL = {
 43: "screenshot + vision OCR works; Termux tesseract optional", 47: "screenshot + vision", 52: "face on Face tab; floating bubble not built", 55: "PiP only via apps", 59: "notification_post; custom AOD not built",
 66: "volume/media keys; others need root shell",
 82: "app_stop soft stop; force-stop needs root shell", 91: "app_info shows grants; pm grant needs root shell", 104: "notifications_list + model ranking", 
 114: "vision decode; zbar optional", 115: "vision counting", 116: "visual_watch: capture->thumbnail->diff pipeline verified on device (static scene diff 0.3, no false alarm); detector unit-tested; no live positive event produced", 119: "time-lapse recipe; video via stock camera UI",
 126: "stock recorder via UI", 128: "notification/UI title + web_search", 129: "mic_mute; output switching via UI", 
 137: "file_search + vision", 152: "needs ACC module (not installed); recipe ready", 
 183: "shell (root)", 188: "cron recipe", 
 214: "calendar + web + notification digest recipe", 
 223: "timers + routines + place events (see 264)", 226: "cron recipe", 227: "cron recipe", 228: "timer + DND via shell",
 264: "place_add/list/remove verified on device; PlaceTracker enter/exit unit-tested; polling not field-tested", 
 322: "state faces built; BatteryLow event -> worried face wired (not field-tested)", 325: "skill (greeting rules); arrival needs presence loop", 326: "expr tags; tone detection by model",
 331: "ArrivalFusion (face+sound+motion+visual -> presence arrived/departed event, routine trigger kind=arrived|departed) unit-tested and wired; builds and runs on device, no live arrival produced", 332: "routine + cron recipes", 333: "DND/call checks via shell", 334: "presence_check turns the eyes toward a detected face (GazeMapper unit-tested; no live face available to confirm); sound direction impossible with one mic",
 335: "barge-in stops turn; resume logic is a skill", 336: "recipe uses vibrate/volume/tts", 337: "ambient_mode + sensors", 338: "audit-log mining recipe",
 394: "recipe",  
 412: "Toggle/Slider values local; apply via Button", 414: "overlay surface renders in Screens tab, not as a true window overlay",
}
BLOCKED = {63: "multi-touch gestures disabled: a two-stroke gesture coincided with a HyperOS input-service crash (see memory)", 7: "no NFC hardware on this phone", 344: "no IR blaster on this phone", 275: "bootguard module not installed", 279: "needs a systemless module (not installed)", 
           399: "needs a boot-animation module", 398: "needs a font module", 118: "gesture classification unreliable; experimental recipe only",
           347: "needs external hardware", 348: "needs external hardware", 422: "Live2D engine/assets not built", 60: "annotation canvas not built",
           320: "teach-by-demonstration is experimental (recipe only)", 304: "screen-recall is a recipe; privacy-gated"}
import json
hver = {}
try: hver = {int(k): v for k, v in json.load(open(f"{root}/docs/hermes-verification.json")).items() if v["ok"]}
except FileNotFoundError: pass
ver = {}
try: ver = {int(k): v for k, v in json.load(open(f"{root}/docs/recipe-verification.json")).items() if v["ok"]}
except FileNotFoundError: pass
rows = []
counts = {"BUILT": 0, "VERIFIED": 0, "PARTIAL": 0, "SKILL": 0, "BLOCKED": 0}
for n in sorted(feat):
    name, grp = feat[n]
    if n in BLOCKED: st, why = "BLOCKED", BLOCKED[n]
    elif n in BUILT: st, why = "BUILT", BUILT[n]
    elif n in hver: st, why = "VERIFIED", "Hermes itself performed it: called " + ", ".join(hver[n]["called"][:4])
    elif n in ver: st, why = "VERIFIED", "recipe command ran OK on the phone via root shell: `" + ver[n]["cmd"][:60] + "`"
    elif n in PARTIAL: st, why = "PARTIAL", PARTIAL[n]
    else: st, why = "SKILL", "recipe in hermes-skills"
    counts[st] += 1
    rows.append(f"| {n} | {name} | {st} | {why} |")
out = ["# Feature status", "", "Generated by `scripts/gen_feature_status.py` from the Feature catalog. Every one of the 384 features has a Hermes skill section (enforced by `FeatureCoverageTest`); this table says how much more is built.", "",
       "| Status | Meaning | Count |", "| --- | --- | --- |",
       f"| BUILT | Implemented in the app/bridge and verified on the device | {counts['BUILT']} |",
       f"| VERIFIED | Skill recipe whose key command was executed successfully on the phone through the app's root shell (scripts/verify_recipes.py -> docs/recipe-verification.json) | {counts['VERIFIED']} |",
       f"| PARTIAL | Part built, rest is a recipe or follow-up | {counts['PARTIAL']} |",
       f"| SKILL | Recipe written; not yet executed on the device | {counts['SKILL']} |",
       f"| BLOCKED | Impossible here or needs hardware/modules/engines not present | {counts['BLOCKED']} |", "",
       "| # | Feature | Status | Where / why |", "| --- | --- | --- | --- |"] + rows
open(f"{root}/docs/Feature status.md", "w").write("\n".join(out) + "\n")
print(counts, sum(counts.values()))
