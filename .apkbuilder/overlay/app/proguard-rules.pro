# ---------------------------------------------------------------------------------------------
# HyperSpring: reglas de R8 (reducir + ofuscar).
#
# Regla de oro: R8 solo puede cambiar el nombre de lo que NADIE busca por nombre. Aquí buscan por
# nombre tres cosas, y las tres se conservan abajo:
#   1) LSPosed, las dos clases de entrada que están escritas como texto en
#      META-INF/xposed/java_init.list (ModernEntry) y assets/xposed_init (LegacyEntry).
#   2) Android, los componentes del AndroidManifest (SettingsActivity, PrefsProvider, PauseTile).
#      Esos AGP ya los conserva solo, no hace falta escribirlos.
#   3) Nosotros mismos con reflexión. Se ha revisado: toda la reflexión de los hooks apunta a clases
#      de OTRAS apps (launcher, SystemUI) o de Android, nunca a clases de HyperSpring.
# ---------------------------------------------------------------------------------------------

# 1) Entradas de LSPosed (API moderna 102 y API clásica 93).
-keep class com.icehome.up.ModernEntry { *; }
-keep class com.icehome.up.LegacyEntry { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# Las API de Xposed no van dentro del APK (las pone el framework): no avisar de que faltan.
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.**

# Los registros de error (Acerca de > Copiar registro) llevan líneas de código. Se conservan los
# números de línea; los nombres salen ofuscados y se leen con mapping.txt (ver build.yml).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Más reducción: todo en un solo paquete, con permiso para que R8 abra el acceso de lo que une.
-repackageclasses ''
-allowaccessmodification

# Kotlin mete comprobaciones de parámetros no nulos en cada función. Aquí sobran.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}
