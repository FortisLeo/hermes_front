# Skill path vs bridge tool path: timing

Measured on the phone (Redmi, Android 16), Hermes 0.23.3, model `gpt-6.1-terra` via `api.0-0.pro`, API `/v1/chat/completions` with `stream:true`, timed from the host over `adb forward` (about 1 ms overhead).

## Important finding first: the production gateway had no `deskbot_*` tools

The gateway that was running (pid 25360, started 01:47 by a client with `--idle-exit`) logged `Desk Bot tool discovery failed. Start the paired Android app before Hermes.` at startup. The `deskbot-phone` plugin registers tools only at gateway start (SETUP step 4), and the app was not up yet, so that gateway has **zero** `deskbot_*` tools. With it, both the "tool" prompt and the "skill" prompt fail: the model spends 4 to 5 calls (`skills_list`, `skill_view`, `tool_search`, `tool_search`, `terminal`) and ends with "deskbot_battery isn't available" after 35 to 80 s (3 sample trials, battery/device_info; kept in the scratchpad, not in the tables below). Fix: restart the gateway after the app is running (one restart, not done: I was only authorised to restart if Hermes died).

To measure a working tool path without touching that gateway I started **my own isolated benchmark gateway** (pid 23399, the `--force --quiet` one; no `--idle-exit`): `HERMES_HOME=~/.hermes-bench`, API port 8652, `config.yaml`/`.env`/`auth.json` copied (Telegram removed), `skills` and `plugins` symlinked to the real ones, started as root (uid 10343 under `su` has no network) via `/data/local/tmp/bench_env.sh`. It discovered the tools fine. Its code version string is slightly newer than the production gateway's. It is a benchmark artefact and should be stopped after the work (`kill 23399`, `rm -rf ~/.hermes-bench`). The `pyc` cache is in `/data/local/tmp/pyc-bench`.

## How sessions and prompts were driven

- Session selection: the chat completions endpoint has no request header for choosing a session. The id is derived from the system prompt plus the **first user message** (`_derive_chat_session_id`, `api-<sha256[:16]>`) and returned in the `X-Hermes-Session-Id` response header. So a fresh session per trial = a unique `[trial <nonce>]` prefix in the first message. The warm call replays the first turn, the answer, and a second user turn ("do exactly the same again"); the response header confirmed the same session id every time. (`/api/sessions/{id}/chat` also exists.)
- Skill prompt: "Load skill `<name>` with skill_view and follow its recipe to ...". Tool prompt: "Call the `deskbot_<tool>` tool directly ... do not load any skill".
- Raw: `{"method":"call"}` over TCP 8643, new connection each, cold = first, warm = second.
- 3 fresh sessions per cell, each with one warm follow-up. Cells: 8 features x 2 paths x 3 trials = 48 cold + 48 warm model runs. Harness: scratchpad `bench.py`.
- "TTFT" below: first non-whitespace content token. Hermes streams a space plus `hermes.tool.progress` events while tools run, and the model only writes its answer at the end, so TTFT is about equal to total. The more useful "first tool" is the time to the first tool-progress event (end of the first model call, which includes reading the 37K token prompt).

## Results (seconds, median with min-max of 3 trials)

| Feature | Raw bridge (ms) cold / warm | Tool path cold | Tool path warm | Skill path cold | Skill path warm |
| --- | --- | --- | --- | --- | --- |
| battery | 12 / 10 | 46.6 (27.3-72.8) | 27.2 (21.6-45.8) | 40.4 (29.3-60.2) | 41.4 (32.0-53.8) |
| device_info | 8 / 9 | 32.6 (18.7-58.9) | 26.4 (23.0-30.7) | 36.5 (33.8-49.7) | 35.9 (29.1-38.1) |
| screen_state | 23 / 17 (status failed in 2 of 3) | 37.0 (31.4-44.2) | 22.3 (20.4-39.1) | 38.2 (29.5-44.3) | 36.2 (29.9-38.2) |
| notifications_list | 14 / 24 | 31.9 (25.6-52.8) | 43.3 (37.5-54.5) | 31.2 (26.0-35.6) | 47.1 (29.9-65.2) |
| camera_capture (front) | 1536 / 1507 (160 KB reply) | 37.4 (32.0-44.1) | 28.4 (20.5-53.1) | 33.9 (30.8-59.9) | 45.0 (35.2-47.8) |
| health_score | 17 / 17 | 25.5 (25.3-30.4) | 25.1 (20.9-26.2) | 54.7 (38.3-83.0) | 50.1 (34.8-60.0) |
| sound_level (1 s) | 1182 / 1190 | 28.2 (26.5-47.8) | 27.1 (24.8-44.4) | 50.0 (41.4-105.2) | 48.9 (38.4-50.6) |
| location_get | 25 / 20 | 41.6 (33.3-44.4)* | 40.2 (27.2-53.4)* | 52.8 (43.2-54.2)* | 45.0 (36.2-53.5)* |

\* `location_get` returned an error reply (no fix or permission) in the model's answer; time still shows the path cost.

Aggregates over all 24 trials per path:

| Path | Cold total median (min-max) | Warm total median (min-max) | First tool event (cold / warm) | TTFT cold / warm | Model calls (tool hops) per run |
| --- | --- | --- | --- | --- | --- |
| Raw bridge | 8 to 25 ms (1.2 to 1.5 s for camera and sound, which do real work) | same | n/a | n/a | 0 |
| Tool path (`deskbot_*` registered) | **33.0 s** (18.7-72.8) | **27.2 s** (20.4-54.5) | 9.7 s / 7.6 s | 32.3 / 26.3 s | 3 (`tool_search`, `tool_describe`, `deskbot_x`) plus the final answer call |
| Skill path (skill_view then recipe) | **40.9 s** (26.0-105.2) | **39.9 s** (29.1-65.2) | 8.8 s / 8.8 s | 39.9 / 38.7 s | 4 (`skills_list`/`skill_view`, `tool_search`, `tool_describe`/`terminal`, tool) |

Model side: median model API call 5.4 s (range 3 to 10 s); first call carries about 37K prompt tokens (about 17K at the lowest, 120K late in a long session), 50 to 100 output tokens. Provider cache hit about 90 % on calls after the first (`cache=45633/50702`), so the first call of a fresh session still pays a cold-ish 6 s read of the 37K prefix.

## Main finding

1. The bridge itself is not the cost: 8 to 25 ms (camera and sound, 1.2 to 1.5 s, are real hardware work). Over 99.9 % of end-to-end time is LLM round trips at about 5.4 s each (slow endpoint, 37K token prompt).
2. Tool path beats skill path: about **8 s faster cold (33 s vs 41 s, about 20 %)** and about **13 s faster warm (27 s vs 40 s, about 32 %)**, because it needs one fewer model call. The skill path always pays `skill_view` (a whole extra call, 5 to 10 s) and, for some, follow-up `read_file`/`terminal` calls; skills here mostly just say "call `deskbot_<tool>`".
3. Neither path is fast, because `deskbot_*` tools are **deferred behind `tool_search` / `tool_describe` / `tool_call`** (Hermes "tool search", tier 1: 15 core tools, rest deferred). A direct tool call costs `tool_search` (1 call) + `tool_describe` (1 call) + the real call + the answer: 4 model calls, about 20 to 25 s, even when the answer is one function call. Cold vs warm difference is small (6 s for tool, 1 s for skill) because the fresh-session cost is mostly the 37K-token prompt, which the provider prefix cache already serves across sessions (identical system prompt and tools).
4. Variance is large (18 s to 105 s): single slow provider calls (up to 40 s) dominate the worst cases.

## Recommendations to make the cold path faster

1. Avoid the three discovery hops (largest win, saves about 10 to 15 s). In `~/.hermes/config.yaml` under `tools.tool_search`, set `enabled: off` (all 134 deskbot tools eager, about 39 KB / 10K tokens of schema; cached after the first call) or, better, keep the bridge but expose only a small hot set eagerly. Not applied or measured here (config untouched); test it on the isolated gateway first (`~/.hermes-bench/config.yaml`). Alternatively register fewer tools in the plugin: expose about 20 hot tools (battery, device_info, screen_state, notifications_list, tts_say, face_set, camera_capture, ...) and put the rest behind one `deskbot_call(name, args)`.
2. Prime the model so it skips `skill_view`: put a one-line map in `SOUL.md` or the skill index ("battery: call deskbot_battery directly; load a skill only for multi-step jobs"). Skills should be for procedures, not for single-tool features; most Deskbot skills are index pages of 2 to 6 KB each (125 KB total across 30 skills) whose first line is the tool name.
3. Shrink the base prompt (37K tokens): disable toolsets the phone never uses for the `api_server` platform (`platform_toolsets.api_server` currently lists `hermes-api-server` plus `deskbot_phone`), trim `SOUL.md`/memories. Every token is re-read on each of the 4 to 5 calls; at 5 to 6 s per call, input reduction helps first-call latency.
4. Prompt cache: keep the system prompt and tool array byte-stable (no timestamps, no reordering) so the 90 % cache hit also covers the first call of a fresh session; a session id based on a unique first message is fine because the cache is by prefix, not session. Keep the model warm (cache TTL) with a cheap periodic request if the endpoint's cache window is short ("cache window not guaranteed warm" appears in the log).
5. Fix the registration order: start the gateway only after the app bridge is up, or add a re-discovery step. Today a gateway started first has no tools at all and every request burns 4 to 5 calls failing. `scripts/` should warn when `/health` is up but the plugin logged discovery failure (grep `errors.log`).
6. Voice-first features that do not need the model (battery, time, timers, face) can call the bridge directly from the app (floor 10 to 25 ms) and only use Hermes for reasoning.
7. Memory: the production gateway runs with most of its 128 MB RSS plus 244 MB in swap; on a 3.4 GB phone with about 60 to 85 MB free, a cold request can stall on swap-in. See `memory-pressure-test.md`.

## Caveats

- Skill-path numbers include the model sometimes also trying `terminal` fallbacks; `location_get` error replies in both paths.
- 3 trials per cell is a small sample; medians over 24 trials per path are more reliable than per-cell numbers.
- Provider latency drifts through the day; tool and skill runs were sequential (tool first, then skill), not interleaved.
- The benchmark gateway (about 260 MB RSS) was resident during these runs, so they ran with less free RAM than normal.
