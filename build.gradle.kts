buildscript {
    dependencies {
        // AGP 9.5's built-in Kotlin defaults to KGP 2.2.10; use the current Kotlin plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
