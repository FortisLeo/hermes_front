---
name: deskbot-storage-network
description: "Files, archives, cleanup, sync, documents, network tools, HTTP, WoL, SSH, downloads, router on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-system-monitor]
---

# Storage, files and network tools

File tools (`deskbot_file_*`) work inside `/sdcard` and the app dirs. Termux-side work (tar, rsync, ssh, python) runs in tmux via `deskbot_shell cmd="su u0_aXXX -c ..."` or directly in the Hermes terminal tool.

### 163 Organize files
`deskbot_file_search`, `deskbot_file_move`, `deskbot_file_delete`; bulk rename/dedupe in Termux (`fdupes`, `rename`); auto-sort downloads with a cron job moving by extension into folders.

### 164 Archive and extract
`tar -czf a.tgz dir`, `zip -r a.zip dir`, `unzip`, `7z x` (pkg install p7zip); encrypted: `zip -e` / `7z a -p`. Never store passwords in notes.

### 165 Storage cleanup
Large files: `du -ah /sdcard | sort -rh | head -30`; caches `pm trim-caches 4G`; old logs `find ... -mtime +14 -delete` (confirm before deleting user data); thumbnails `/sdcard/DCIM/.thumbnails`.

### 166 External and network storage
USB OTG: `ls /storage`; mount via vold automatically; SMB/NFS in Termux (`pkg install samba`/`rclone` or `mount.cifs` unavailable → use `smbclient`/rclone). Verify with `df -h`.

### 167 Sync folders
`rsync -avz /sdcard/Documents/ user@host:/path/` or `rclone sync /sdcard/X remote:X` (configure `rclone config` once). Schedule with cron; log results.

### 168 Parse documents
`pdftotext` (poppler), `pandoc` (docx/epub → text), `python -c "import openpyxl"` for xlsx, `csvkit` for CSV in Termux; summarize with the model.

### 169 Share files out
Local HTTP server: `python -m http.server 8080 --directory /sdcard/share` (LAN only; stop when done); link `http://<ip>:8080` where ip from `deskbot_network_info`. Send to another device with `adb push`/`scp`.

### 170 Disk health and integrity
`df -h /data /sdcard`; `sha256sum` verify; free-space alert when < 10%; `e2fsck` is unsafe on mounted partitions (don't).

### 171 Connectivity check
`deskbot_network_info`; `ping -c 3 1.1.1.1`; DNS `nslookup example.com` (Termux dnsutils); `deskbot_http_request url=http://connectivitycheck.gstatic.com/generate_204` (204 = online, other = captive portal); speed test `curl -o /dev/null -w "%{speed_download}" <big-file-url>`.

### 172 Scan the local network
Termux: `nmap -sn 192.168.0.0/24` (pkg install nmap) for devices, `nmap -p- <ip>` for ports (only on the user's own LAN), mDNS `avahi-browse -a` or `dns-sd` equivalents. Report new devices vs a stored baseline.

### 173 HTTP requests and APIs
`deskbot_http_request url method headers body retries`. Auth via `headers={"Authorization":"Bearer …"}` (keys from memory, never echoed). Webhooks: POST JSON with `Content-Type: application/json`.

### 174 Run a local server
`python -m http.server`, Flask/FastAPI in Termux (`pip install flask`), keep alive in a tmux window; bind to the LAN IP only when needed; document the port in memory.

### 175 Firewall rules
`iptables -I OUTPUT -d <ip> -j REJECT` / per-app `-m owner --uid-owner <uid>`; list `iptables -S`; remove with `-D`. Always record the exact undo command in the reply and memory.

### 176 Remote power
Wake-on-LAN: Termux `pkg install wakeonlan` then `wakeonlan <MAC>`; smart plug: `deskbot_http_request` to the plug's local API (Tasmota `http://<ip>/cm?cmnd=Power%20On`).

### 177 SSH and remote commands
Termux `ssh user@host "cmd"`; keys `ssh-keygen -t ed25519`, `ssh-copy-id`; tunnels `ssh -L 8080:localhost:80 host`. Never print private keys.

### 178 Download manager
`curl -L -C - -o file <url>` (resume), `aria2c -c -x4 -i list.txt` (queue); schedule via cron; verify `sha256sum file`.

### 179 Network monitoring
Per-app: `dumpsys netstats --uid <uid>`; new-device alerts: diff of #172 against baseline; outage log: cron pinging every minute appending to a log file.

### 180 Router interaction
Open the router page: `am start -a android.intent.action.VIEW -d http://192.168.0.1`; read status via `deskbot_ui_tree`/vision; reboot or DNS change through the UI with confirmation. If the router has an API, prefer `deskbot_http_request`. Credentials only from the user's vault.
