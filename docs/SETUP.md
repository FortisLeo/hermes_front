# Setting up a Deskbot phone

Deskbot is the body of a Hermes Agent: this Android app senses, speaks, shows a face and acts on the phone, and Hermes (running in Termux on the same phone) is the brain. This guide takes a fresh phone to a working desk robot. For the short version, run `./scripts/setup_device.sh` (see [One click](#one-click)).

## 1. What you need

| Item | Notes |
| --- | --- |
| A dedicated Android phone (Android 10+, tested on Android 16 / HyperOS, arm64) | Keep personal data off it. About 3.5 GB RAM works; more is better for the 3D avatar. |
| Root with KernelSU (Next) | Needed for the shell tool, screenshots, input injection and reading Termux files. |
| Termux (from F-Droid) | Hermes runs here, in a tmux session called `hermes`. |
| Hermes Agent installed in Termux | With its OpenAI-compatible API server on `127.0.0.1:8642` and an `API_SERVER_KEY` in `~/.hermes/.env`. |
| A PC with adb, JDK 17+ and the Android SDK | To build and install. USB debugging on. |

## 2. Build and test

```sh
./gradlew test testDebugUnitTest assembleDebug
```

`test` runs the pure-Kotlin domain tests, `testDebugUnitTest` the Android ones; both must pass. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Install and grant permissions

Some phones (HyperOS) restrict `adb install` after a crash; installing as root always works:

```sh
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deskbot.apk
adb shell su -c 'pm install -r -g /data/local/tmp/deskbot.apk'
adb push scripts/grant_all.sh /data/local/tmp/ && adb shell su -c 'sh /data/local/tmp/grant_all.sh'
```

`grant_all.sh` is idempotent and prints `ok`/`FAIL` per step. It grants runtime permissions, enables the accessibility service and notification listener, allows overlays, writes system settings, whitelists the battery optimiser and allows autostart (HyperOS app-op 10008). Re-run it after every update.

Give the app root: open KernelSU Next and grant Superuser to *Deskbot*, or set an `allow_su` profile with `ksud profile set`. The app's shell tool then runs `su --mount-master -c` (the `--mount-master` flag is what lets it see Termux's files).

## 4. Connect Hermes and the app

1. **API key.** Open the app, then Settings (swipe from the left edge or open the hidden menu), and enter the Hermes endpoint (`http://127.0.0.1:8642`) and the `API_SERVER_KEY`. Never put the key in the repo.
2. **Bridge token.** The app serves its tools on `127.0.0.1:8643` and Hermes's `deskbot-phone` plugin authenticates with `~/.hermes/deskbot-bridge-token`. Push it into the app with `./scripts/sync_token.sh` (it reads the token on the phone and never prints it).
3. **Skills.** Copy `hermes-skills/deskbot` to `~/.hermes/skills/deskbot` in Termux (the one-click script does this).
4. **Restart the Hermes gateway** so it discovers the `deskbot_*` tools. Hermes registers plugin tools only at start, so repeat this whenever the app gains tools:
   ```sh
   tmux new-window -d -t hermes -n deskbot-gateway 'hermes gateway run'
   ```
   (The window closes when the process exits, so create a fresh one.)

## 5. Termux extras

`scripts/termux/setup_packages.sh` installs `openssh rsync ffmpeg nmap tesseract` (used by the SSH, sync, media, scan and OCR recipes). It backs up `~/.hermes` first, upgrades only `libc++` (newer ffmpeg needs it) and never runs a full `pkg upgrade`. `scripts/termux/verify_packages.sh` smoke-tests each tool.

For start-at-boot, install the Termux:Boot app, open it once, and place `scripts/termux/boot.sh` at `~/.termux/boot/10-deskbot.sh`; `watchdog.sh` restarts the gateway and app if they stop.

## 6. First run checklist

- The face fills the screen; the status bar and navigation bar are hidden.
- Swipe from the left edge for the hidden menu: Chat, Voice, Debug, Settings, Avatar, and the Always-listen toggle.
- Debug shows the Hermes link, bridge tool count and the recent tool-call audit log.
- Say "Hey bot" (the wake word model downloads once, checksum-pinned). The stop phrases "stop" (cancel the turn) and "emergency stop" / "kill switch" (kill switch) always work.
- Ask Hermes to do something: "show my battery on screen", "set a 5 minute timer", "wave hello".
- Verify everything: `python3 scripts/verify_recipes.py` (read-only phone commands) and `python3 scripts/verify_hermes.py` (real agent tasks).

## 7. The 3D avatar (optional)

Copy a permissively licensed `.vrm` to the phone and have Hermes call `avatar_import {path}` (only CC0, CC-BY, CC-BY-SA or VRM "everyone" licences are accepted), then `avatar_mode avatar`. `.vrma` animation clips import with `avatar_import_clip` and play with `avatar_clip`. See the README for how the agent poses the avatar.

## 8. Safety

- **Kill switch:** the phrase "emergency stop", three volume-down presses, or the `kill_switch` tool refuse all further tool calls. Only the user can reset it, on the phone (Debug screen). Hermes cannot.
- **Shell deny-list:** `dd`, `mkfs`, `fastboot`, partition tools, `rm -rf /`, recovery or bootloader reboots and wipes are refused.
- **Multi-touch:** multi-stroke accessibility gestures are blocked on Xiaomi/Redmi/POCO because one coincided with a system crash; drags use root `input draganddrop`.
- **Safe mode:** repeated crashes at start-up turn the risky sensors off until you clear it.
- **Privacy:** presence detection counts faces and discards the frame; the app never stores or sends camera frames unless a tool call asks to.

## 9. Troubleshooting

| Symptom | Fix |
| --- | --- |
| Hermes does not call `deskbot_*` tools | Restart the gateway (step 4); check the token with `./scripts/sync_token.sh`. |
| All tool calls say "Kill switch active" | Reset it on the phone: menu, Debug, reset. |
| Tools time out or the app is killed | The phone is low on memory (Termux + Hermes + app). Close other apps; the avatar falls back to eyes after two unclean exits. |
| Accessibility tools fail | Re-run `grant_all.sh` (it re-enables the service after each update). |
| Notifications tools return nothing | `cmd notification allow_listener com.rishitgoklani.hermes_front_mobile/.data.notifications.DeskbotNotificationListener` (done by `grant_all.sh`). |
| App does not start after reboot (HyperOS) | Allow autostart: `appops set com.rishitgoklani.hermes_front_mobile 10008 allow`. |
| `adb install` says `INSTALL_FAILED_USER_RESTRICTED` | Use the root `pm install` command in step 3. |
| Hermes answers with "rejected your API key" | The model provider key in Hermes is wrong; unrelated to the app. |
| Avatar screen is black | Update to the latest build; the canvas needed explicit pixel sizing in this WebView. |

## One click

```sh
./scripts/setup_device.sh                  # build+test, install, grants, skills, Termux scripts and packages, token
./scripts/setup_device.sh --no-build       # reuse the last APK
./scripts/setup_device.sh --no-termux-packages
```

Each step prints `ok` or `FAIL` and the rest still run. Afterwards restart the gateway (step 4).
