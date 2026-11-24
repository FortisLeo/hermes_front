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
| androidx.webkit 1.12.1 | Google | Apache-2.0 | WebViewAssetLoader: serves bundled assets and the avatar folder to the viewer |

Besides the two bundled JS libraries above (license files next to them), no source was copied from other projects; everything else is a dependency.
| OpenSourceAvatars registry | https://github.com/ToxSam/open-source-avatars (data/projects.json, data/avatars/*.json) | repo has no licence file; models carry their own (CC0 / CC-BY) | Read at runtime only (catalog listing + model download); nothing is bundled. Catalog downloads are licence-checked by `VrmLicense` before install; user-uploaded files are not |
| AndroidX Room 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room (androidx.room:room-runtime/ktx/compiler) | Apache-2.0 | Avatar library database (installed avatars) |
| Kotlin Symbol Processing (KSP) 2.3.6 | https://github.com/google/ksp | Apache-2.0 | Build-time annotation processing for Room |
| ZXing core 3.5.3 | https://github.com/zxing/zxing (com.google.zxing:core) | Apache-2.0 | QR / barcode decoding for `scan_code` (Gradle dependency, no source copied) |
| ML Kit text-recognition 16.0.1, object-detection 17.0.2, image-labeling 17.0.9 | https://developers.google.com/ml-kit (com.google.mlkit:*) | ML Kit SDK terms (proprietary SDK, free, models bundled and run fully on device, no network); not open source | OCR (`ocr`, `table_to_csv`, `scan_document`) and object counting (`count_objects`). Gradle dependency only; adds roughly 22 MB per ABI |
| AndroidX CameraX video 1.4.2 | https://developer.android.com/jetpack/androidx/releases/camera | Apache-2.0 | `video_record` (front or back camera clips) |
