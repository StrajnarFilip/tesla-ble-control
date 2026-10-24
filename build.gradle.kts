// Declaring the Kotlin plugins here (with a version) also pins the Kotlin Gradle plugin that
// AGP's built-in Kotlin support uses for the :app module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
}
