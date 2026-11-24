plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
// Pure Kotlin on purpose: no Android, no OpenGL. The renderer in :feature:avatar uploads what this module prepares,
// so parsing, skeleton, posing, animation and spring physics are all unit-testable on the JVM.
dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
