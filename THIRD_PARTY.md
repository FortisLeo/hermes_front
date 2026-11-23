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
| three.js 0.160.0 (+ GLTFLoader, BufferGeometryUtils) | https://github.com/mrdoob/three.js (npm `three@0.160.0`) | MIT | Bundled unmodified in `feature/avatar/src/main/assets/avatar/lib` for the VRM viewer (#423) |
| @pixiv/three-vrm 2.1.0 | https://github.com/pixiv/three-vrm (npm `@pixiv/three-vrm@2.1.0`) | MIT | Bundled unmodified, loads and animates VRM models (#423) |
| @pixiv/three-vrm-animation 2.1.0 | https://github.com/pixiv/three-vrm (npm `@pixiv/three-vrm-animation@2.1.0`) | MIT | Bundled unmodified, plays VRM Animation (.vrma) clips on the avatar |
| androidx.webkit 1.12.1 | Google | Apache-2.0 | WebViewAssetLoader: serves bundled assets and the avatar folder to the viewer |

Besides the two bundled JS libraries above (license files next to them), no source was copied from other projects; everything else is a dependency.
| OpenSourceAvatars registry | https://github.com/ToxSam/open-source-avatars (data/projects.json, data/avatars/*.json) | repo has no licence file; models carry their own (CC0 / CC-BY) | Read at runtime only (catalog listing + model download); nothing is bundled. Catalog downloads are licence-checked by `VrmLicense` before install; user-uploaded files are not |
