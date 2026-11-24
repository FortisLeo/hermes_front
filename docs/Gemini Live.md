# Gemini Live backend

An optional voice backend that talks to Gemini directly over one streaming WebSocket instead of going wake word -> STT -> Hermes -> TTS. Hermes stays the default and the fallback; Settings > "Gemini Live voice" switches it. The choice is read each time the voice loop starts, and flipping it restarts a running loop.

## Flow
wake word (Vosk) releases the mic -> `RunLiveSession` opens `GeminiLiveClient` -> mic PCM (16 kHz) goes up, speech (24 kHz) streams back into `AudioTrackLiveOutput` and plays as it arrives -> tool calls run on the phone through the same capability registry the bridge uses -> after silence (default 25 s) or `end_conversation` the loop returns to waiting for the wake word.

## Where things live
| Piece | Module |
| --- | --- |
| `LiveSession`, `LiveAudioIn/Out` ports, `RunLiveSession`, `LiveToolBridge`, `LivePrompt`, `PcmLevel` | `:core:domain` (`domain/live`) |
| `LiveConversationLoop`, `VoiceLoop`, `SwitchingVoiceLoop` | `:core:domain` (`domain/voice`) |
| `GeminiLiveProtocol` (JSON codec), `GeminiLiveClient` (OkHttp WebSocket) | `:core:network` (`data/live`) |
| `AudioRecordLiveInput`, `AudioTrackLiveOutput`, `LiveSettingsStore` | `:core:data` (`data/live`) |
| Settings section | `:feature:settings` (`LiveSettingsViewModel`) |

## Decisions
- **Tools.** Only a hot set (`LivePrompt.HOT_TOOLS`, ~35) is declared; every declaration costs prompt tokens each turn. `end_conversation` and `look` are built in. Calls obey the kill switch.
- **Vision.** No continuous video (sessions with video are capped at 2 minutes). The model calls `look` (front or back camera) and one JPEG is sent.
- **Echo.** Half-duplex by default: the mic is muted while the bot talks plus a 350 ms tail, so it cannot interrupt itself on the phone speaker. Turn it off in Settings to allow barge-in (relies on the system echo canceller).
- **Long sessions.** Context-window compression and session resumption are on; a clean close with a resumption handle reconnects up to 3 times.
- **Lip sync.** The mouth follows the loudness of the Live audio (`PcmLevel`), merged with the TTS mouth.
- **Secrets.** The API key lives in DataStore (`live_settings`) only. It is placed in the WebSocket URL for the connection and redacted from error text; never log the URL.

## Facts to re-verify on the device
The model id (`gemini-3.8-live`), the voices, and the exact setup fields come from Google's docs as read on 2026-11-25. If the server rejects the setup, the error text from the close frame is shown in the Error state and logged (`DeskbotLive`).

## Debug > API calls
Debug has an "API calls" tab listing every exchange, newest first, with source filters (Hermes, Gemini Live, Bridge), expandable request and response, Copy shown, and Clear log.
- **Hermes:** each chat request body and the full streamed reply (images collapsed to a size marker).
- **Gemini Live:** the session open (setup and confirmation), each tool call with its result, one entry per spoken turn (what was heard, what was said), camera frames, close and error reasons, and one running audio-volume entry per connection (audio is never logged per chunk).
- **Bridge:** every call Hermes makes to the phone, with arguments and result.
The log is in memory only (300 entries, 4,000 characters per field), so it is empty after the app restarts. Keys and tokens are masked (`ApiRedactor`) and long base64 payloads are collapsed.
