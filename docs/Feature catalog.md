# Feature catalog: what the bot can do with the phone

The catalog holds 384 features in 43 groups: 47 of the original 400 were removed because Hermes already does them with built-in tools (listed at the end), and 31 features were added for app self-improvement, A2UI generative UI, and the persona system. Numbers stay fixed, so a gap marks a removed feature. Variants that share one mechanism are folded into one feature, so Wi-Fi and mobile data are a single toggle, while enabling accessibility, changing developer options, and changing normal settings are separate features.

**Path** is the likely way to do it, not a decision: Sh = shell, su, or adb; Ac = accessibility; App = deskbot app APIs; Vi = screen or camera vision; He = Hermes, cloud model, or web; Mod = root module. A ? marks a feature that needs design thought first. To decide MCP tool, skill, or not yet thought about, add a Decision column to any table.

## 1. Connectivity

| # | Feature | Path |
| --- | --- | --- |
| 1 | **Toggle a radio**: Wi-Fi, mobile data, Bluetooth, NFC, airplane mode, location, hotspot | Sh |
| 2 | **Join or forget a Wi-Fi network**: saved networks, hidden SSID, captive-portal login | Sh, Ac |
| 3 | **Pair and manage Bluetooth devices**: connect, unpair, default audio device | Ac, Sh |
| 4 | **Configure hotspot and tethering**: SSID, password, band, USB and Bluetooth tethering | Sh, Ac |
| 5 | **Set Private DNS and proxy**: DNS provider, HTTP proxy, per-network settings | Sh |
| 6 | **Connect or disconnect a VPN**: always-on VPN, per-app VPN, switching WireGuard profiles | Ac, App |
| 7 | **Read and write NFC tags**: read, write, tag-triggered routines | App |
| 8 | **Nearby sharing**: Wi-Fi Direct, Quick Share, local file send, device discovery | Ac |
| 9 | **Metered networks and data limits**: mark metered, data saver, per-app limits | Sh |
| 10 | **Cast and mirror the screen**: cast to TV, Miracast, mirror to a PC, stop casting | Ac, Sh |

## 2. System settings

| # | Feature | Path |
| --- | --- | --- |
| 11 | **Read any system setting**: system, global, and secure tables, full dump | Sh |
| 12 | **Change a setting by key**: put a value, validate it, read it back | Sh |
| 13 | **Display look**: brightness, auto-brightness, font scale, display size, dark mode | Sh |
| 14 | **Sound and haptics**: ring and media volume, vibration, touch sounds, ringtone | Sh |
| 15 | **Language, region, time zone**: locale, 24-hour clock, auto time, keyboard layout | Sh |
| 16 | **Default apps**: launcher, browser, assistant, dialer, SMS, home | Sh, Ac |
| 17 | **Do Not Disturb and focus modes**: schedules, exceptions, bedtime mode | Sh |
| 18 | **Lock screen and security settings**: lock type, timeout, notifications on lock screen | Sh, Ac |
| 19 | **Back up and restore settings**: export all, diff against a baseline, restore | Sh |
| 20 | **Find a setting by name**: search Settings, deep-link to the right page | Sh, Ac |

## 3. Developer options and debugging

| # | Feature | Path |
| --- | --- | --- |
| 21 | **Enable developer options**: build-number taps or the secure flag | Sh, Ac |
| 22 | **USB and wireless debugging**: pairing code flow, port, reconnect after boot | Sh, Ac |
| 23 | **Animation scales**: window, transition, and animator scale; off for faster UI automation | Sh |
| 24 | **Stay-awake and touch overlays**: stay awake while charging, show taps, pointer location | Sh |
| 25 | **Rendering flags**: force dark, GPU rendering, overdraw debug | Sh |
| 26 | **Background process limits**: phantom process killer, background limit, do not keep activities | Sh |
| 27 | **Mock location app**: select provider, feed fake coordinates | Sh, App |
| 28 | **USB configuration default**: file transfer, PTP, RNDIS tethering | Sh |
| 29 | **Bug reports and traces**: bugreport, perfetto trace, systrace to file | Sh |
| 30 | **Developer option audit**: read all flags, compare to a known-good baseline, repair | Sh |

## 4. Accessibility service management

| # | Feature | Path |
| --- | --- | --- |
| 31 | **Enable or disable an accessibility service**: by component name, with enable flag and grant check | Sh |
| 32 | **Keep services alive**: detect a killed service, re-enable, re-bind after reboot | Sh |
| 33 | **Built-in accessibility features**: TalkBack, Select to Speak, switch access, live caption | Ac, Sh |
| 34 | **Color and contrast**: color correction, inversion, high-contrast text, grayscale | Sh |
| 35 | **Text and magnification**: font size, bold text, magnification gestures, cursor size | Sh |
| 36 | **Accessibility shortcut and button**: assign shortcut, floating button, volume-key shortcut | Sh, Ac |
| 37 | **Touch timing**: touch-and-hold delay, tap duration, ignore repeated touches | Sh |
| 38 | **Audio accessibility**: mono audio, balance, captions, sound notifications | Sh |
| 39 | **Accessibility event stream**: window, click, and text-change events as triggers | Ac |
| 40 | **Audit service permissions**: list services, what each can see, flag unknown ones | Sh |

## 5. Screen understanding

| # | Feature | Path |
| --- | --- | --- |
| 41 | **Understand the current screen**: one capture, many outputs: read, summarize, answer a question, translate, explain | Ac, Vi, He |
| 42 | **Structured screen dump**: element tree, bounds, clickable flags, resource ids | Ac |
| 43 | **OCR of screen text**: text in images and video frames, apps that hide their tree | Vi |
| 44 | **Locate an element**: by text, id, description, or visual description such as the blue button | Ac, Vi |
| 45 | **Detect screen state**: current app, activity, dialog open, loading, keyboard shown | Ac, Sh |
| 46 | **Wait for or watch a change**: wait for text, wait for idle, watch a region, alert | Ac, Vi |
| 47 | **Read charts and tables on screen**: table to CSV, describe a chart | Vi, He |
| 48 | **Foreground app detection**: package, activity, window title, split-screen | Sh |
| 49 | **Understand a screen recording**: summarize a clip, extract the steps shown | Vi, He ? |
| 50 | **Learn an app layout**: map screens and transitions into a reusable graph | Ac, He ? |

## 6. Overlay and on-screen display

| # | Feature | Path |
| --- | --- | --- |
| 51 | **Text overlay anywhere**: floating caption, subtitle, translation, note | App |
| 52 | **Floating widget**: mini controls, bot face bubble, quick-action ring | App |
| 53 | **Highlight an element**: box or arrow on a screen region to guide the user | App |
| 54 | **Full-screen display**: clock, photo frame, dashboard, face-only mode | App |
| 55 | **Picture-in-picture output**: camera preview, video, call, bot answer | App |
| 56 | **Toasts and heads-up messages**: brief status without a notification | App |
| 57 | **Click-through overlays**: overlay that passes touches through, with a passthrough toggle | App |
| 58 | **Screen dimmer and filters**: extra dim, blue-light tint, privacy overlay | App |
| 59 | **Lock-screen display**: answers on the lock screen, always-on display content | App, Sh |
| 60 | **Draw on screen**: live annotation, freehand pointer | App |

## 7. Touch and gesture injection

| # | Feature | Path |
| --- | --- | --- |
| 61 | **Tap**: by coordinate or element; single, double, long press | Sh, Ac |
| 62 | **Swipe and scroll**: directional swipe, scroll to find, fling, edge swipe | Sh, Ac |
| 63 | **Multi-touch gestures**: pinch, rotate, two-finger scroll | Ac |
| 64 | **Drag and drop**: move items, reorder lists, drag to share | Ac, Sh |
| 65 | **System navigation actions**: back, home, recents, notification shade, quick settings | Ac, Sh |
| 66 | **Hardware key events**: volume, power, camera, media, assistant keys | Sh |
| 67 | **Record and replay a gesture macro**: capture touches, parameterize, replay | Sh |
| 68 | **Coordinate calibration**: map screen size, rotation, and cutouts to coordinates | Sh, Vi |
| 69 | **Human-like input timing**: randomized delays, eased swipes, retries for flaky UIs | Ac |
| 70 | **Haptic confirmation**: vibration patterns as feedback | App, Sh |

## 8. Keyboard and text input

| # | Feature | Path |
| --- | --- | --- |
| 71 | **Type into the focused field**: direct set-text, key events, paste fallback | Ac, Sh |
| 72 | **Type non-Latin text and emoji**: through clipboard or an input method | Ac |
| 73 | **Edit text**: select, cut, copy, paste, undo, delete a word or line | Ac |
| 74 | **Switch or configure the keyboard**: pick input method, layouts, handwriting, voice typing | Sh, Ac |
| 75 | **Dictate into any field**: speak and insert into any app | App, Ac |
| 76 | **Autofill forms**: fill by field label from memory | Ac |
| 77 | **Navigate forms**: next field, dropdown, checkbox, date picker, submit | Ac |
| 78 | **Search inside an app**: open search, type, run, read results | Ac |
| 79 | **Enter secrets and one-time codes**: from a vault or from a notification | Ac |
| 80 | **Text snippets**: saved replies, signatures, code snippets | Ac |

## 9. App lifecycle

| # | Feature | Path |
| --- | --- | --- |
| 81 | **Launch an app**: by name, package, deep link, shortcut | Sh |
| 82 | **Stop, freeze, or restart an app**: force-stop, suspend, disable, cold restart | Sh |
| 83 | **Install an app**: APK, URL, F-Droid, split APKs, Play Store UI | Sh, Ac |
| 84 | **Update, downgrade, or pin a version**: check versions, install a specific one | Sh |
| 85 | **Uninstall or hide an app**: including system apps, per user | Sh |
| 86 | **Inventory installed apps**: version, install source, size, last used | Sh |
| 87 | **Switch between apps**: recents, split screen, freeform windows, bring to front | Sh, Ac |
| 88 | **App defaults and links**: open-by-default, link verification, intent handlers | Sh |
| 89 | **Detect an app crash or freeze**: watch logs, restart, report the cause | Sh |
| 90 | **Keep an app running**: battery exemption, auto-relaunch, foreground priority | Sh |

## 10. App data and permissions

| # | Feature | Path |
| --- | --- | --- |
| 91 | **Grant or revoke permissions**: runtime grants, bulk by app, audit | Sh |
| 92 | **Manage app ops**: overlay, usage access, write settings, exact alarms | Sh |
| 93 | **Clear cache or data**: per app, all apps, selective | Sh |
| 94 | **Read app private data**: SQLite databases, shared preferences, files | Sh |
| 95 | **Back up and restore app data**: APK plus data, per-app archives | Sh |
| 96 | **Export or import app settings**: config files between an app and Termux | Sh |
| 97 | **App storage report**: app, data, and cache size, ranked | Sh |
| 98 | **App notification settings**: channels per app, block, allow | Sh, Ac |
| 99 | **Per-app network access**: block internet, data usage, firewall rule per app | Sh |
| 100 | **Verify an app**: signature check, compare to a known-good hash | Sh |

## 11. Notifications

| # | Feature | Path |
| --- | --- | --- |
| 101 | **Read notifications**: title, text, app, time, actions | App |
| 102 | **Act on a notification**: tap, inline reply, snooze, mark read | App, Ac |
| 103 | **Dismiss notifications**: one, by app, all, by keyword | App |
| 104 | **Filter and prioritize**: summarize, rank by importance, silence noisy apps | App, He |
| 105 | **Speak notifications aloud**: new ones, per-app rules, quiet hours | App |
| 106 | **Post a notification**: reminder, alert, status, with action buttons | App |
| 107 | **Notification-triggered automations**: if a message from X contains Y, do Z | App, He |
| 108 | **Capture one-time codes**: extract the code from SMS or app notifications | App |
| 109 | **Notification history**: search past notifications, daily digest | App, He |
| 110 | **Channel settings and lights**: importance, sound, LED, lock-screen visibility | Sh |

## 12. Camera and vision

| # | Feature | Path |
| --- | --- | --- |
| 111 | **Capture a photo**: front or rear, resolution, flash, burst | App |
| 112 | **Describe what the camera sees**: object in hand, scene, people count without identity, colors | App, He |
| 113 | **Read text through the camera**: labels, documents, handwriting, signs | App, He |
| 114 | **Scan codes and documents**: QR, barcode, document scanner | App |
| 115 | **Recognize and count objects**: desk items, inventory, plant ID | App, He |
| 116 | **Visual change watcher**: alert when something appears, moves, or disappears | App, He |
| 117 | **Presence from the camera**: someone sits down or leaves; face present, no recognition | App |
| 118 | **Hand gesture commands**: wave, thumbs-up, hand signals | App ? |
| 119 | **Record video or time-lapse**: clips, time-lapse, motion-triggered recording | App |
| 120 | **Camera controls**: zoom, focus, exposure, switch lens, torch | App |

## 13. Audio and voice

| # | Feature | Path |
| --- | --- | --- |
| 121 | **Wake-word listening**: custom words, sensitivity, several words | App |
| 122 | **Speech to text**: streaming, language switch, noisy-room tuning | App |
| 123 | **Text to speech**: voices, speed, pitch, emotion, interruption | App |
| 124 | **Barge-in and end-of-speech**: stop speaking when interrupted, detect when you finish | App |
| 125 | **Sound event detection**: doorbell, alarm, glass break, clap | App, He ? |
| 126 | **Record audio**: voice notes, meeting recordings, timed clips | App |
| 128 | **Identify music playing**: song ID through a web lookup, no lyrics copied | He |
| 129 | **Audio routing**: switch to Bluetooth, speaker, or wired; mute the mic | Sh |
| 130 | **Earcons and announcements**: chimes, status sounds, spoken alerts | App |

## 14. Media playback and capture

| # | Feature | Path |
| --- | --- | --- |
| 131 | **Control playback**: play, pause, next, seek, queue, repeat | Sh, App |
| 132 | **Play specific content**: song, playlist, podcast, or video by voice request | Ac, App |
| 133 | **Per-stream volume**: media, alarm, ring, call, mute all | Sh |
| 134 | **Take screenshots**: full, region, scrolling, annotated | Sh |
| 135 | **Record the screen**: with or without audio, time-limited, auto-stop | Sh |
| 136 | **Handle media files**: rename, convert, trim, extract audio, thumbnails | Sh |
| 137 | **Gallery search**: find photos by description, date, or text in the image | He |
| 138 | **Wallpapers**: set from file, rotate daily, AI-generated, live wallpaper | Sh, App |
| 139 | **E-books and documents**: open, read aloud, turn pages, find a passage | Ac, He |
| 140 | **Podcasts and radio**: stream, subscribe, resume, sleep timer | Ac |

## 15. Screen, display, and power

| # | Feature | Path |
| --- | --- | --- |
| 141 | **Wake, sleep, and unlock the screen**: screen on or off, lock, PIN unlock | Sh |
| 142 | **Keep the screen awake**: wake lock, rules per app | Sh |
| 143 | **Rotation and orientation**: lock, force landscape, per-app | Sh |
| 144 | **Resolution and density**: display size, DPI, reset | Sh |
| 145 | **Refresh rate and HDR**: force 60 or 120 Hz, adaptive refresh | Sh |
| 146 | **Always-on display behavior**: content, schedule, burn-in shifts | Sh, App |
| 147 | **Night mode and color temperature**: schedule, intensity, grayscale at night | Sh |
| 148 | **Power menu actions**: reboot, reboot to recovery or bootloader, shutdown | Sh |
| 149 | **Scheduled power events**: nightly reboot, timed screen off, maintenance window | Sh |
| 150 | **Multi-window and display modes**: split screen, freeform, secondary display, desktop mode | Sh, Ac |

## 16. Battery and thermal

| # | Feature | Path |
| --- | --- | --- |
| 151 | **Battery status and history**: level, health, cycles, temperature, charge rate | Sh |
| 152 | **Charge limit control**: cap at 80 percent, charge schedule, pause charging | Mod |
| 153 | **Doze and standby control**: doze allowlist, standby bucket, battery saver schedule | Sh |
| 154 | **Power use by app**: wakelock offenders, drain ranking | Sh |
| 155 | **Thermal monitoring**: CPU and battery temperature, throttle alerts | Sh |
| 156 | **Throttle response**: lower brightness, pause tasks, cool-down mode | Sh, He |
| 157 | **Charger and dock detection**: plugged or unplugged events, wireless charging | Sh |
| 158 | **Abnormal battery warning**: odd temperature or voltage behavior, swelling risk | Sh ? |
| 159 | **Low-battery behavior**: reduce features, announce, switch to a power-save profile | He |
| 160 | **Energy report**: daily summary of charge cycles and time on battery | Sh, He |

## 17. Storage and files

| # | Feature | Path |
| --- | --- | --- |
| 163 | **Organize files**: move, copy, delete, bulk rename, dedupe, auto-sort downloads | Sh |
| 164 | **Archive and extract**: zip, tar, 7z, encrypted archives | Sh |
| 165 | **Storage cleanup**: cache, thumbnails, old logs, large-file report | Sh |
| 166 | **External and network storage**: USB OTG, SD card, SMB and NFS shares | Sh |
| 167 | **Sync folders**: rsync, rclone to cloud, two-way sync | Sh |
| 168 | **Parse documents**: PDF, DOCX, XLSX, CSV, EPUB to text | Sh, He |
| 169 | **Share files out**: local HTTP server, share link, send to another device | Sh |
| 170 | **Disk health and integrity**: filesystem checks, free-space alerts, checksum verification | Sh |

## 18. Network tools

| # | Feature | Path |
| --- | --- | --- |
| 171 | **Connectivity check**: ping, DNS, speed test, captive-portal detection | Sh |
| 172 | **Scan the local network**: devices, open ports, mDNS services | Sh |
| 173 | **HTTP requests and APIs**: GET, POST, auth, webhooks, retries | Sh |
| 174 | **Run a local server**: web UI, API, webhook receiver, file server | Sh |
| 175 | **Firewall rules**: block per app or address with iptables | Sh |
| 176 | **Remote power**: Wake-on-LAN, smart plug on or off | Sh |
| 177 | **SSH and remote commands**: run on another machine, tunnels, key management | Sh |
| 178 | **Download manager**: resume, schedule, queue, verify hash | Sh |
| 179 | **Network monitoring**: traffic per app, new-device alerts, outage log | Sh |
| 180 | **Router interaction**: read the status page, reboot the router, set DNS | Ac, Sh |

## 19. System info and monitoring

| # | Feature | Path |
| --- | --- | --- |
| 181 | **Device info report**: model, Android version, kernel, build, root status | Sh |
| 182 | **CPU, memory, and process monitor**: top processes, memory pressure, kill hogs | Sh |
| 183 | **Uptime and reboot reasons**: last boot cause, crash history | Sh |
| 184 | **Sensor inventory and reads**: accelerometer, gyro, light, proximity, barometer | App, Sh |
| 185 | **Hardware capability check**: cameras, codecs, GPU, NFC presence | Sh |
| 186 | **Health score**: one summary of battery, storage, temperature, network, errors | Sh, He |
| 187 | **Clock sync**: NTP, set time, detect drift | Sh |
| 188 | **Scheduled health checks**: hourly checks, thresholds, alerts | Sh |
| 189 | **Property inspection**: getprop, build and vendor properties | Sh |
| 190 | **Environment snapshot**: full system state saved to compare after a change | Sh |

## 20. Logs and diagnostics

| # | Feature | Path |
| --- | --- | --- |
| 191 | **Read logcat**: filter by tag, app, level, time | Sh |
| 192 | **Explain an error**: match log lines to causes and fixes | Sh, He |
| 193 | **Kernel messages**: dmesg, last kernel log, pstore after a crash | Sh |
| 194 | **Dump system services**: dumpsys for activity, window, battery, Wi-Fi | Sh |
| 195 | **Bot action audit log**: every tool call with arguments, result, and time | App, Sh |
| 196 | **Replay a failure**: re-run steps from the log with extra tracing | He |
| 197 | **Crash report bundle**: logs, state, and screenshot in one package | Sh |
| 198 | **Performance profiling**: frame stats, startup time, memory over time | Sh |
| 199 | **Log rotation and cleanup**: size caps, archive, compress | Sh |
| 200 | **Remote log viewing**: serve logs on the LAN, tail from another device | Sh |

## 21. Clipboard and sharing

| # | Feature | Path |
| --- | --- | --- |
| 201 | **Read and write the clipboard**: text, images, history | App |
| 202 | **Clipboard watcher**: act on a copied link, address, code, or phone number | App, He |
| 203 | **Share content to an app**: share sheet, direct intent, to a named contact | Sh, Ac |
| 204 | **Receive shared content**: send a page, image, or file to the bot from any app | App |
| 205 | **Quick capture to memory**: save a note, link, or screenshot with one phrase | App, He |
| 206 | **Cross-device clipboard**: sync with a PC or another phone | Sh |
| 207 | **Copy text out of images**: OCR, then clipboard | Vi |
| 208 | **Convert on paste**: strip formatting, convert to Markdown or JSON | He |
| 209 | **Drag content between apps**: text or files across split screen | Ac |
| 210 | **Clipboard hygiene**: auto-clear after a delay, redact sensitive text | App |

## 22. Contacts, calendar, and tasks

| # | Feature | Path |
| --- | --- | --- |
| 211 | **Manage contacts**: add, search, edit, merge, import and export VCF | Sh, App |
| 212 | **Read the calendar**: today, week, free time, conflicts | App |
| 213 | **Create and edit events**: reminders, recurrence, attendees | App |
| 214 | **Daily briefing**: calendar, weather, reminders, and news as one spoken summary | He |
| 216 | **Birthday and anniversary reminders**: from contacts or memory | He |
| 217 | **Meeting prep**: pull agenda, notes, and attendee context before an event | He |
| 218 | **Leave-by alerts**: estimate travel time from location and calendar | He |
| 219 | **Task lists**: add, check off, due dates, recurring tasks | App, He |
| 220 | **Schedule awareness**: local holidays, work hours, quiet mode by schedule | He |

## 23. Alarms, timers, and routines

| # | Feature | Path |
| --- | --- | --- |
| 221 | **Set an alarm**: one-time, repeating, labeled, ringtone | Sh, App |
| 222 | **Timers and stopwatch**: several named timers, cooking, pomodoro | App |
| 223 | **Reminders by place or condition**: geofence, on charger, nag until done; plain timed reminders use Hermes cron | App, He |
| 225 | **Event-triggered routines**: on charger, on Wi-Fi join, on app open, on notification | App |
| 226 | **Wake-up routine**: gradual brightness, news, weather, music | He |
| 227 | **Bedtime routine**: dim, Do Not Disturb, tomorrow summary, alarm check | He |
| 228 | **Focus session**: timer plus blocked apps and notifications | App, Sh |

## 24. Browser and web

| # | Feature | Path |
| --- | --- | --- |
| 233 | **Control the phone's browser app**: tabs, address bar, bookmarks, history, private mode | Ac |
| 234 | **Fill and submit web forms in the phone browser**: logins, search forms, checkout up to a stop point | Ac |
| 236 | **Download from the web**: direct links, files behind a click, verification | Sh, Ac |
| 239 | **Translate a web page**: in place, or read translated aloud | He, Ac |
| 240 | **Browser data and settings**: clear cache and cookies, permissions, default search | Ac, Sh |

## 25. Messaging and social

| # | Feature | Path |
| --- | --- | --- |
| 241 | **Send a message in an app**: WhatsApp, Telegram, Signal, Messenger through UI or intent | Ac, Sh |
| 242 | **Read and summarize chats**: unread, one thread, group catch-up | Ac, He |
| 244 | **Voice messages**: record and send, play and transcribe incoming | Ac, App |
| 245 | **Post to social apps**: text, image, scheduled post | Ac |
| 246 | **Start or join a call**: voice and video in chat apps, hang up, mute | Ac |
| 248 | **Media in chats**: send a photo, receive and describe an image | Ac, He |
| 249 | **Group and channel management**: mute, leave, pin, mark read | Ac |
| 250 | **Presence and status**: status message, last-seen privacy | Ac |

## 26. Email and documents

| # | Feature | Path |
| --- | --- | --- |
| 251 | **Triage email**: inbox summary, priority, unsubscribe candidates | Ac, He |
| 252 | **Draft and send email**: compose, attach, reply, schedule send | Ac, He |
| 253 | **Search mail**: by sender, subject, date, attachment | Ac |
| 254 | **Extract from mail**: receipts, tickets, tracking numbers, dates | He |
| 258 | **Fill PDF forms**: detect fields, fill from memory, save | Sh, He |
| 259 | **Scan paper to a document**: camera scan, crop, OCR, searchable PDF | App |

## 27. Maps, location, and sensors

| # | Feature | Path |
| --- | --- | --- |
| 261 | **Get location**: one-shot, continuous, geofence enter and exit | App |
| 262 | **Navigate**: open a route, read the next turn, ETA | Ac, Sh |
| 264 | **Geofence routines**: home, desk, and away modes on arrive or leave | App |
| 265 | **Room inference from Wi-Fi**: infer desk versus elsewhere from signal strength | Sh ? |
| 266 | **Motion events**: picked up, tilted, moved, knock on the desk | App |
| 267 | **Ambient light and sound level**: adapt brightness and volume, detect quiet hours | App |
| 269 | **Mock or fixed location**: test geofences, set a fixed position | Sh |
| 270 | **Sensor logging**: record to a file, plot, alert on anomalies | App, Sh |

## 28. Root and module management

| # | Feature | Path |
| --- | --- | --- |
| 271 | **Check root state**: Magisk or KernelSU version, su access, module list | Sh |
| 272 | **Install, enable, disable modules**: ksud or magisk command, manager UI fallback | Sh, Ac |
| 273 | **Module scouting**: research, vet, fork, and test open-source modules | He, Sh |
| 274 | **Module health tests**: smoke test per module, report | Sh |
| 275 | **Bootguard control**: boot counter, disable-all trigger, status | Mod |
| 276 | **Superuser grants**: allow or deny su for apps, policies | Sh, Ac |
| 277 | **SELinux inspection**: read mode, denials, suggest rules without disabling it | Sh |
| 278 | **System partition read**: build.prop, overlays, vendor files | Sh |
| 279 | **Systemless overrides**: hosts file, properties, fonts through a module overlay | Mod |
| 280 | **Boot image backup**: dump the boot image, store it off-phone, verify the hash | Sh |

## 29. Termux and development

| # | Feature | Path |
| --- | --- | --- |
| 282 | **Manage Termux packages**: pkg, pip, npm; version pins | Sh |
| 283 | **Manage tmux sessions**: create, attach, detect dead ones, restart | Sh |
| 286 | **Build small web tools**: local dashboards, forms, utilities served on the LAN | Sh, He |
| 287 | **Boot and OS-level jobs**: Termux:Boot scripts, Android job scheduler, run at boot | Sh |
| 289 | **Containers and chroot**: Linux userland through proot or chroot | Sh |
| 290 | **Debug the bot's own code**: run tests, read tracebacks, propose fixes | Sh, He |

## 30. Hermes self-management

| # | Feature | Path |
| --- | --- | --- |
| 291 | **Start, stop, restart Hermes in tmux**: gateway, API server, MCP server | Sh |
| 293 | **Track token and cost use**: per day, per task, alerts at a cap | He |
| 294 | **Update Hermes**: pull a release, migrate, roll back on failure | Sh |
| 300 | **Self-diagnosis**: detect failing tools and slow responses, explain and fix | He |

## 31. Memory and knowledge

| # | Feature | Path |
| --- | --- | --- |
| 303 | **Personal knowledge base**: add documents, notes, links; ask later | He |
| 304 | **Remember what was on screen**: recall an earlier article or page when asked | He, Vi ? |
| 305 | **Daily journal**: end-of-day summary of what happened and what was learned | He |
| 306 | **People and relationship notes**: who is who, preferences, last topic | He |
| 307 | **Object memory**: where things are, from camera sightings such as keys on the shelf | He, App ? |
| 308 | **Memory hygiene**: merge duplicates, expire stale facts, ask when facts conflict | He |
| 309 | **Export and import memory**: snapshot to a file, move to a new device | Sh |
| 310 | **Lists and purchase memory**: running lists, reorder reminders, price history | He |

## 32. Skill learning and registry

| # | Feature | Path |
| --- | --- | --- |
| 314 | **Method promotion**: replace a UI step with a command once one is found | He |
| 315 | **Per-app UI recipes**: selectors, screen graph, quirks, app version | Ac, He |
| 316 | **Regression test runner**: nightly run, report, auto-disable broken skills | He, Sh |
| 317 | **Capability registry**: status of each capability, success rate, last verified | He |
| 318 | **Skill versioning and rollback**: snapshots, diff, revert | Sh |
| 319 | **Learn from corrections**: "that was wrong" feedback updates skills | He |
| 320 | **Teach by demonstration**: you do a task while the bot watches, then it writes the skill | Ac, Vi, He ? |

## 33. Desk-bot persona and face

| # | Feature | Path |
| --- | --- | --- |
| 321 | **Face and expressions**: eyes, mouth, moods, blink, look-at | App |
| 322 | **Status display**: listening, thinking, working, error, low battery | App |
| 323 | **Idle behaviors**: look around, yawn, clock, screensaver | App |
| 324 | **Voice and personality profiles**: names, styles, languages, formality | App, He |
| 325 | **Greeting and farewell**: on arrival, time of day, return after an absence | App, He |
| 326 | **Emotional response**: react to tone, thanks, errors, praise | App, He |
| 327 | **Sleep and wake cycles**: night mode, wake-up behavior, quiet face | App |
| 328 | **Touch interaction with the bot**: tap the face, long press for a menu, swipe for settings | App |
| 329 | **Quick actions panel**: buttons for common commands on the bot screen | App |
| 330 | **Debug screen**: logs, connection status, last tool calls | App |

## 34. Presence and proactive behavior

| # | Feature | Path |
| --- | --- | --- |
| 331 | **Arrival and departure events**: fuse camera, sound, Wi-Fi, and phone-in-range signals | App, Sh |
| 332 | **Proactive suggestions**: meeting in 10 minutes, battery low, it is late | He |
| 333 | **Context-aware quiet**: hold speech during calls, sleep, and focus mode | He |
| 334 | **Attention cues**: look at the user when spoken to, turn toward a sound | App |
| 335 | **Interruptible tasks**: pause a long task when the user speaks, resume afterward | App, He |
| 336 | **Escalation**: louder or repeated alerts for important things | App, He |
| 337 | **Ambient awareness**: room brightness, noise, and activity shape behavior | App |
| 338 | **Routine learning**: notice repeated actions and offer to automate them | He ? |
| 339 | **Away summary**: what happened while the user was away | He |
| 340 | **Visitor handling**: announce someone at the door, ask who, log it | App, He ? |

## 35. Smart home and peripherals

| # | Feature | Path |
| --- | --- | --- |
| 341 | **Devices outside Home Assistant**: MQTT, vendor cloud APIs, local HTTP plugs | Sh |
| 344 | **IR blaster control**: TV, AC, and fan remotes if the hardware exists | App ? |
| 345 | **USB OTG devices**: keyboard, mouse, flash drive, serial, relay boards | Sh |
| 346 | **Bluetooth peripherals**: speaker, earbuds, keyboard, BLE sensors | Sh, App |
| 347 | **Serial and GPIO through adapters**: Arduino or ESP32 commands, servos | Sh ? |
| 348 | **Desk hardware**: servo-driven stand, LEDs, buttons, battery dock | Sh ? |
| 349 | **Printer control**: print a file, check the queue, scan | Ac, Sh |
| 350 | **TV and display control**: power, input, volume over HDMI-CEC or network | Sh |

## 36. Multi-device ADB

| # | Feature | Path |
| --- | --- | --- |
| 351 | **Connect to other Android devices**: adb over Wi-Fi, pairing, saved devices | Sh |
| 352 | **Run commands on remote devices**: the same tools on other phones and tablets | Sh |
| 353 | **Mirror or control another screen**: scrcpy-style view, tap through | Sh |
| 354 | **Move files across devices**: between phones, tablets, and a PC | Sh |
| 355 | **Fleet status**: battery, version, and online state for all devices | Sh |
| 356 | **Install an app on many devices**: roll out the same APK | Sh |
| 357 | **Sync settings across devices**: apply one configuration to several devices | Sh |
| 358 | **Family device assist**: time limits and app blocks, with consent | Sh, Ac ? |
| 359 | **Cross-device test runs**: run an app test script on several devices | Sh |
| 360 | **PC bridge**: hand heavy tasks to a PC and return results | Sh, He |

## 37. Games and entertainment

| # | Feature | Path |
| --- | --- | --- |
| 361 | **Play a turn-based game**: chess, quiz, word games by reading the screen | Vi, Ac |
| 363 | **Game automation**: repetitive tasks such as daily rewards, with limits | Ac, Vi |
| 364 | **Board-state help**: describe the position and suggest moves | Vi, He |
| 365 | **Mood music**: choose music from mood, time, and weather | He, Ac |
| 369 | **Watch-party helper**: pause on request, find a scene, subtitles | Ac |
| 370 | **Identify what is shown**: movie, song, or object on the screen through a web lookup | Vi, He |

## 38. Productivity and life admin

| # | Feature | Path |
| --- | --- | --- |
| 371 | **Shopping lists and ordering**: build the list, open the store app, add items, stop before paying | Ac, He |
| 373 | **Expense logging**: from receipts, notifications, voice | Vi, He |
| 374 | **Bill and subscription reminders**: detect from email, calendar nudges | He |
| 380 | **Health reminders**: water, stretch, posture, eye breaks | He |

## 39. Backup, recovery, and safety

| # | Feature | Path |
| --- | --- | --- |
| 381 | **Back up Hermes state**: snapshot \~/.hermes, schedule, off-device copy | Sh |
| 382 | **Back up phone data**: selected apps, files, and settings to a PC or cloud | Sh |
| 383 | **Restore from backup**: Hermes, settings, apps, with verification | Sh |
| 384 | **Watchdog and auto-recovery**: restart dead services, escalate, log | Sh |
| 385 | **Kill switch**: voice phrase and button combo that halt everything | App |
| 386 | **Destructive-command guard**: deny-list and dry-run mode for risky commands | Sh |
| 387 | **Spending guard**: token and API budget caps, circuit breaker | He |
| 388 | **Roll back a change**: undo the last setting, module, or skill change | Sh, He |
| 389 | **Safe mode**: boot into a minimal feature set after repeated crashes | Sh, App ? |
| 390 | **Incident report**: after a failure, what happened and how it was fixed | He |

## 40. Customization

| # | Feature | Path |
| --- | --- | --- |
| 391 | **Launcher and home screen**: layout, icons, widgets, folders | Ac |
| 392 | **Themes and icon packs**: dark and light schedule, accent color, fonts | Sh, Ac |
| 393 | **Wallpaper automation**: by time, by weather, generated art | Sh |
| 394 | **Sound packs and ringtones**: set, rotate, per contact or app | Sh |
| 395 | **Navigation style**: three-button or gesture navigation | Sh |
| 396 | **Quick Settings tiles**: reorder, add a custom tile for bot actions | Ac, App |
| 397 | **Shortcuts and widgets**: app shortcuts, home widgets showing bot data | App |
| 398 | **Font and display tuning**: install a font module, size, weight | Mod, Sh |
| 399 | **Boot animation and splash**: custom boot animation through a module | Mod |
| 400 | **Status bar and system UI tuning**: icons, clock format, battery percentage | Sh |

## 41. App self-improvement

| # | Feature | Path |
| --- | --- | --- |
| 401 | **Edit the app's own source**: Kotlin, layouts, manifest, web UI assets in the local git repo | He, Sh |
| 402 | **Build the APK**: Gradle in Termux, or a remote build on a PC over SSH | Sh ? |
| 403 | **Install and restart itself**: `pm install -r -g`, re-run grants, relaunch the service | Sh |
| 404 | **Post-update health check**: reconnect to Hermes, mic, camera, and speech self-tests | Sh, App |
| 405 | **Roll back a bad build**: reinstall the last-good APK, revert the commit | Sh |
| 406 | **Hot-loaded features**: change the web UI or load a script plugin without rebuilding | App ? |
| 407 | **Feature request loop**: notice a missing app ability, propose, build, test, ship | He |
| 408 | **App changelog**: git history, version tags, spoken "what's new" | Sh, He |
| 409 | **UI self-tuning by voice**: change face, layout, colors, animations on request | App, He |
| 410 | **Crash-driven fixes**: read app crash logs, patch the cause, rebuild | Sh, He |

## 42. Generative UI (A2UI)

| # | Feature | Path |
| --- | --- | --- |
| 411 | **Render agent-composed screens**: cards, lists, forms, and status views from the catalog | App, He |
| 412 | **Two-way bound controls**: sliders, toggles, pickers that update locally and send actions | App |
| 413 | **Live device control panels**: toggles and gauges bound to phone state | App, Sh |
| 414 | **Overlay surfaces over any app**: A2UI cards drawn above other apps | App |
| 415 | **Saved screen templates**: briefing, status, and timer screens reused as macros | He, App |
| 416 | **UI actions to tools or Hermes**: a tap calls an MCP tool or sends a structured turn | App, He |
| 417 | **Sandboxed web views in a surface**: MCP Apps for maps and bespoke visuals | App ? |
| 418 | **Grow the catalog**: add a native component, rebuild, regenerate the A2UI skill | Sh, He |

## 43. Persona system

| # | Feature | Path |
| --- | --- | --- |
| 419 | **Default eyes face**: parameter-driven eyes with blink, gaze, and moods; no image files | App |
| 420 | **Tweak the face by voice**: change eye shape, color, size, blink rate as a parameter edit | App, He |
| 421 | **Switch persona packs**: face, theme, voice, and personality change together; switch back | App |
| 422 | **Live2D characters**: 2D anime-style models with expressions and lip sync | App |
| 423 | **VRM 3D avatars**: standard expressions, mouth shapes, eyes that follow you | App |
| 424 | **Lip sync and gaze**: mouth driven by speech loudness, eyes toward the user or a sound | App |
| 425 | **Theme from a persona**: palette and fonts applied to the app shell and all A2UI screens | App, He |
| 426 | **Create a persona on request**: find licensed models, generate sprites or a mesh, or import your file | He, App ? |
| 427 | **Persona gallery**: browse, preview, switch, and delete packs on an A2UI screen | App |
| 428 | **Persona license tracking**: record each asset's source and allowed use; refuse unlicensed rips | He |
| 429 | Standard expression library: moods, bot states, and reactions such as cry, think, happy, sad, shared by every pack | App |
| 430 | Effects layer: tears, blush, hearts, sparkles, Zzz, question marks drawn over any avatar | App |
| 431 | Expressions timed with speech: Hermes tags moods and reactions in its reply, played in sync | He, App |

## Removed: already built into Hermes

These 47 features are covered by Hermes's own tools, so no MCP tool or skill is needed for them.

| Removed # | What Hermes uses instead |
| --- | --- |
| 127 | Speech-to-text providers |
| 161, 162 | `search_files`, `read_file`, `write_file`, `patch` |
| 215, 243, 362, 366, 367, 368, 375, 378, 379 | The model itself (small talk, stories, drafting, writing, study, recipes, time zones) |
| 224, 229, 230 | `cronjob` plus `memory` |
| 231, 263, 268 | `web_search` |
| 232 | `web_extract` |
| 235 | `cronjob` plus `web_extract` |
| 237, 238 | Browser toolset (headless Chromium) and `web_extract` |
| 247 | Messaging gateway (Telegram, Discord, Slack, and others) |
| 255, 256 | `write_file`, `patch` |
| 257, 260 | `execute_code`, `search_files` |
| 281 | `terminal`, `process` |
| 284, 285, 288 | `execute_code`, `patch`, `terminal` |
| 292 | `hermes model` and `/model` |
| 295 | Profiles and `SOUL.md` persona |
| 296 | `/new`, `/reset`, `session_search` |
| 297 | `hermes config set` |
| 298 | `mcp_servers` in `config.yaml` |
| 299 | `hermes plugins install` |
| 301 | `memory` |
| 302 | `session_search` |
| 311, 312, 313 | `skill_manage`, `skill_view`, `skills_list`, and the learning loop |
| 342, 343 | Home Assistant tools (`ha_*`) |
| 372, 376, 377 | `web_search`, `web_extract`, `delegate_task` |

Kept on purpose despite overlap: 122 and 123 (Hermes has speech tools, but the app runs its own for low latency) and 112 and 113 (Hermes's `vision_analyze` does the analysis, but camera capture is app-side). Built-in tool names come from the [Hermes tools reference](https://hermes-agent.nousresearch.com/docs/reference/tools-reference) and the [tools and toolsets guide](https://hermes-agent.nousresearch.com/docs/user-guide/features/tools); check them with `hermes tools` on your install.
