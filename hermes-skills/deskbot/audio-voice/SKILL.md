---
name: deskbot-audio-voice
description: "Wake word, speech in/out, barge-in, sound events, recording, audio routing on the Deskbot phone."
version: 1.0.0
author: Deskbot
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [deskbot, android, phone]
    related_skills: [deskbot-media]
---

# Audio and voice

Speaking: `deskbot_tts_say text=…` (queued, sentence by sentence). The app owns the mic; Termux must not open it.

### 121 Wake-word listening
App feature (settings: wake phrase, sensitivity). Until the dedicated detector ships the Voice tab "Talk" button starts a turn. Change the phrase via settings (planned); ask the user which phrase they want and record it in memory.

### 122 Speech to text
App uses on-device/Google recognition with partial results. Language switch and noisy-room tuning are app settings (planned); tell the user the language recognized when asked.

### 123 Text to speech
`deskbot_tts_say`. Voice/speed/pitch live in Android TTS settings: `am start -a com.android.settings.TTS_SETTINGS`; speed `settings put secure tts_default_rate 100..300`, pitch `tts_default_pitch`.

### 124 Barge-in and end-of-speech
App stops speaking when the user says "stop" (barge-in) and detects end of speech from the recognizer. Cancel speech now: say "stop" or call `deskbot_media_key action=stop` for media; for the bot's own TTS use the Voice tab Stop button.

### 125 Sound event detection
Experimental: sample the mic level periodically (planned tool `sound_level`) and alert on spikes. Not implemented in tools yet; say so rather than guessing.

### 126 Record audio
`deskbot_shell cmd="am start -a android.provider.MediaStore.RECORD_SOUND_ACTION"` then tap record/stop via `deskbot_ui_click`. Timed clip in Termux (if Termux:API installed): `termux-microphone-record -d -l 30 -f /sdcard/rec.m4a`. The app owns the mic, so pause listening first.

### 128 Identify music playing
`web_search` after capturing lyrics/title shown on screen: `deskbot_notifications_list` (media notification shows title/artist) or `deskbot_ui_tree` in the player. Say title and artist; never reproduce lyrics.

### 129 Audio routing
Bluetooth/speaker: Sound output switcher via `deskbot_ui_global quick_settings` + `deskbot_ui_click text="Media"`; or `cmd media_session` is limited. Mic mute: `deskbot_mic_mute mute=true|false`.

### 130 Earcons and announcements
Chime: `deskbot_shell cmd="cmd media_session dispatch ..."` is unreliable; use `deskbot_vibrate` for tactile cue and `deskbot_tts_say` for spoken alerts. Alert tones: `am start -a android.intent.action.VIEW -d content://media/internal/audio/media/<id> -t audio/*`.
