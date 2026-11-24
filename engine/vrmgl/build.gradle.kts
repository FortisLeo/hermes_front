plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.rishitgoklani.hermes_front_mobile.engine.vrmgl"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// The OpenGL ES 3.0 side of the VRM engine: shaders, GPU upload, texture decoding and the GLSurfaceView. Everything that can be
// tested without a GPU lives in :engine:vrm.
dependencies { api(project(":engine:vrm")); testImplementation(libs.junit) }
