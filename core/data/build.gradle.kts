plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.rishitgoklani.hermes_front_mobile.core.data"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation(libs.okhttp)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    testImplementation(libs.junit)
}
