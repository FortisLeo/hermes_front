# Memory-pressure test and the "Hermes killed due to low memory" explanation

Phone: Redmi (Android 16, KernelSU), 3.4 GB RAM, 4 GB swap (zram-style, 1.9 to 2.6 GB free at rest), `MemFree` normally 50 to 100 MB, `MemAvailable` 400 to 700 MB. lmkd is PSI-driven (`ro.lmk.use_psi`), `kill_heaviest_task` on.

## Root cause of the earlier kills (the main finding)

`dumpsys activity exit-info com.termux` lists **four** kills of the Termux app process, all `reason=3 (LOW_MEMORY)` by lmkd, `importance=125` (foreground service), rss about 100 MB:

| Time | Event |
| --- | --- |
| 2026-11-23 16:55 / 18:02 / 18:18 | Termux app process killed by lmkd |
| 2026-11-24 01:45 | same; Hermes restarted at 01:47 |
| 2026-11-25 02:00:26 | same (lmkd `killinfo` shows it killed `com.termux` adj 200 right after a burst of cached-app kills) |
| 2026-11-25 02:06:56 | same, while the Hermes dashboard started a `node web.mjs` build (about 414 MB RSS) |

Mechanism:

1. `oom_score_adj=-1000` (what `scripts/protect_termux.sh` already did) protects only the individual child processes. lmkd never picks them. But the **Termux app process itself** (adj 200: foreground service) is not protected: Android's ActivityManager owns its adj and rewrites it, so writing `-1000` to `/proc/<termux>/oom_score_adj` does not stick (observed `adj=200`).
2. When lmkd kills the Termux app process, Android also kills its whole **process cgroup** (`/apps/uid_10343/pid_<app>`). Every Hermes process (gateway, dashboard/TUI, tmux server, sshd, `process_supervisor.sh`) lived in that cgroup (`/proc/<pid>/cgroup` showed `0::/apps/uid_10343/pid_8537`), so they all died regardless of `-1000`. Next start they have new PIDs and `gateway.previous_unclean_exit` in `gateway-exit-diag.log`.
3. The trigger is memory spikes on a phone that already runs at `MemFree` about 60 MB: the Hermes stack itself (gateway RSS 100 to 130 MB plus 240 MB swapped, dashboard/TUI 40 to 190 MB, `node web.mjs` web build 414 MB RSS), the Deskbot app (170 to 250 MB), Chrome/Messages/Camera/system apps being re-launched.
4. Phantom process killer is **not** the cause: `settings_enable_monitor_phantom_procs=false` and `max_phantom_processes=2147483647` are applied, and no `PHANTOM` or `signal 9 from phantom` exit reasons appear in exit-info; all exit reasons were LOW_MEMORY.

## `--idle-exit` (second, independent way for Hermes to disappear)

`gateway run --idle-exit` is what the Hermes TUI/dashboard client uses when it auto-starts a gateway (`hermes_cli/gateway_runtime_start`). It arms `gateway/run_idle_exit.py`: after `gateway.unmanaged_idle_exit_seconds` (default 600) with no attached client, no messaging adapter, no cron/kanban work and no in-flight run, the gateway drains and exits (`drain_reason: idle_exit`). Only traffic through the gateway socket/dashboard server calls `note_client_activity`; requests to the aiohttp API on 8642 (`/health`, chat completions from the app) do not reset the timer except while a run is actually in flight. So a gateway started by `hermes`/`dashboard` with nobody attached would exit 10 minutes after its last client leaves, even with no memory pressure. It had been running for 20 h only because a TUI client stayed attached. Telegram is configured but its adapter fails (`No adapter available for telegram`), so it does not keep the gateway alive either.

The gateway reads the setting live on each 30 s tick (`idle_exit_seconds()` reloads the config), so no restart is needed. It was set to `0` (disabled) in `~/.hermes/config.yaml`; backup: `~/.hermes/config.yaml.before-idle-exit`.

## Fixes applied (`scripts/protect_termux.sh`, idempotent, prints `ok`/`FAIL` per step)

1. `escape_app_cgroup`: creates `/sys/fs/cgroup/deskbot-hermes` and moves every Termux-uid process except the Termux app process into it. Verified: after the move `/proc/<gateway>/cgroup` = `0::/deskbot-hermes`. Children inherit the cgroup from their parent (tmux server, gateway, supervisor), so only newly started top-level processes need the `--watch` loop. No process is restarted.
2. `disable_idle_exit`: appends `gateway:\n  unmanaged_idle_exit_seconds: 0` to `config.yaml` if absent (backup `.before-idle-exit` first; refuses and prints FAIL if a `gateway:` section already exists).
3. `--watch` now re-runs both the `-1000` protection and the cgroup escape every 30 s (new processes start at adj 0 inside the app cgroup). I started `sh /data/local/tmp/protect_termux.sh --watch` (root, background). It stops when the phone reboots; add it to `scripts/termux/boot.sh`/watchdog if wanted.

## Evidence the fix works (unplanned, then planned)

- 02:06:56: lmkd killed the Termux app process again (LOW_MEMORY, during the `node web.mjs` build), **after** the cgroup move. Gateway 11284, tmux 10844, sshd 11404 and the dashboard kept their PIDs, `/health` stayed `ok`, the bridge answered `list` (156 tools). Before the fix the same event (02:00:26) restarted every Hermes PID.
- Controlled test at 02:09 (below).

## Controlled pressure test (02:09 to 02:12, `adb shell su`)

Hog: Termux python as root, `oom_score_adj=1000`, +256 MB every 4 s to 768 MB (touched pages), hold 60 s, release; polled every 3.5 s. Capped at 768 MB (3 steps) because earlier uncontrolled spikes already killed the Termux app; the run with 4 steps was cut short by a launcher bug and an unrelated Termux kill (see above), so this is the clean run.

| | Baseline | During (peak) | After release |
| --- | --- | --- | --- |
| MemAvailable | 694 MB | 232 MB (low), 400 to 480 MB while holding | 784 MB then 730 MB |
| SwapFree | 2.06 GB | 1.33 GB | 1.83 GB |
| PSI memory `some avg10` | 0.00 | 6.96 | 0.00 |
| hog RSS | n/a | 583 MB peak, 349 MB held (rest swapped) | gone |
| gateway pid / adj | 11284 / -1000 | unchanged | unchanged |
| dashboard pid / adj | 13655 / -1000 | unchanged | unchanged |
| tmux (10844, 13380), sshd (11404, 13036, 13166) | alive, -1000 | unchanged | unchanged |
| Deskbot app | pid 18436, adj 0 (foreground, MainActivity focus) | unchanged | unchanged |
| Deskbot BotService | foreground, FGS | still foreground | still foreground |
| Accessibility service | `DeskbotAccessibilityService` enabled | enabled | enabled |
| Hermes `/health` | ok | ok | ok |
| Bridge `list` | 156 tools | n/a | 156 tools |
| PIDs changed | none | none | none |

Kills seen in `logcat -b all` during the window: only cached/background apps (`com.android.camera` repeatedly, `htmlviewer:remote`, `messaging:rcs`, `providers.calendar`, `messaging`), all by lmkd (`killinfo`). No lmkd kill of Hermes, tmux, sshd, the Deskbot app or its services. The hog was not killed by lmkd (processes started from `su` are not registered with lmkd, so adj 1000 is not honored); it exited by itself. Note that the Termux app process was already dead from the 02:06:56 kill when this test ran; Hermes did not need it.

## How the Deskbot app fared

No Deskbot exit-info after 01:06:43 (that one is `FORCE STOP` by an `adb`/install, not memory). In all kill bursts lmkd killed other apps first; the app is foreground (adj 0) with a foreground service, accessibility and notification listener bound.

## Recommendations (not applied)

1. Keep `protect_termux.sh --watch` running (or run `protect_termux.sh` from the Deskbot watchdog) after every Hermes restart.
2. Do not start the Hermes dashboard web build (`node web.mjs`, about 414 MB) on this phone while memory is tight; prebuild `web_dist` once or skip `dashboard`.
3. Free RAM at rest is about 60 to 90 MB with 2 GB swap already used: trim the app side (avatar engine textures, `Display quality: Low`) and uninstall/disable background apps (Chrome, Messages, Camera keep respawning and being killed).
4. The Termux app process can still be killed by lmkd; Termux:API/Termux notifications will vanish until the app is reopened, but Hermes now survives. To also keep the Termux UI alive, open Termux in the foreground occasionally or exclude it from battery restrictions (already done).
5. Re-run `scripts/protect_termux.sh` once after any phone reboot (cgroup `deskbot-hermes` is created at runtime and is not persistent).
