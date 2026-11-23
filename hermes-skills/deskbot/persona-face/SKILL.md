---
name: deskbot-persona-face
description: "Control the Deskbot face, expressions, moods, personas, theme and voice; expression tags in replies."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-generative-ui]
---

# Desk-bot face, persona and expressions

Expression tags: put `<expr NAME INTENSITY/>` anywhere in a reply (e.g. `Sure! <expr happy 0.8/>`). The app strips tags from speech and plays them. Moods persist until changed; reactions last 1-3 s. Valid names come from `expressions.yaml` (moods: neutral happy sad angry surprised worried scared confused bored sleepy shy proud love focused disgusted; reactions: cry laugh giggle wink nod shake_no blink eye_roll startle sigh yawn celebrate dizzy heart_eyes star_eyes mind_blown glance look_around). Use sparingly: one tag per sentence at most.

Tools: `deskbot_face_set` (mood/reaction), `deskbot_face_tweak` (eye parameters), `deskbot_persona_list`, `deskbot_persona_set`.

### 321 Face and expressions
`deskbot_face_set mood=happy intensity=0.8` or inline `<expr .../>`. Gaze: `deskbot_face_set look_x=-1..1 look_y=-1..1`.

### 322 Status display
The app shows listening/thinking/speaking/working/error/low-battery automatically from bot state; to show a custom status line use `deskbot_overlay_text`.

### 323 Idle behaviors
App idle loop: blink, glance around, yawn at night, clock display. Tune with `deskbot_face_tweak blink_rate=…`.

### 324 Voice and personality profiles
Persona packs bundle voice + name + tone (`deskbot_persona_set name=<pack>`); voice rate via TTS settings (#123). Change formality/language by instruction and store in memory.

### 325 Greeting and farewell
On arrival (#331) greet by time of day and mention one useful thing (next event); on departure say a short goodbye. Do not greet more than once per hour.

### 326 Emotional response
React to tone: thanks → `<expr happy/>`, errors → `<expr worried/>`, praise → `<expr proud/>`; keep it subtle.

### 327 Sleep and wake cycles
Night mode (cron): `deskbot_face_set mood=sleepy`, dim (`deskbot_screen_dim`), mute proactive speech; wake on first interaction or alarm.

### 328 Touch interaction with the bot
App: tap the face → a reaction; long-press → quick actions menu; swipe → settings. Hermes can ask the user to "tap my face".

### 329 Quick actions panel
App panel with common commands (timer, weather, lights, camera). Add/remove entries via `deskbot_quick_actions set=[…]` (planned) or in app settings.

### 330 Debug screen
App Debug tab: connection status, last tool calls (audit log tail), bridge/Hermes health. Ask the user to open it when diagnosing.

### 419 Default eyes face
Parameter-driven eyes: color, size, pupil, blink rate, eyelid curve; no image files.

### 420 Tweak the face by voice
"Make the eyes sleepy and green" → `deskbot_face_tweak color=#4CAF50 openness=0.5`; applies instantly; persist with `save=true`.

### 421 Switch persona packs
`deskbot_persona_list`, `deskbot_persona_set name=<pack>`; the previous pack is kept so "change it back" works.

### 422 Live2D characters
Planned pack type (WebView + pixi-live2d-display). Until implemented say it is not available; fall back to the default eyes. Check Cubism license terms before adding a model.

### 423 VRM 3D avatars
There are no built-in motions: you compose every movement yourself with three tools.
- `avatar_mode {mode: avatar|eyes}` shows the 3D avatar or the eyes as the face.
- `avatar_bones` lists every bone and node name of the loaded model; call it first, the names differ per model.
- `avatar_pose {bones, expressions, offsets, hips_offset, blend_ms}` holds a pose. `bones` maps ANY bone or node name from `avatar_bones` (VRM humanoid names such as hips, spine, head, leftUpperArm, rightLowerLeg, leftIndexProximal, or extra nodes) to euler `[x, y, z]` radians (non-humanoid nodes are offsets from their own rest rotation); `offsets` moves any node by `[x, y, z]` metres; all zero is the T-pose and the resting stance has the arms down (leftUpperArm z=+1.25, rightUpperArm z=-1.25). `expressions` maps names (aa ih ou ee oh blink, plus whatever the model defines) to 0..1. `reset=true` returns to rest.
- `avatar_animate {keyframes:[{t_ms, bones, expressions, hips_offset}], loop}` plays an animation you write (up to 60 keyframes, 60 s); values ease between keyframes, and a bone missing from a keyframe keeps interpolating between the ones that set it. Stop with `avatar_pose reset=true`.
Axes other than the arm z-axis are not documented: try a pose, take a screenshot (`deskbot_screenshot`), look, adjust. Keep angles within ±3.2 rad. Mouth lip sync and blinking run by themselves unless you set `aa` or `blink` yourself.
`avatar_import {path}` loads a model from a file on the phone (the user's own file: any licence, but it must be a valid VRM; catalog downloads via `avatar_download` are still licence-checked) and `avatar_import_clip` + `avatar_clip` play imported `.vrma` clips. Heat limits: the viewer draws at most 30 fps and 10 fps when idle; do not loop animations for hours. `avatar_library {action: list|select|eyes|next|remove, id}` switches between the installed avatars (the same ring the user swipes through); `avatar_catalog {action: collections|list, collection, query}` browses the OpenSourceAvatars catalog (free VRM models) and `avatar_download {collection, id}` installs one (same licence check). The user can also upload a .vrm or pick from the catalog in the Avatar screen.

### 424 Lip sync and gaze
Mouth/eye motion driven by speech loudness and gaze target (app). Not available for the default eyes except a subtle speaking animation.

### 425 Theme from a persona
Packs carry theme tokens (colors/fonts) applied to the app shell and A2UI surfaces on switch.

### 426 Create a persona on request
Flow: read request → get assets (user file, licensed sources, `image_generate`) → map expressions → make a theme → write personality → render every state, verify with vision and measure FPS/temperature → switch, keeping the previous pack. Named commercial characters: don't fetch ripped models; build an original in a similar spirit or use a user-supplied licensed file.

### 427 Persona gallery
A2UI screen listing packs with preview and switch/delete buttons (`deskbot_ui_show` template `persona_gallery`).

### 428 Persona license tracking
Each pack's `license.json` records source URL, author, license, allowed use; refuse packs with unknown or restrictive licenses; never ship ripped assets.

### 429 Standard expression library
All packs support the shared set above; missing ones resolve through fallback chains (cry → sad + tears, love → happy + hearts, laugh → happy + bounce, dizzy → neutral + spirals).

### 430 Effects layer
Engine-drawn tears, blush, hearts, sparkles, Zzz, question mark, anger mark, spirals over any pack; triggered by expressions automatically.

### 431 Expressions timed with speech
Inline `<expr/>` tags are removed from the TTS text and played at the matching sentence; a missing/unknown tag is ignored.
