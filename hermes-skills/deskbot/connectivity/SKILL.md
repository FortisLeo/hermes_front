---
name: deskbot-connectivity
description: "Wi-Fi, Bluetooth, hotspot, DNS, VPN, NFC, casting on the Deskbot phone. Use when asked to toggle or configure any radio or network link."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-settings]
---

# Connectivity

Method ladder for every item: app tool or `deskbot_shell` (root) first, accessibility (`deskbot_ui_*`) only if the command is refused, screenshot+vision last. Always **verify** by reading state back (`deskbot_network_info`, `deskbot_settings_get`). Commands below vary by Android version (device is Android 16); if one fails, try the alternative listed, then record what worked in memory.

### 1 Toggle a radio
`svc wifi enable|disable`; `svc data enable|disable`; `cmd bluetooth_manager enable|disable`; `svc nfc enable|disable`; `cmd connectivity airplane-mode enable|disable`; `cmd location set-location-enabled true|false`; hotspot: `cmd wifi start-softap <ssid> wpa2 <pass>` / `cmd wifi stop-softap`. Verify with `deskbot_network_info` (wifi_enabled, bluetooth_enabled) or `settings get global airplane_mode_on`.

### 2 Join or forget a Wi-Fi network
Join: `cmd wifi connect-network "<ssid>" wpa2 "<pass>"` (open: `open`; hidden: add `-h`). Forget: `cmd wifi forget-network <netId>` (list with `cmd wifi list-networks`). Captive portal: open the portal in the browser (`am start -a android.intent.action.VIEW -d http://neverssl.com`), then drive with `deskbot_ui_tree`/`deskbot_ui_click`. Verify: `deskbot_network_info` shows the SSID and `online:true`.

### 3 Pair and manage Bluetooth devices
Open pairing UI: `am start -a android.settings.BLUETOOTH_SETTINGS`; use `deskbot_ui_click` on the device name then "Pair". Connected list: `dumpsys bluetooth_manager | grep -i -E "connected|name"`. Unpair: `deskbot_ui_click` gear icon > Forget. Default audio route: the output switcher in Sound settings via `deskbot_ui_click`. Verify: `dumpsys bluetooth_manager` shows the device state.

### 4 Configure hotspot and tethering
`cmd wifi start-softap <ssid> wpa2 <password>`; band via Settings > Hotspot UI (`am start -a android.settings.TETHER_SETTINGS`). USB tether: `svc usb setFunctions rndis`; Bluetooth tether via UI. Verify: `ip addr | grep -E "ap0|wlan1"` and `deskbot_network_info`.

### 5 Set Private DNS and proxy
Private DNS: `settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.google` (off: `private_dns_mode off`, automatic: `opportunistic`). Global proxy: `settings put global http_proxy host:port` (clear: `settings put global http_proxy :0`). Use `deskbot_settings_put` (tables `global`) which reads back. Verify with `deskbot_settings_get` then `deskbot_http_request` to a known URL.

### 6 Connect or disconnect a VPN
WireGuard/other VPN app: `deskbot_app_open`, then `deskbot_ui_click` the profile toggle; or intent `am start -n com.wireguard.android/.activity.TunnelToggleActivity --es tunnel <name>` (if the app exposes it). Always-on: `settings put secure always_on_vpn_app <pkg>`. Verify: `dumpsys connectivity | grep -i vpn` and `deskbot_http_request https://ifconfig.me`.

### 7 Read and write NFC tags
Needs NFC hardware: check `pm list features | grep nfc`. Reading/writing tags requires an app with NDEF handling; open it (`deskbot_app_open`) and drive with `deskbot_ui_*`. If no NFC feature, tell the user it is unsupported on this phone. Tag-triggered routines: register an intent filter app-side (planned).

### 8 Nearby sharing
Quick Share/Nearby: `am start -a android.settings.NEARBY_SHARE_SETTINGS`; send a file with `am start -a android.intent.action.SEND -t <mime> --eu android.intent.extra.STREAM <uri>` then `deskbot_ui_click` the target device. Wi-Fi Direct needs UI. Verify by the receiver's confirmation (screenshot via `deskbot_screenshot` + vision).

### 9 Metered networks and data limits
Mark metered: `cmd netpolicy set metered-network wifi "<ssid>" true` (fallback: Wi-Fi settings UI). Data saver: `cmd netpolicy set restrict-background true|false`. Per-app: `cmd netpolicy add restrict-background-whitelist <uid>`. Verify: `cmd netpolicy list restrict-background-whitelist` and `deskbot_network_info` (metered).

### 10 Cast and mirror the screen
Open the cast panel: `deskbot_ui_global quick_settings` then `deskbot_ui_click text="Cast"` (label varies: "Screen cast", "Smart View"). Pick the device by text. Stop: reopen the panel and tap "Stop". Verify with `deskbot_screenshot` showing the casting indicator.
