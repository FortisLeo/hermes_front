#!/usr/bin/env python3
"""Asks the real Hermes agent (via its API on the phone) to perform feature tasks and checks, from Hermes's own tool log,
that the expected tools were called. Writes docs/hermes-verification.json {feature#: {prompt, expected, called, ok}}."""
import json, os, subprocess, time
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = "/data/data/com.termux/files/home/.hermes/logs/agent.log"
CASES = [
 (214, "Give me a short spoken morning briefing using my calendar events for today and the current battery level. Use your deskbot tools.", ["deskbot_calendar_events", "deskbot_battery"]),
 (305, "Write a one-line daily journal entry saying 'verified journal flow' into /sdcard/Deskbot/journal.md using your deskbot file tools (append).", ["deskbot_file_write"]),
 (373, "Log an expense: 12.50 for coffee. Append the line 'coffee,12.50' to /sdcard/Deskbot/expenses.csv using your deskbot file tools.", ["deskbot_file_write"]),
 (303, "Save this note to my knowledge base with your deskbot quick note tool: 'router admin page is 192.168.0.1'. Then search the notes for 'router' and tell me what you find.", ["deskbot_quick_note", "deskbot_notes_search"]),
 (310, f"Always call the deskbot quick_note tool now (even if a similar note exists): save a NEW note 'buy oat milk run-{__import__('time').strftime('%H%M%S')}' tagged 'shopping'.", ["deskbot_quick_note"]),
 (339, "Summarize what notifications I got recently using your deskbot notification history digest tool, in one sentence.", ["deskbot_notification_history"]),
 (300, "Run your deskbot self test and tell me if anything fails.", ["deskbot_self_test"]),
 (186, "What is the phone's health score right now? Use your deskbot health score tool.", ["deskbot_health_score"]),
 (316, "Run a quick regression: call deskbot battery, deskbot screen_state, deskbot http_request to https://example.com, and a deskbot file write+read roundtrip in /sdcard/Deskbot/regress.txt. Report PASS/FAIL per step.", ["deskbot_battery", "deskbot_screen_state", "deskbot_http_request", "deskbot_file_write", "deskbot_file_read"]),
 (307, "Look at the room with the back camera using your deskbot camera tool, and then you MUST call the deskbot quick_note tool to save a note tagged 'objects' with one sentence about what you saw (if the image is unclear, write 'camera check: nothing clear').", ["deskbot_camera_capture", "deskbot_quick_note"]),
 (222, "Start a 15 second timer called pasta with your deskbot timer tool and then list the running timers.", ["deskbot_timer_start", "deskbot_timer_list"]),
 (321, "Make your face sleepy using your deskbot face tool, then switch the persona to night-owl.", ["deskbot_face_set", "deskbot_persona_set"]),
 (411, "Show a screen on the phone titled 'Hello' with one Button that dismisses it, using your deskbot ui_show tool.", ["deskbot_ui_show"]),
 (178, "Download https://example.com into /sdcard/Deskbot/dl.html with your deskbot download tool and verify its sha256 using the file hash tool.", ["deskbot_download_file", "deskbot_file_hash"]),
 (176, "Send a Wake-on-LAN packet to MAC 00:11:22:33:44:55 using your deskbot tool.", ["deskbot_wake_on_lan"]),
 (172, "Scan my local network for devices using your deskbot lan scan tool and list what you find.", ["deskbot_lan_scan"]),
]
def adb(cmd, t=240): return subprocess.run(["adb", "shell", cmd], capture_output=True, text=True, timeout=t).stdout
def lines(): return int((adb(f"su -c 'wc -l < {LOG}'") or "0").strip() or 0)
import sys
only = {int(a) for a in sys.argv[1:]}
OUT = f"{root}/docs/hermes-verification.json"
results = json.load(open(OUT)) if only and os.path.exists(OUT) else {}
for n, prompt, expected in CASES:
    if only and n not in only: continue
    before = lines()
    open("/tmp/ask.json", "w").write(json.dumps({"model": "hermes-agent", "messages": [{"role": "user", "content": prompt}]}))
    subprocess.run(["adb", "push", "/tmp/ask.json", "/data/local/tmp/ask.json"], capture_output=True)
    reply = adb("su -c 'sh /data/local/tmp/ask.sh'", 280).strip()
    tail = adb(f"su -c 'tail -n +{before + 1} {LOG}'")
    called = sorted({l.split("tool ")[1].split(" ")[0] for l in tail.splitlines() if "agent.tool_executor: tool deskbot_" in l})
    ok = all(e in called for e in expected)
    results[str(n)] = {"prompt": prompt[:90], "expected": expected, "called": called, "ok": ok}
    print(("PASS" if ok else "FAIL"), n, called, "|", reply[:70].replace("\n", " "), flush=True)
json.dump(results, open(OUT, "w"), indent=1)
print(sum(v["ok"] for v in results.values()), "/", len(results))
