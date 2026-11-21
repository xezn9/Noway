// AGP 9 trae Kotlin integrado, así que ya no se aplica "org.jetbrains.kotlin.android".
// Se fija aquí la versión de Kotlin (la misma que el plugin de Compose) porque backdrop 2.0.1
// y Compose 1.12 necesitan Kotlin 2.4.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Compose: solo para la app de ajustes (barra, interruptores y deslizadores de cristal).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
