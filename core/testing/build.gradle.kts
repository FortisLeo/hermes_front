plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
}
