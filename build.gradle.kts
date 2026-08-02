plugins {
    // AGP 9 with android.builtInKotlin=true: no separate Kotlin plugin needed.
    id("com.android.application") version "9.3.0" apply false
    // KSP 2.3.4: the version Readium kotlin-toolkit 3.3.0 pairs with
    // Kotlin 2.3.20 + AGP 9 built-in Kotlin (same Kotlin as this project).
    id("com.google.devtools.ksp") version "2.3.4" apply false
    // Compose compiler, matching the Kotlin 2.3.20 built into AGP 9.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
