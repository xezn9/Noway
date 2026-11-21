plugins {
    id("com.android.application") version "8.9.1" apply false
    // Kotlin y Compose: solo para la app de ajustes (barra, interruptores y deslizadores de cristal).
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
