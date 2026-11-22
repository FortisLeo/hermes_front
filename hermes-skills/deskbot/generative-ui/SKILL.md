---
name: deskbot-generative-ui
description: "Compose and show interactive native screens (cards, forms, toggles, gauges, timers) with deskbot_ui_show; handle UI action events."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-persona-face]
---

# Generative UI (A2UI-style)

Screens appear **on the face** (the face is the whole app; chat/voice/debug/settings live in a hidden left-edge menu). A screen **auto-hides after `ttl_s` seconds (default 90; 0 = never)** and **hides after any button action** unless you pass `keep=true`; the user can also tap Hide. Keep screens short and always include `speak`. Use `deskbot_time_now` for the local date/time before composing anything time-based.

Tool: `deskbot_ui_show` takes a JSON `screen` built from the Deskbot catalog and a `surface` (`desk`, `overlay`, `full`). Always add a short spoken line (voice first). Components: `Text`, `Card`, `Row`, `Column`, `Button`, `Toggle` (bound), `Slider` (bound), `Gauge`, `Timer`, `ConfirmCard`, `AppTile`, `NotificationCard`, `DeviceToggle`, `StatusGauge`, `Image`. A button/confirm tap sends back a message `[ui-action <id> {json}]` as a normal turn; bound controls update locally and only send when a button is pressed.

### 411 Render agent-composed screens
`deskbot_ui_show surface=desk screen={"type":"Card","children":[{"type":"Text","text":"Battery"},{"type":"Gauge","value":80,"label":"%"}]}`. If validation fails the tool returns the error; fix and resend.

### 412 Two-way bound controls
Use `Toggle`/`Slider` with `bind="wifi"`; the app keeps their value; include a `Button action="apply"` to send the bound values back.

### 413 Live device control panels
Compose `DeviceToggle` (wifi, bluetooth, dnd, torch) and `StatusGauge` (battery, temp) from live tool data; a tap runs the matching tool directly through the bridge (no model round trip) and is logged.

### 414 Overlay surfaces over any app
`surface=overlay` draws a compact card above other apps (needs the overlay permission). Dismiss with `deskbot_overlay_clear`.

### 415 Saved screen templates
Save frequent screens as named templates (`deskbot_ui_show template=briefing data={...}`): briefing, status, timer, persona_gallery. Templates render fast and look the same every time.

### 416 UI actions to tools or Hermes
Simple device actions map to bridge tools (`action="tool:torch {on:true}"`); anything needing judgment goes to Hermes as `[ui-action confirm {context}]`.

### 417 Sandboxed web views in a surface
`WebEmbed` component loads sandboxed HTML (no network, no JS bridge to tools) for maps/visuals. Planned; use `Image` with a screenshot or chart PNG meanwhile.

### 418 Grow the catalog
A new component is native code: add it through the app self-update pipeline (#401-#404), then regenerate this skill's component list.
