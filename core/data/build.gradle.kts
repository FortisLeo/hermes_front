plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.rishitgoklani.hermes_front_mobile.core.data"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 24 }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// /tmp can be full on the dev machine; tests (Robolectric, temp files) use a folder inside the build dir instead.
tasks.withType<Test>().configureEach {
    val tmp = layout.buildDirectory.dir("test-tmp").get().asFile
    doFirst { tmp.mkdirs() }
    systemProperty("java.io.tmpdir", tmp.path)
}
