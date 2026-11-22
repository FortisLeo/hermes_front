# Third-party code and data

Per CLAUDE.md: permissive licenses only, pinned versions, listed here.

| Name | Used for | Version / pin | License | Source |
| --- | --- | --- | --- | --- |
| Vosk (vosk-android) | Offline wake-word / "stop" keyword spotting (restricted grammar) | 0.3.47 | Apache-2.0 | https://github.com/alphacep/vosk-api |
| vosk-model-small-en-us-0.15 | Speech model downloaded at first use, checksum-verified | sha256 `30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498` | Apache-2.0 | https://alphacephei.com/vosk/models |
| JNA | Native bridge required by Vosk | 5.13.0 | Apache-2.0 / LGPL-2.1 (dual) | https://github.com/java-native-access/jna |
| OkHttp / MockWebServer | HTTP client, SSE streaming, test server | 4.12.0 | Apache-2.0 | https://github.com/square/okhttp |
| kotlinx.serialization / coroutines | JSON and concurrency | 1.9.0 / 1.10.2 | Apache-2.0 | https://github.com/Kotlin |
| AndroidX (Compose, CameraX, DataStore, Lifecycle) | UI, camera, settings | see gradle/libs.versions.toml | Apache-2.0 | https://developer.android.com/jetpack |
| MockK, Turbine, JUnit4 | Tests | see gradle/libs.versions.toml | Apache-2.0 / EPL-1.0 | |

No source files were copied from other projects; everything above is consumed as a dependency.
