# CLAUDE.md

Deskbot: the Android "body" for a Hermes Agent desk robot. Hermes (Termux, tmux, same phone) is the brain; this app senses, speaks, shows a face, and executes phone actions. Specs live in `docs/` (`Tab 1.md` = project plan, `Architecture.md`, `Feature catalog.md`). Read `Architecture.md` before adding a capability.

## Stack
Kotlin 2.2, AGP 9.3, Compose (BOM in `gradle/libs.versions.toml`), coroutines/Flow, OkHttp, kotlinx.serialization, DataStore. minSdk 24, target/compile 37. Package root: `com.rishitgoklani.hermes_front_mobile`. All versions go in the version catalog only.

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (must pass before any install)
- Install + grant: `adb install -r -g app/build/outputs/apk/debug/app-debug.apk`, then `adb push scripts/grant_all.sh /data/local/tmp/ && adb shell su -c 'sh /data/local/tmp/grant_all.sh'` (re-run after every update)
- Logs: `adb logcat | grep -i deskbot`

## Module architecture (multi-module, dependencies point inward)
```
:app                     wiring only (DI container, manifest, navigation)
:feature:chat | voice | face | camera | bridge | settings | a2ui   (one per feature)
:core:domain             pure Kotlin: entities, use cases, ports (interfaces)
:core:data               port implementations (repositories, Android adapters)
:core:network            OkHttp, SSE, TCP bridge transport
:core:schema             shared message models (serialization)
:core:ui                 shared Compose components + theme
:core:testing            the ONE shared set of fakes/fixtures
```
Rules (enforced by Gradle module graph, later Konsist):
- `:core:domain` imports nothing Android and nothing from other modules.
- A feature module depends only on `:core:domain`, `:core:ui`, `:core:schema`. **Never on another feature.**
- `:core:data`/`:core:network` implement domain ports; `:app` is the only place that wires implementations to ports.
- Today the code is still one `:app` module with `domain/`, `data/`, `ui/` packages. Moving code into the modules above is an explicit migration task; keep packages matching the target module so the move is mechanical.

## SOLID, applied here
- **S**: one use case per action (single public `invoke`); one executor per method rung; one ViewModel per screen.
- **O**: add behavior by adding a class and registering it (capability registry, executor list); do not add `when` branches to existing classes.
- **L**: every `ActionExecutor` returns the same result type and error model so executors are interchangeable.
- **I**: small ports (`HermesGateway`, `CameraPort`, `SpeechPort`, `AccessibilityPort`, `OverlayPort`); never one `DevicePort`.
- **D**: features and use cases depend on ports; constructors take interfaces; no `object` singletons holding state, use DI-scoped instances so tests can swap fakes.

## Design patterns (use only for the named problem)
Strategy + Chain of responsibility (`ActionExecutor` ladder: native API, then `su`/adb shell, then accessibility, then screenshot+vision last); Command (`DeviceCommand` with execute/verify/undo); Decorator (audit, retry, timing, deny-list wrappers); Facade (`BotSession`); Adapter (Android APIs and gateway behind ports); Repository (settings, conversation); Observer (`StateFlow`/`SharedFlow`); State machine (`BotState` with legal transitions only); Registry + factory (capabilities, A2UI components); Template method (self-update steps).

## Testing: everything gets a test
- Every use case, repository, executor, decorator, parser, ViewModel, and bridge handler ships with a test in the same change. No test, no merge.
- Write tests against ports with fakes from `:core:testing`; never define a private duplicate fake in a test.
- Tools: JUnit4, MockK (sparingly, prefer fakes), Turbine for Flow, `kotlinx-coroutines-test`, OkHttp `MockWebServer` for HTTP/SSE, Compose UI tests for screens, Robolectric for Android adapters.
- Contract tests: JSON fixtures in one shared folder must round-trip in Kotlin and match the Hermes side.
- Device tests (`androidTest`) cover the bridge on the real phone; mark them so they do not run in the default unit task.
- Coverage target 90% on domain and data; test names describe behavior (`parsesDelta`, `rejectsBadToken`).

## Hermes contracts (do not break)
- Chat: Hermes OpenAI-compatible API `http://127.0.0.1:8642/v1/chat/completions`, SSE, `Authorization: Bearer <API_SERVER_KEY>`, stable session id for memory. Images go as `image_url` data URIs (verify support).
- Tool bridge: the app serves newline-delimited JSON over TCP `127.0.0.1:8643`, token-authenticated, consumed by `~/.hermes/plugins/deskbot-phone` on the phone. `{"method":"list"}` returns `{"tools":[{name,description,inputSchema}]}`; `{"method":"call","name","arguments","token","id"}` returns `{"status":"completed|handed_off|accepted|..."}`. Token file: `~/.hermes/deskbot-bridge-token` (Termux home).
- Every new capability follows `docs/Architecture.md` "Where a new capability goes": Hermes built-in, skill, MCP/bridge tool, app capability, A2UI, then app change.

## Device and root (KernelSU)
- Phone: Android 16, arm64, KernelSU. `adb shell su -c '...'` runs as root. Termux data: `/data/data/com.termux/files/home` (`~/.hermes` is Hermes state).
- Root scripts live in `scripts/`, are idempotent, print `ok`/`FAIL` per step, and never run destructive commands (`dd`, `mkfs`, `fastboot`, partition or block-device writes, `rm -rf /`).
- Read before write: inspect a device file or setting before changing it; keep a `.before-*` backup when editing Hermes config.
- Do not restart Hermes or reboot the phone without saying so; the user may be mid-conversation with the bot.

## Reusing outside code
- Searching the web and GitHub for existing projects is encouraged before writing something non-trivial (wake word, VAD, A2UI renderer, face/eyes, SSE, accessibility helpers).
- Only reuse code with a permissive license (Apache-2.0, MIT, BSD). Check the license file first; GPL/AGPL or unlicensed code is reference only, never copied.
- Pin the exact commit/version, adapt to our ports (do not paste a foreign architecture in), keep the original copyright header, and add a row to `THIRD_PARTY.md` (name, URL, commit, license, what it is used for).
- Prefer a Gradle dependency over copied source when a maintained artifact exists.
- Downloaded repos go in their own new directory outside the project tree (e.g. scratchpad); read them, do not run their scripts blindly, and never execute `curl | sh` from the web.

## Conventions
- Secrets (API key, bridge token) live in DataStore or device files only; never in source, logs, tests, or commits. Do not print them in command output.
- Generated files (once codegen exists) are never hand-edited.
- Keep UI state in one `StateFlow` per ViewModel; screens are stateless and take state plus callbacks.
- Voice-first: every user-facing result has a short speakable form.
- Small, reviewable changes; one capability per change.

## Root shell notes (learned on device)
- The app's `shell` tool runs `su --mount-master -c`; without `--mount-master` root cannot see other apps' data dirs (e.g. Termux).
- Root profile for the app is set with `ksud profile set <file>` (see docs/Feature status.md history); regenerate status with `python3 scripts/gen_feature_status.py` after `python3 scripts/verify_recipes.py`.
