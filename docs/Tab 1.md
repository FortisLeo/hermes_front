# Deskbot Project Plan: Hermes Agent on a Rooted Phone

Draft v1, November 22, 2026

## 1. Goal

Build a desk robot assistant on a dedicated, rooted Android phone. Hermes Agent is the brain and backend. A custom Android app is the body: wake word, voice, camera, face, and phone control. The bot should hear you, see what you hold up, act on the phone and on connected systems, and get better over time by learning skills.

**Example interactions**

- "Hey bot, what's in my hand?" -> camera capture -> vision model -> spoken answer.
- "Turn on hotspot and open YouTube." -> root/accessibility actions.
- "Remind me at 6 to drink water." -> Hermes cron -> bot speaks at 6.
- "Learn how to check my router's status page." -> Hermes works it out once, saves it as a skill, reuses it.

## 2. Decisions already made

- **Model:** cloud LLM with vision. API key is already configured in Hermes.
- **Device:** dedicated phone, rooted, no SIM, no personal data, Wi-Fi only. Security hardening is intentionally minimal.
- **Runtime:** Hermes runs in Termux inside tmux.
- **Roles:** Hermes = brain/backend. Android app = frontend and sensors. Phone-control MCP server = hands.
- **Interface:** Hermes gateway with a custom platform plugin for the app. The OpenAI-compatible API server is used first for prototyping.

## 3. Architecture

```
Android app (Kotlin)
  wake word, VAD, STT, TTS, face UI,
  camera, AccessibilityService, foreground service
        |  WebSocket on localhost
        v
Deskbot platform plugin (inside Hermes gateway)
        |
Hermes agent loop: memory, skills, cron, cloud LLM
        |  MCP (stdio)
        v
Phone-control MCP server
  su shell, adb (localhost), Termux:API, app bridge
```

**Principles**

1. The app owns the mic, camera, and accessibility. Termux never fights it for hardware.
2. Hermes owns reasoning, memory, scheduling, and skills.
3. The MCP server is the single door for phone control, so every action is logged in one place.
4. Keep the app-to-Hermes message format simple: text in, streamed text out, image attachments, a few control events.

## 4. Components

### 4.1 Hermes side

- Enable the API server (`API_SERVER_ENABLED=true` in `~/.hermes/.env`, port 8642, bind to 127.0.0.1) for early testing.
- Run the gateway as the long-lived process. Add the deskbot platform plugin in `~/.hermes/plugins/`.
- Register the phone-control MCP server under `mcp_servers` in `~/.hermes/config.yaml`.
- Use one persistent session for the bot so memory carries across wake-ups. Reset on voice command ("forget this conversation").
- Write a system prompt/persona file: name, voice style, short spoken answers, how to describe what the camera sees, when to ask before acting.

### 4.2 Android app

- **Foreground service** that stays alive and holds the audio pipeline.
- **Wake word:** openWakeWord or Porcupine, always on, low CPU.
- **VAD + STT:** voice activity detection, then streaming STT (cloud Whisper-class or similar). Send partial transcripts for early "thinking" feedback.
- **TTS:** Piper (local, fast) or a cloud voice. Start speaking as soon as the first sentence streams in.
- **Face/UI:** animated eyes and states: idle, listening, thinking, speaking, error. Large clock/status when idle.
- **Camera module:** capture a frame on request, downscale (about 1024 px longest side), JPEG-encode, attach to the message.
- **AccessibilityService:** expose screen tree, tap, swipe, type, global actions, app launch, notification reading.
- **Local bridge server:** WebSocket/HTTP on localhost so the MCP server can call app-only functions (camera, accessibility).
- **Settings screen:** wake word sensitivity, voice, model endpoint, kill-switch phrase, debug log view.

### 4.3 Phone-control MCP server

Python or Node, run from Termux. Start with about 25 tools plus the raw escape hatches. Do not hand-write 300 tools; capabilities beyond this set come from Hermes skills.

| Domain | Tools |
| --- | --- |
| Escape hatches | `shell(cmd, root)`, `adb(cmd)` |
| UI control | `ui_tree()`, `ui_tap(target)`, `ui_swipe()`, `ui_type(text)`, `ui_back()`, `ui_home()`, `ui_wait_for(text)` |
| Apps | `app_open(name)`, `app_close(name)`, `app_list()`, `app_install(apk)` |
| Sensing | `camera_capture()`, `screenshot()`, `mic_record(seconds)`, `sensor_read(name)`, `battery()`, `location()` |
| Notifications | `notifications_list()`, `notification_act()` |
| System | `setting_get/set()`, `radio_toggle(wifi/bt/hotspot/airplane)`, `volume()`, `brightness()`, `screen_on/off()` |
| Files | `file_read()`, `file_write()`, `file_search()` |
| Media | `media_play/pause()`, `tts_say(text)`, `sound_play()` |
| Network/IoT | `http_request()`, `lan_scan()`, optional MQTT/Home Assistant |

**Implementation notes**

- Prefer Termux:API for SMS, sensors, torch, and clipboard before reaching for root.
- Use `su -c` for `input`, `screencap`, `am`, `pm`, `cmd`, `settings`, `svc`, `dumpsys`, `logcat`.
- Use `uiautomator dump` as a root fallback when the accessibility tree is unavailable.
- Every tool returns a short, structured result plus an error field. Log every call (tool, args, result, time) to a file.
- Deny-list in the shell tool only: `dd`, `mkfs`, `fastboot`, bootloader or partition writes, `rm -rf /`, writes to system block devices.

### 4.4 Memory and knowledge

- Start with Hermes's built-in persistent memory and session search.
- Add RAG only when needed (manuals, notes, long documents): a local SQLite store with embeddings, exposed as one MCP tool `knowledge_search(query)`.
- Store per-app UI recipes as skills, not in RAG.

## 5. Voice and vision pipeline

**Voice flow**

1. Wake word fires; face goes to listening; short chime.
2. VAD records until silence; STT streams partial text.
3. App sends the final transcript (plus image if needed) over the WebSocket.
4. Hermes streams tokens back; the app speaks sentence by sentence.
5. Tool calls show as a "working" animation, with an optional short spoken status ("checking the camera").

**Vision flow**

- Trigger by intent: Hermes calls `camera_capture()` when the question needs sight, or the app attaches a frame when it hears phrases like "what is this" or "look at".
- Use the vision-capable cloud model; keep frames small to save latency and cost.

**Latency targets**

| Step | Target |
| --- | --- |
| Wake detection | under 300 ms |
| End of speech to final transcript | under 400 ms |
| First LLM token | under 1.2 s |
| First TTS audio | under 2.5 s from end of speech |

If a tool run will take long, play a filler sound or short phrase so the bot never feels frozen.

## 6. Reliability on Android

Android will kill background processes. Plan for this from day one.

- **Termux:Boot** starts tmux and Hermes after reboot; the app also auto-starts at boot.
- `termux-wake-lock` running; battery optimization disabled for Termux, Termux:API, Termux:Boot, and the app.
- **Phantom process killer (Android 12 and newer):** disable it with root or ADB, for example `settings put global settings_enable_monitor_phantom_procs false` and the `device_config` max\_phantom\_processes override. Exact commands depend on the Android version; confirm on the device.
- **Watchdog:** a small script that checks the gateway and MCP server every 30 s and restarts them; the app shows an error face if Hermes is unreachable and retries with backoff.
- **Charging health:** a phone left plugged in 24/7 risks battery swelling. Use a charge limiter (for example a Magisk charging-control module) to hold around 60 to 80 percent.
- **Heat:** avoid cases that trap heat; consider a small fan or open stand.
- **Time and Wi-Fi:** keep Wi-Fi sleep off and a static DHCP lease for the phone.

## 7. Guardrails (lean, because the device is dedicated)

- Deny-list of destructive shell patterns (section 4.3).
- **Backups:** a full device backup or stock image kept off-phone, plus scheduled snapshots of `~/.hermes/` (memory, skills, config).
- **Kill switch:** a voice phrase and a hardware button combo that stop the agent and restart the gateway cleanly.
- **API key hygiene:** set a spending cap with the model provider and a per-session tool-call limit to stop runaway loops.
- **Network:** bind the API server, WebSocket, and MCP bridge to localhost, or require a token if exposed to the LAN.
- No personal accounts on the device unless the bot truly needs one.

## 8. Self-improvement loop

- Let Hermes write a skill after any multi-step success and refine it after a failure.
- Add feedback phrases ("that was wrong", "good job") that update the relevant skill.
- Maintain a **regression set** of about 10 scripted tasks (toggle Wi-Fi, take photo, open settings, read notification, tap a known button). Run it nightly and after changes to skills or the MCP server.
- Keep skill and memory snapshots so a bad self-edit can be rolled back in one command.
- Review the skill library weekly: remove duplicates, rename unclear skills.

## 9. What the bot can do

The built-in tools plus the raw shell and ADB tools cover the 18 categories below; anything not listed is learned by Hermes and saved as a skill.

| # | Category | What it can do | How it does it |
| --- | --- | --- | --- |
| 1 | Voice and conversation | Wake on a keyword; listen and answer by voice; hold a running conversation; change voice, language, or speaking speed; stop mid-sentence on "stop" | App wake word, STT, TTS; Hermes session |
| 2 | Vision | Describe what is in your hand or in front of it; read labels, documents, and handwriting; identify objects, plants, and products; count items; compare two things; watch for a change and report it | Camera tool plus vision model |
| 3 | Screen and UI control | Read the screen; tap, swipe, long-press, scroll, type; go back or home; switch apps; find and click by text; wait for an element; fill forms; dismiss dialogs | AccessibilityService; root `input` as fallback |
| 4 | Apps | Open, close, force-stop, freeze, or clear data; list apps; install and uninstall APKs; open deep links; share files between apps | `am`, `pm`, root shell |
| 5 | Media | Play, pause, skip; control volume; play sounds; record audio; take photos and video; take screenshots; record the screen | Media session; `screencap`, `screenrecord`; app camera |
| 6 | Notifications | Read, summarize, and dismiss notifications; read them aloud; speak new alerts; tap notification actions | NotificationListener; accessibility |
| 7 | Messaging (no SIM) | Send messages in installed chat apps; send email through an app or SMTP; post to Telegram; call webhooks | UI automation; Termux tools; HTTP |
| 8 | System settings | Toggle Wi-Fi, Bluetooth, hotspot, airplane mode, rotation, dark mode, battery saver; set brightness, volume, screen timeout, Do Not Disturb, language, time zone; reboot | `settings`, `svc`, `cmd`, root |
| 9 | Device status | Report battery level, temperature, and charging; storage; RAM and CPU; Wi-Fi details; uptime; running apps; logs; light, motion, and proximity sensors | `dumpsys`, `logcat`, Termux:API |
| 10 | Files and data | Read, write, search, move, copy, and zip files; parse PDF and CSV; download files; back up and restore the Hermes folder | Shell; Termux; Hermes file tools |
| 11 | Network and web | Search the web; fetch pages; call APIs; ping and scan the LAN; check the router; SSH into other machines; send Wake-on-LAN | Hermes web tools; shell |
| 12 | Home and desk devices | Publish to MQTT or Home Assistant; switch smart plugs; drive other Android devices over ADB; use USB OTG gadgets | HTTP, MQTT; `adb` |
| 13 | Time and routines | Set alarms, timers, and reminders; give a morning briefing; run scheduled checks; speak proactive alerts | Hermes cron; gateway delivery to the app |
| 14 | Memory and learning | Remember facts, preferences, people, and places; recall past conversations; create and refine skills; learn per-app UI recipes; say what it learned | Hermes memory and skills |
| 15 | Termux and dev work | Run scripts; use pip, npm, and git; edit code; build small tools; manage tmux sessions and its own services | Termux shell |
| 16 | Self-maintenance | Restart Hermes or the MCP server; read its own logs; run the regression tests; snapshot `~/.hermes/`; update itself; report errors by voice | Watchdog scripts; shell |
| 17 | Root-only powers | Freeze or disable system apps; edit the hosts file; change system properties; set `iptables` rules; remount partitions; read protected app data; control charge limits | `su`; Magisk modules (installed by hand) |
| 18 | Desk-bot behavior | Show face expressions and idle animations; greet you when it sees you; sleep at night; show a clock and status screen | App UI; camera presence check |

UI-automation abilities depend on how each app is laid out, so expect the bot to get better at them as it saves a recipe per app.

## 10. Permissions granted to the app

The app asks for every permission it needs in its manifest, and a one-time root setup script grants them all so no pop-ups appear. Commands below vary by Android version, so test each on the device.

### 10.1 Runtime permissions (granted with `pm grant`)

| Permission | Used for |
| --- | --- |
| `CAMERA` | Vision, presence detection, photos |
| `RECORD_AUDIO` | Wake word, speech-to-text, audio recording |
| `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | Location tool, Wi-Fi scanning |
| `POST_NOTIFICATIONS` | The foreground service notification |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` | Reading photos, video, and audio files |
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | Bluetooth control and device discovery |
| `NEARBY_WIFI_DEVICES` | Wi-Fi scans on Android 13 and newer |
| `ACTIVITY_RECOGNITION` | Motion and step sensors |
| `READ_CONTACTS`, `READ_CALENDAR`, `WRITE_CALENDAR` | Optional; only if you store contacts or events on the phone |

SMS and phone-call permissions stay off because the phone has no SIM.

### 10.2 Install-time permissions (declared in the manifest)

| Group | Permissions |
| --- | --- |
| Network | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` |
| Staying alive | `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` with camera, microphone, and special-use types, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Device control | `MODIFY_AUDIO_SETTINGS`, `VIBRATE`, `FLASHLIGHT`, `EXPAND_STATUS_BAR`, `KILL_BACKGROUND_PROCESSES`, `QUERY_ALL_PACKAGES` |
| Scheduling and display | `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT` |
| Installing apps | `REQUEST_INSTALL_PACKAGES` |

### 10.3 Special access (set outside the permission dialog)

| Access | Why the bot needs it | Root setup command |
| --- | --- | --- |
| Accessibility service | Read the UI tree; tap, swipe, type | `settings put secure enabled_accessibility_services <pkg>/<service>` then `settings put secure accessibility_enabled 1` |
| Notification listener | Read and act on notifications | `cmd notification allow_listener <pkg>/<service>` |
| Draw over other apps | Face and status overlay | `appops set <pkg> SYSTEM_ALERT_WINDOW allow` |
| Modify system settings | Brightness, timeout, rotation | `appops set <pkg> WRITE_SETTINGS allow` |
| All files access | Read and write any storage path | `appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` |
| Usage access | See which app is in the foreground | `appops set <pkg> GET_USAGE_STATS allow` |
| Battery optimization exemption | Keep the app and Termux running | `dumpsys deviceidle whitelist +<pkg>` |
| Do Not Disturb access | Toggle DND by voice | `cmd notification allow_dnd <pkg>` |
| Install unknown apps | Install APKs it downloads | `appops set <pkg> REQUEST_INSTALL_PACKAGES allow` |
| Write secure settings | Change secure settings without a shell | `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` |
| Read logs | Read `logcat` for self-diagnosis | `pm grant <pkg> android.permission.READ_LOGS` |
| Exact alarms | Timers and reminders on time | `appops set <pkg> SCHEDULE_EXACT_ALARM allow` |

Screen capture through MediaProjection cannot be pre-granted, so the bot uses root `screencap` and `screenrecord` instead.

### 10.4 Root, ADB, and companion apps

| Item | Setting |
| --- | --- |
| Root (Magisk or KernelSU) | Grant `su` permanently to the deskbot app and to Termux; turn off the grant prompt and timeout |
| Termux and Termux:API, Termux:Boot | Same runtime and special grants as the app; battery exemption on all three |
| ADB over localhost | Enable Wireless debugging, pair once, connect Termux to `127.0.0.1`; used by the `adb` tool for commands that do not need root |
| Shizuku (root mode) | Optional; gives app-level privileged API access |
| Device admin and wipe rights | Left off, so the agent cannot factory-reset the phone |

### 10.5 Setup script

Write one `grant_all.sh` that loops over the permission, `appops`, and settings commands above for each package, run it once after install and again after every app update, and check the result with `dumpsys package <pkg>` and `appops get <pkg>`.

### 10.6 Auto-grant everything with a module

A small KernelSU module of your own can make the deskbot app a privileged system app, so almost every permission is granted with no prompts and stays granted after updates.

| Permission kind | How it is auto-granted |
| --- | --- |
| Install-time (normal) | Granted automatically at install |
| Runtime (dangerous) | `pm install -r -g` grants every declared runtime permission at install; for the systemized app, a `default-permissions` XML grants them at boot |
| Signature and privileged, such as `INSTALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `WRITE_SECURE_SETTINGS`, `BATTERY_STATS` | The module places the APK in `/system/priv-app/` and lists each permission in a `privapp-permissions` XML |
| Special access: accessibility, notification listener, overlay, app ops | Not permissions, so the module's `service.sh` runs `grant_all.sh` on every boot, and Hermes re-runs it after each self-update |

```
deskbot-system/
  module.prop
  system/priv-app/Deskbot/Deskbot.apk
  system/etc/permissions/privapp-permissions-deskbot.xml
  system/etc/default-permissions/default-permissions-deskbot.xml
  service.sh        # waits for boot, then runs grant_all.sh
```

- **List every privileged permission exactly.** On recent Android versions, a priv-app that requests a privileged permission missing from its allowlist can stop the phone from booting. The bootguard module (11.4) is the safety net.
- **Updates:** after the module installs the base APK, self-updates use `pm install -r`. The update should keep privileged status if it is signed with the same key; verify this on your Android version.
- **Same key forever:** sign every build with one keystore, backed up off the phone.
- Older "App Systemizer" modules do this but are dated; writing your own is about 30 lines.
- Each manifest permission still has to be declared in the app; declare the full set from 10.1 to 10.3 up front so new features rarely need a manifest change.

## 11. Root modules and the module-scout skill

The bot starts with a short list of well-known modules and gets a skill that finds, vets, installs, and tests new ones on demand. Module names below come from memory, so the agent must check each against its live repository before installing.

### 11.1 Modules worth having

| Module | Purpose | When |
| --- | --- | --- |
| Own bootguard module | Disables agent-installed modules after repeated bad boots (see 11.4) | Build first, before any other module |
| ACC (Advanced Charging Controller) | Charge limiter for a phone that stays plugged in | Early |
| Busybox for Android NDK | Full set of shell tools for the MCP server | Early |
| MMRL CLI | Search, download, and install modules from repos, several at once | Optional; `ksud` already covers KernelSU |
| ReZygisk or Zygisk Next, plus LSPosed | Hook into apps when a capability needs it | Only on demand |

Skip kernel tuners, overclockers, "optimizer" mega-modules, and anything that writes to partitions: they are the likeliest to brick the phone. Root-hiding modules are unnecessary because the phone holds no banking apps.

### 11.2 Module-scout skill

The agent runs this when asked for a capability a module might provide ("find a module that does X").

1. **Research:** search the web and GitHub for repos with a `module.prop`, an open-source license, recent commits, and a matching Android version and CPU architecture.
2. **Vet:** clone the repo and read `customize.sh`, `service.sh`, `post-fs-data.sh`, and `sepolicy.rule`. Flag `dd`, block-device writes, `curl | sh`, and `setenforce`. Pin the exact commit. On this phone the check guards against bootloops, not data theft.
3. **Fork locally:** clone to `~/modules/src/<name>`, apply patches on a local branch, and zip the module with `module.prop` at the zip root.
4. **Install:** run `ksud module install <zip>`; manage it with `ksud module enable`, `disable`, `uninstall`, and `list`. On Magisk use `magisk --install-module <zip>` (verify on the device). If the command fails, find the KernelSU Next manager with `pm list packages | grep -i ksu`, open it with `am start`, and drive it through accessibility.
5. **Verify:** reboot, run a smoke test for the module, and save the result to memory. On failure, disable the module and record why.
6. **Integrate:** write a capability file (11.3) and generate an MCP tool or skill from it.

### 11.3 Capability file for each module

```yaml
id: example_module
source: https://github.com/<owner>/<repo>
commit: <pinned sha>
enables: what the bot can now do
detect: command that shows the module is active
test: command that proves it works
rollback: ksud module disable example_module
tool: name and arguments of the generated MCP tool
```

### 11.4 Bootloop safety

- **Bootguard module:** its `post-fs-data.sh` counts unhealthy boots; Hermes clears the counter once it is running. After two unhealthy boots it touches the `disable` file in every agent-installed module folder under `/data/adb/modules`. A bootloop cannot be fixed from Termux, so this is the only safeguard that still works after the agent breaks boot.
- Install one module at a time, then reboot and run the health check, so a bad boot has one suspect.
- Keep an off-phone copy of the stock boot image, and look up the safe-mode procedure for your root manager and phone.

Sources: [KernelSU module installation and management (DeepWiki)](https://deepwiki.com/5ec1cff/KernelSU/4.3-module-installation-and-management) for the `ksud` commands; [MMRL CLI](https://github.com/MMRLApp/MMRL-CLI) for repo-based installs.

## 12. Skills and the method ladder

MCP tools are the verbs; skills are recipes that pick the cheapest reliable method, check the result, and fall back to the UI only when nothing else works.

### 12.1 Method ladder

Every skill tries these in order and stops at the first that works.

1. Native API or Termux:API
2. `su` or `adb` shell commands: `settings`, `cmd`, `svc`, `am`, `pm`, `input`
3. Accessibility tree actions: find by text or resource id, then tap
4. Screenshot plus vision: last resort, because it is slowest and least reliable

### 12.2 Skill format

Each skill holds these fields. Write them in Hermes's own skill format once its file layout is confirmed.

```markdown
name: toggle-wifi
trigger: user asks to turn Wi-Fi on or off
preconditions: root available; Android 12 or newer
methods: 1) svc wifi enable|disable  2) Quick Settings tap via accessibility
steps: run the command for the requested state
verify: settings get global wifi_on returns the requested state
rollback: set the previous state
tested_on: Android version, date, result
```

### 12.3 Starter skills

| Task | Preferred method | Fallback |
| --- | --- | --- |
| Wi-Fi on or off | `svc wifi enable` or `disable` | Quick Settings tap |
| Brightness | `settings put system screen_brightness <0-255>` | Settings slider |
| Open an app | `monkey -p <pkg> 1` or `am start` | Launcher search through accessibility |
| Media play, pause, next | `input keyevent` media keys | In-app buttons |
| Volume | `cmd media_session volume` (syntax varies by version) | Volume keys through `input keyevent` |
| Screenshot | `screencap -p` | App screenshot API |
| Install an APK | `pm install -r <apk>` | Package installer UI |
| Read notifications | NotificationListener in the app | Parse `dumpsys notification` |
| Send a WhatsApp message | `am start` with a `wa.me` link, then tap Send | Full UI flow |
| Take a photo | App camera API | Camera intent, then tap the shutter |
| Reboot | `reboot` | Power menu through accessibility |

### 12.4 Learning rules

- **Verify every action:** read the state back after acting, for example `settings get global airplane_mode_on`.
- **Promote better methods:** when a command is found for a task that currently uses the UI, make the command preferred and keep the UI as the fallback.
- **Key by version:** store what worked per Android version and per app version, and re-test after an OS or app update.
- **Selectors before coordinates:** UI recipes save text or resource ids first and screen coordinates only as a last resort.
- **Registry:** keep each skill's success rate and last-success date in a capability registry, and run it in the nightly regression set from section 8.

## 13. App self-improvement

Hermes can change the app's code, build it, install it, and check that it still works, with automatic rollback, because a broken app leaves the bot unable to hear or see.

### 13.1 Change without rebuilding where possible

Keep a stable host app and push most change into parts that reload without a new APK.

| Layer | Where it lives | How Hermes changes it |
| --- | --- | --- |
| Tools and logic | MCP server and gateway plugin (Python in Termux) | Edit the file, restart the process |
| Face and screens | WebView with HTML, CSS, and JS assets in a folder Hermes can edit | Edit the assets, reload the view |
| New native-side abilities | Optional plugin folder the app loads at runtime (script engine or DEX) | Drop in a plugin, reload (needs design work) |
| Services, manifest, native libraries | The APK itself | Full rebuild through the pipeline in 13.2 |

Because the manifest already declares every permission (10.6), full rebuilds should be rare.

### 13.2 Self-update pipeline

1. **Trigger:** you ask for a feature, or a skill fails because the app lacks an ability.
2. **Branch:** Hermes creates a git branch in `~/deskbot-app` and bumps `versionCode`.
3. **Edit:** Hermes patches the Kotlin, layouts, or manifest.
4. **Build:** Gradle in Termux (OpenJDK 17, Android command-line tools, an arm64 `aapt2` build) or a remote build on a PC over SSH. On-device builds are slow and can run out of memory, so test both routes early.
5. **Test:** unit tests and lint; a failure stops here with nothing installed.
6. **Install:** the bot says it is updating, saves the current APK as last-good, runs `pm install -r -g`, runs `grant_all.sh`, and restarts the service with `am`.
7. **Health check:** within 60 seconds the app must reconnect to the gateway and pass mic, camera, and speech self-tests; Hermes checks with a WebSocket ping and `logcat`.
8. **Keep or roll back:** on a pass, merge the branch, tag the version, and add a changelog line; on a fail, reinstall last-good with `pm install -r -d` (needs a debuggable build), revert the commit, and record why in a skill note.

### 13.3 Rules that keep the bot recoverable

- **Watchdog outside the app:** the watchdog and rollback script run in Termux, so they still work when the app crashes.
- **Second front door:** keep Telegram connected through the Hermes gateway, so you can talk to the bot while the app is down.
- **Protect the recovery path:** the update script, watchdog, and keystore sit outside the folder Hermes edits; it can propose changes to them, and you apply them.
- **One change per build,** then the nightly regression run (section 8) on the new version.

## 14. Generative UI with A2UI

The app renders A2UI surfaces, so Hermes can answer with interactive native screens (cards, forms, toggles, lists) built only from components the app registers, alongside speech. A2UI v1.0 is an open-source protocol in which the agent streams JSON messages that describe UI and a native renderer draws them; Jetpack Compose has an official renderer.

### 14.1 Flow

```
Hermes reply containing an <a2ui> block (Express format)
  -> deskbot gateway plugin: A2UI Python SDK compiles and validates it against the catalog
  -> WebSocket: createSurface, updateComponents, updateDataModel
  -> app: Compose A2UI renderer draws the surface with native widgets
  -> user taps a control -> action message -> plugin -> phone tool or Hermes
```

Bound controls such as sliders and passenger-count steppers update the surface's data model locally; only explicit triggers such as a button tap send an `action` back.

### 14.2 Building blocks

| Piece | Choice |
| --- | --- |
| Renderer | Jetpack Compose A2UI renderer inside the app |
| Transport | The existing app-to-gateway WebSocket; A2UI messages travel next to text and audio events |
| Agent side | A2UI skill generated from the catalog and installed as a Hermes skill; the Python Agent SDK in the gateway plugin compiles and validates output |
| Fallback | An MCP tool `ui_show` that sends a prebuilt layout when inline output fails validation |
| Version | Pin spec v1.0 in the catalog, renderer, and SDK |

### 14.3 Deskbot catalog

The A2UI basic catalog (text, cards, rows, columns, buttons, inputs) plus these custom components:

| Component | What it shows |
| --- | --- |
| `DeviceToggle` | A setting with live state, such as Wi-Fi or Do Not Disturb; a tap runs the matching tool |
| `StatusGauge` | Battery, temperature, storage, or network level |
| `AppTile` | App icon and name; a tap launches the app |
| `NotificationCard` | One notification with its action buttons |
| `CameraSnapshot` | The last camera frame with detected objects labeled |
| `ScreenshotView` | A screenshot with highlighted regions |
| `TimerCard` | A live countdown or stopwatch |
| `SkillCard` | A learned skill with run, edit, and disable buttons |
| `ConfirmCard` | Large yes and no buttons for actions you confirm by touch |
| `WebEmbed` | A sandboxed HTML view (MCP App) for bespoke visuals such as maps |

### 14.4 Surfaces

- **Desk surface:** beside or under the face on the main screen.
- **Overlay surface:** drawn over any app, using the overlay permission.
- **Full-screen surface:** dashboards, photo frame, morning briefing.

### 14.5 Rules

- **Voice first:** every surface comes with a short spoken line, because you may not be looking at the screen.
- **Action routing:** simple device actions such as a toggle call the phone-control MCP tool directly through the plugin (A2UI supports calling MCP tools); anything needing judgment goes to Hermes as a structured message such as `[ui-action confirm {context}]`. Both are logged.
- **No rebuild for new screens:** Hermes composes new layouts from the catalog at runtime, which suits section 13.1.
- **Rebuild for new components:** a new catalog component is native Compose code, so it goes through the 13.2 pipeline, and the A2UI skill is regenerated from the updated catalog afterward.
- **Templates:** screens Hermes uses often, such as the briefing or device status, are saved as macros so they render quickly and the same way every time.

Sources: [A2UI home](https://a2ui.org) and [How A2UI works](https://a2ui.org/concepts/overview/). Check the Compose renderer's current release before building, as the project is young.

## 15. Persona system

The face is a swappable persona pack, and switching packs changes the face, the colors of the whole app and every A2UI screen, the voice, and the personality in one step. The default pack is a set of animated eyes; Hermes can create new packs on request.

### 15.1 Persona pack

| Part | What it controls |
| --- | --- |
| Renderer and assets | The face or character: settings only for the eyes, or model and sprite files |
| Expression map | The animation for each bot state: idle, listening, thinking, speaking, working, error, sleeping |
| Theme tokens | Colors, fonts, and background for the app shell and all A2UI surfaces |
| Voice and personality | TTS voice and a Hermes profile: name, tone, catchphrases |
| License record | Where each asset came from and what use its terms allow |

Each pack is one folder with a `persona.json` manifest, validated against a schema like every other shared contract (Architecture tab, single sources of truth).

### 15.2 Face engine

One renderer handles every pack type: a WebView inside the `face` feature module, driven by bot state and speech loudness over a JavaScript bridge.

| Pack type | Library |
| --- | --- |
| Default eyes | Drawn shapes from parameters (eye shape, color, pupil size, blink rate, eyelid curve); no image files |
| 2D anime character (Live2D) | pixi-live2d-display |
| 3D avatar (VRM) | three.js with three-vrm |
| Sprites and animations | Image sets per expression, Lottie, or Rive |

- Because the engine is web code, Hermes can change it without rebuilding the APK (section 13.1).
- A request like "make the eyes sleepy and green" is only a parameter change, applied instantly.
- A2UI gets a `PersonaView` component so the face can appear inside screens Hermes composes, plus a persona gallery screen for browsing, previewing, and switching.

### 15.3 Create-persona skill

1. **Read the request:** character type, style, 2D or 3D, mood.
2. **Get assets, in this order:** a file you share to the bot; licensed sources such as VRoid Hub (VRM avatars with per-model usage terms), Sketchfab (downloadable Creative Commons models), and Live2D sample models; generation with Hermes `image_generate` for sprite sets or a text-to-3D service for a mesh.
3. **Map expressions:** VRM defines standard expressions and mouth shapes, so moods and lip sync work automatically; an unrigged model is shown only as a still or turntable, so VRM is preferred.
4. **Make a theme:** extract a color palette from the textures and write the theme tokens.
5. **Write the personality:** name, voice choice, speaking style.
6. **Check before switching:** render every state and confirm it with vision, measure frame rate, memory, and temperature, then switch. The previous pack is kept so "change it back" works.

### 15.4 Named characters

Characters owned by companies, such as game characters or brand mascots, are not fetched as ripped or unlicensed models, since extracting game assets breaks those games' terms. The skill installs a model you import yourself or one whose license allows personal use, and otherwise builds an original character in a similar spirit with its own design and name. Generic requests, such as an anime-style companion, are filled from repositories where the author allows personal use, checked per model.

### 15.5 Limits

- Cap the face at about 30 frames per second, drop to a few when idle, and dim the screen when no one is present, because a 3D avatar rendering all day heats the phone.
- Check the Live2D Cubism license terms; it is free for individuals and small projects.
- If a pack fails to load or crashes the engine, the error state falls back to the default eyes.

### 15.6 Standard expression set

Every pack, including the default eyes, supports one shared set of expressions, defined once in `expressions.yaml` and generated into the face engine, the app, the Hermes expression skill, and the pack validator.

Three layers play at once, each with an intensity from 0 to 1 and a blended transition:

| Layer | Lasts | Expressions |
| --- | --- | --- |
| Mood | Until changed | neutral, happy, sad, angry, surprised, worried, scared, confused, bored, sleepy, shy, proud, love, focused, disgusted |
| Bot state | Set by the app automatically | idle, listening, thinking, speaking, working, updating, error, sleeping, offline |
| Reaction | 1 to 3 seconds, then back to the mood | cry, laugh, giggle, wink, nod, shake no, blink, eye roll, startle, sigh, yawn, celebrate, dizzy, heart eyes, star eyes, mind blown, glance at user, look around |

**How the default eyes show them** (a sample; every expression gets a design):

| Expression | Default eyes |
| --- | --- |
| happy | Eyes curve into upturned arcs with a small bounce |
| sad | Outer lids droop, pupils sit low, slow blinks |
| cry | Sad eyes plus tears falling from the lower lids |
| angry | Inner lids slant down, pupils shrink, slight red tint |
| thinking | Eyes glance up and to one side, one lid narrows, three dots pulse |
| confused | One eye larger than the other, a question mark appears |
| surprised | Eyes widen, pupils shrink |
| listening | Eyes open slightly wider and follow the speaker |
| sleepy, sleeping | Half-closed lids; closed lines with drifting Zzz |
| love, heart eyes | Heart-shaped pupils |
| shy | Eyes look down, blush marks appear |
| laugh | Closed arcs that bounce quickly |
| wink | One eye closes into an arc |
| dizzy | Spiral pupils with a slow wobble |
| error | X-shaped eyes with a short flicker |

- **Shared effects layer:** tears, blush, sweat drop, hearts, sparkles, Zzz, question mark, anger mark, and spirals are drawn by the engine over any pack, so a 3D avatar gets the same effects with no extra assets.
- **Fallbacks:** each pack maps what it has natively (VRM presets such as happy, angry, sad, relaxed, surprised; Live2D motions). Anything missing resolves through a fallback chain in `expressions.yaml`: cry becomes sad plus tears, love becomes happy plus hearts, laugh becomes happy plus a bounce, dizzy becomes neutral plus spirals.
- **Validation:** the validator rejects a pack only if a mood has neither a mapping nor a fallback; the create-persona skill renders every expression during its check step (15.3, step 6).
- **Triggering:** the app sets bot-state expressions itself. Hermes marks moods and reactions in its reply with inline tags such as `<expr happy 0.8/>`; the gateway plugin strips them from the spoken text and sends them as control events timed with the speech. Hermes learns the vocabulary from a skill generated from `expressions.yaml`, so it never drifts from what packs support.
- **New expressions:** adding one to `expressions.yaml` regenerates the enum, skill, and validator; packs without a design for it use its fallback until the eyes or the pack gets one.

## 16. Things to verify before building

These came from documentation reading and need confirmation on the real setup.

1. Does the Hermes API server accept image content in chat requests? (decides how camera frames are sent)
2. What methods must a Hermes platform plugin implement, and can it stream partial output? (decides the WebSocket protocol)
3. Can cron jobs deliver to a custom platform adapter so the bot can speak reminders?
4. How are MCP servers declared in `config.yaml` (command, args, env), and do they reload without a full restart?
5. Android version and phone model: affects phantom process commands, camera access from background, and accessibility restrictions.
6. Which STT, TTS, and vision-capable model give the best latency and cost balance?

## 17. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Android kills Termux or the app | Wake lock, battery exemptions, phantom process fix, watchdog, boot scripts |
| Slow replies break the "robot" feel | Streaming everywhere, filler sounds, small images, short answers |
| Agent bricks the phone | Deny-list, backups, stock image on hand |
| Camera or mic conflict between Termux and app | App owns hardware; Termux requests through the bridge |
| Runaway API cost | Provider spending cap, tool-call limits, nightly cost check |
| Self-edits degrade skills | Snapshots, regression set, weekly review |
| Battery swelling from constant charging | Charge limiter, ventilation |
| Accessibility quirks across apps | Per-app recipes as skills, root `input` fallback |

## 18. Immediate next steps

1. Note the phone model, Android version, and Hermes version.
2. Get Hermes starting on boot and share the result of a curl test to the API server.
3. Decide the app framework (Kotlin recommended for accessibility, camera, and foreground services).
4. Pick STT and TTS providers for the first prototype.
5. Start the MCP server skeleton and the gateway developer-guide review alongside the voice app work.
