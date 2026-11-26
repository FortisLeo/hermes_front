# Avatar benchmarks

Device-side benchmarks for the native VRM avatar. Every load logs one `DeskbotVrmPerf` line with its phases, so you can also just
watch `adb logcat -s DeskbotVrmPerf` while using the app.

| Script | What it measures |
|---|---|
| `avatar_cold.sh [runs]` | Opening the app with an avatar saved: first frame (`am start -W`), activity created → avatar on screen, load phases |
| `avatar_swap.sh [seconds]` | Switching avatars over the bridge: every "next" (pre-loaded neighbour), then jumps that force a load from scratch |
| `avatar_release.sh [runs]` | Both of the above on a release build compiled ahead of time, then reinstalls the debug build |
| `face_sheet.sh moods\|reactions [crop]` | A contact sheet of the face for every mood or reaction (needs ImageMagick) |

Prerequisites: adb with root on the phone, Deskbot installed with an avatar selected, the bridge token synced
(`scripts/sync_token.sh`). The bridge token is read on the phone and never printed.

## Reading a load line

```
load d8224ba4 total=383 preloaded=15img/210ms open=0 parse=0 ... gpuWait=4 gpuRun=8 gl=22 images=15 cap=2048 reused=15 decode=0 uploadWait=.. uploadRun=.. textures=.. shaderWait=.. shaderRun=..
```

- `load` = shown now, `stage` = prepared behind the current avatar, `finish` = textures of a geometry-only stage at promotion.
- `*Wait` = time a task waited for the GL thread (a frame), `*Run` = time it ran there. `uploadWait`/`uploadRun` are summed over all
  images, which upload in parallel with decoding, so compare `textures=` (wall time) between runs.
- `preloaded`/`reused`: the model and images were prepared at app start (`AvatarStartup`), so parse and decode were skipped.
- `source=` where the parsed model came from: `preload` (app start), `cache` (`<key>.geo`, no GLB opened) or `glb` (parsed from the
  file). `textures=etc2` means the textures were uploaded straight from the ETC2 cache (`<key>.etc2`), with no decoding.
- `cache <key> geometry=…ms textures=…ms` lines are the background cache builds (a few seconds after a model's first load).
- `shown … want->shown=` is what a user waits for after a swipe or a bridge call.

## Gotchas

- **Don't touch the phone during a run.** Swipes mix extra avatar switches into the numbers.
- **Kill spacing.** Two process deaths within 60 s of start make `AvatarSafety` switch the app to the eyes face; the scripts keep the
  app alive ~65 s per cold run. If the app opens on the eyes after testing, that is why.
- **The process restarts by itself.** After `am force-stop` the accessibility/notification services bring the process back within
  ~0.3 s, so cold start is measured from the activity launch and logs are read by pid.
- **Debug builds are 3-6x slower** (no ahead-of-time compilation): judge speed on `avatar_release.sh`.
- **Signing.** Gradle signs debug builds with `~/.config/.android/debug.keystore`; the release script signs with the same key so it
  installs over the debug app without wiping its data.
