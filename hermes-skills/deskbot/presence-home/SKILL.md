---
name: deskbot-presence-home
description: "Presence, proactive behavior, smart home, IR, USB/Bluetooth peripherals and hardware on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-camera-vision]
---

# Presence, proactive behavior and smart home

### 331 Arrival and departure events
Fuse signals in a cron every 30 s: front-camera presence (#117, only when allowed), phone-in-range (Bluetooth/Wi-Fi of the user's phone via `ping`/`arp` in Termux), sound level (planned). Declare arrival after 2 agreeing signals; debounce 2 min.

### 332 Proactive suggestions
Cron rules: meeting in 10 min (#217), battery low (#159), late-night (#227), weather change. Speak at most one suggestion per 30 min; respect quiet mode.

### 333 Context-aware quiet
Stay silent when: in a call (`dumpsys telecom | grep -i state`), DND/zen on (`settings get global zen_mode`), user asleep schedule, focus mode (#228). Queue messages as notifications instead.

### 334 Attention cues
App face looks at the user when spoken to (gaze set by state). Turn-toward-sound needs a direction estimate (not available with one mic); skip and say so.

### 335 Interruptible tasks
Long tasks run as Hermes background work; when the user speaks, pause (`stop` barge-in), answer, then offer "resume?" and continue from the last verified step.

### 336 Escalation
Important alerts repeat with rising prominence: notification → spoken (`deskbot_tts_say`) → louder (`deskbot_stream_volume stream=music level=…`) + vibrate pattern; stop on acknowledgment ("got it") or after 3 rounds.

### 337 Ambient awareness
Use light (`deskbot_sensor_read light`), time of day, and activity to adapt brightness/volume/verbosity (dark room → dim screen, whisper-level volume).

### 338 Routine learning
Review the audit log weekly for repeated sequences (same tools at similar times); propose an automation ("you turn on the lamp at 7 every day, want me to?") and only create it after a yes.

### 339 Away summary
On return: summarize the notification digest (`deskbot_notification_history digest=true since_minutes=<away>`), missed calendar events, timers, and tasks completed, in ≤ 5 sentences.

### 340 Visitor handling
Experimental: on a doorbell notification (`deskbot_notifications_list app=<doorbell app>`) or sound event, capture a frame if the camera faces the door, announce "someone is at the door", ask the user, and log the event with time.

### 341 Devices outside Home Assistant
MQTT in Termux: `mosquitto_pub -h <broker> -t <topic> -m <msg>` (`pkg install mosquitto`); vendor cloud via `deskbot_http_request`; local HTTP plugs per #176. Keep device list/credentials references in memory (not the secrets).

### 344 IR blaster control
Check hardware: `pm list features | grep -i infrared` / `dumpsys consumer_ir`. If present, `am` intents for IR apps or the ConsumerIrManager through a helper app. This phone: verify first; if absent say "no IR hardware".

### 345 USB OTG devices
`lsusb` (Termux `pkg install usbutils`), `ls /dev/bus/usb`, storage appears under `/storage`; serial/relay via `pyserial` with `termux-usb` permission (needs Termux:API).

### 346 Bluetooth peripherals
Pair/connect per #3; BLE sensors: Termux Python `bleak` unsupported on Android; use a BLE scanner app via UI or the future app tool. Speaker/earbuds routing per #129.

### 347 Serial and GPIO through adapters
Arduino/ESP32 over USB-OTG: `pyserial` + `termux-usb -r -e <script> <device>`; commands as simple line protocols; verify with echo responses. Hardware dependent; confirm wiring with the user.

### 348 Desk hardware
Servo stand/LEDs/buttons: via #347 or Wi-Fi ESP32 endpoints through `deskbot_http_request`. Design needed; record device endpoints in memory when added.

### 349 Printer control
Print: `am start -a android.intent.action.SEND -t application/pdf --eu android.intent.extra.STREAM file:///sdcard/x.pdf -p com.android.printspooler` or share to the print service app and tap Print (`deskbot_ui_click`); queue via `dumpsys print`; network printers by IPP in Termux `lp`/`cups`.

### 350 TV and display control
HDMI-CEC/network: smart TVs with HTTP APIs via `deskbot_http_request`; Chromecast: cast panel (#10); Android TV: `adb connect <tv-ip>:5555` then `input keyevent` for power/volume/inputs (see #351).
