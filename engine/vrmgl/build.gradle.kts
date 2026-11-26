plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.rishitgoklani.hermes_front_mobile.engine.vrmgl"
    compileSdk { version = release(37) }
    defaultConfig {
        minSdk = 24
        ndk { abiFilters += listOf("arm64-v8a") }   // same as the app
    }
    // libvrmtex: the vendored etcpak ETC2 encoder behind a JNI wrapper, used to build the compressed texture cache.
    ndkVersion = libs.versions.ndk.get()
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = libs.versions.cmake.get() } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// The OpenGL ES 3.0 side of the VRM engine: shaders, GPU upload, texture decoding and the GLSurfaceView. Everything that can be
// tested without a GPU lives in :engine:vrm.
dependencies { api(project(":engine:vrm")); testImplementation(libs.junit) }
