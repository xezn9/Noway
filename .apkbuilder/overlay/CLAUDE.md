# HyperSpring: notas para Claude Code

Módulo LSPosed para HyperOS (antes HyperTweak). Añade animaciones al launcher y a la interfaz del sistema, y cristal líquido al centro de control. Lo mantiene **olilio3** (github.com/olilio3/HyperSpring), que está aprendiendo Java. **Responde siempre en español** y explica lo que cambias de forma sencilla.

> **Sesión anterior (16-17/11/2026, versiones 2.9.2 a 2.12.1):** lee `docs/CONTEXTO_IA.md`. Resume lo hecho, cómo compilar sin SDK y lo que quedó pendiente.

## Lo básico

- **Paquete:** `com.icehome.up`. **No cambiarlo nunca**: así se actualiza encima de versiones anteriores y se conservan los ajustes.
- **API de Xposed:** dos, con el mismo código (desde la 2.0.0):
  - **libxposed 102** (`io.github.libxposed.api`): entrada `ModernEntry`, apuntada en `app/src/main/resources/META-INF/xposed/` (`java_init.list`, `module.prop`, `scope.list`).
  - **Clásica 93** (`de.robv.android.xposed`): entrada `LegacyEntry`, apuntada en `app/src/main/assets/xposed_init`.
  - Las dos llaman a `MainHook.onPackage`. Los hooks **nunca** usan esas API directamente: pasan por la fachada `xp/` (`XposedBridge`, `XposedHelpers`, `XC_MethodHook` propios, con los nombres de siempre), que reenvía a `xp/ModernEngine` o `xp/LegacyEngine`. Un módulo cargado con la 102 no puede tocar `de.robv`.
  - Si hace falta algo nuevo de Xposed, añádelo a `xp/Engine` y a los dos motores.
- **Ámbitos:** `com.miui.home` o `com.mi.android.globallauncher` (launcher de HyperOS o POCO Launcher; ver `Launchers.java`), `com.android.systemui` y `miui.systemui.plugin` (centro de control y volumen), `com.miui.screenshot`.
- **Launchers:** usa siempre `Launchers` (`is`, `current`, `kashiFeatures`) en vez de escribir `"com.miui.home"`. POCO Launcher tiene las mismas clases `com.miui.home.*`, pero no la pila de widgets ni el redimensionado de carpetas de Kashi.
- **Móvil de pruebas:** Redmi 14C (Helio G81 Ultra, Mali-G52), HyperOS 3, Android 16, Vector 2.2 (API 102), con los mods de Kashi (HyperOS Launcher Mod 7.5 e iOS Control Center). Si está conectado por adb se puede instalar y leer el registro (`adb logcat -s HyperSpring` con la 102, `VectorLegacyBridge` con la 93); lo visual lo valida el usuario.
- **Versión:** cambia a la vez `Version.java` (`NAME`, `CODE`) y `app/build.gradle.kts` (`versionName`, `versionCode`). Añade la entrada en `CHANGELOG.md` y la versión en los dos README.

## Estructura (`app/src/main/java/com/icehome/up/`)

- **Launcher:**
  - `WindowAnim.java`: hooks de la animación de ventanas de MIUI. Decide qué animaciones llevan genio (`OPEN_FROM_HOME`, `CLOSE_TO_HOME`, `CLOSE_TO_DRAG`).
  - `Genie.java`: sesión del genio (estado, capturas, física de lámpara, forma en `shape()`).
  - `GlRenderer.java`: OpenGL, shaders del genio (`VERTEX`, `FRAGMENT_*`).
  - `RecentsCurtain.java`: tarjetas de recientes que se doblan (AGSL).
  - `FolderMorph`, `GlassFolder`, `StackSwipe`, `IconPress`, `WallpaperZoom`, `Springs`: el resto de efectos.
  - `GlassDock.java` (en pruebas), `UnlockZoom.java`.
  - `Exclude.java`: apps sin genio (y sin telón). Un ajuste de texto (`icehome_exclude`, paquetes separados por comas) fuera de `Tune.ITEMS`; `WindowAnim.onFrame` lo mira al empezar cada animación.
- **Interfaz del sistema:**
  - `telon/`: transición de cortina y modo seguro de SystemUI.
  - `cc/CcGlass.java` + `cc/GlassShader.java`: cristal del centro de control (y del volumen). Tiene la captura del fondo que comparten todos los cristales de SystemUI.
- **App de ajustes:**
  - `SettingsActivity.java`: toda la interfaz, sin XML.
  - `I18n.java`: textos en español e inglés. Todo texto visible pasa por `I18n.t(es, en)`.
  - `Tune.java` / `telon/Tune.java` / `cc/Tune.java`: ajustes y sus valores por defecto. La tabla `ITEMS` es la fuente de verdad.
  - `PrefsProvider.java`: guarda los ajustes. `Remote.java`: avisos entre la app y los procesos enganchados.
  - `FeatureWheel.java`: la rueda de funciones de Inicio. `Health.java`: «¿Cómo va mi móvil?». `AutoPause.java`: pausa sola con calor, juegos o ahorro de batería.
  - `Presets.java`: perfiles (`ALL`), estilos de cristal (`GLASS`) y modos de sistema (`MODES`). Cada uno solo toca sus claves (`Presets.keys`). `Backup.java`: exportar e importar ajustes en JSON.
- **Registro:** `Log.java` / `telon/Log.java`. Se ve en la app con **Acerca de → Copiar registro**. Es lo que piden los usuarios en los issues.

## Compilar

- **Con Android SDK:** `./gradlew assembleRelease`.
  - La release usa **R8** (`isMinifyEnabled` + `isShrinkResources`, reglas en `app/proguard-rules.pro`). Si añades una clase que alguien busque **por nombre** (entrada de LSPosed, reflexión sobre clases propias), añade su `-keep`. `mapping.txt` sale en `app/build/outputs/mapping/release/` y CI lo sube como artefacto.
- **Sin SDK ni GitHub** (sesiones en la nube y **Termux** en el móvil): `bash local-build/maven/get-tools.sh` descarga las herramientas en `~/tools` y `bash local-build/maven/build.sh` compila. Con `KEYSTORE=/ruta/hyperspring.jks` firma con la clave de releases (pide la contraseña). En Termux usa el `aapt2` del sistema (`pkg install aapt2`), porque el de apktool es x86_64.
- **CI:** `.github/workflows/build.yml` compila en cada push con `build.sh` y sube los APK.
- **Sin SDK:** `TOOLS=/ruta bash local-build/build.sh`.
  - Necesita JDK 17, `android.jar` (API 35), jadx, apktool y uber-apk-signer.
  - Sale un APK en `local-build/out/`: `HyperSpring.apk` (libxposed 102, lleva `META-INF/xposed`). El legacy (solo API 93) se dejó de publicar en la 3.0.0; `LegacyEntry` sigue en el código.
  - Se compila contra `local-build/xposed-stub` (API 93) y `local-build/libxposed-stub` (copia de libxposed 102). Ninguno va en el APK.
- **Firma, importante:**
  - Hasta la 2.12.x las releases iban firmadas con la clave de depuración de uber-apk-signer (pública). **Desde la 2.13.0 van con la clave privada del usuario**, que vive fuera del repo (`C:\HyperSpring\Keys\`). Compilar una release: `KEYSTORE=/ruta/hyperspring.jks TOOLS=/ruta bash local-build/build.sh` (la contraseña la escribe el usuario).
  - Sin `KEYSTORE` (CI, pruebas, `maven/build.sh`) sale con la clave de depuración: **no se instala encima** de las releases.
  - El actualizador de la app (`Updates.sameSigner`) detecta un APK con otra firma y explica cómo reinstalar.
  - Nunca subas un keystore al repo.
- **Shaders, valídalos siempre antes de compilar:**
  - **AGSL:** `python3 local-build/agsl_check.py <archivo.java>`. Necesita `pip install skia-python`.
  - **GLSL del genio:** `python3 local-build/gl_check.py`. Necesita Mesa; en Windows, usa WSL.

## Cómo trabajar aquí

- **Listar métodos de clases del launcher:** usa `XposedHelpers.declaredMethods(clase)` y `m.getParameterCount()`, no `clase.getDeclaredMethods()`: este falla entero si un solo método nombra una clase que no existe (pasa con `BaseLauncher` del launcher de Kashi).
- **Hooks de métodos muy frecuentes:** sobrescribe `XC_MethodHook.skip(thisObject)` para salir sin crear objetos cuando no hay nada que hacer.
- **`WeakHashMap` con vistas como clave:** el valor nunca debe retener la vista (usa `WeakReference`), y los listeners usan la vista que reciben, no la capturan. Si no, la vista no se libera nunca.
- **`PrefsProvider` solo deja leer a los procesos de `READERS`** (por UID). Si añades un ámbito nuevo que lea ajustes, añádelo ahí.
- **Cada función se instala por separado y en `try/catch`:** si un hook falla, se anota en el registro y lo demás sigue funcionando. Mantén ese patrón.
- **Nunca bloquees ni lances excepciones en un hook de SystemUI o del launcher.** Un fallo repetido activa el modo seguro y apaga esa parte.
- **Hilos:**
  - El genio dibuja en `IceHome-genie` y captura en `IceHome-capture`.
  - El centro de control captura en `HyperSpring-ccglass`.
  - Nada de trabajo pesado en el hilo principal.
- **Hilos nuevos, siempre con `Threads.start`:** un hilo hereda las CPU permitidas de quien lo crea, y durante una animación HyperOS ata el hilo principal a los núcleos grandes. Con `new HandlerThread` en mitad de una animación, el hilo se queda atado a cpu6-7 para siempre (era la causa de los tirones del genio).
- **Medir antes de cambiar:** el registro de cada animación da fotogramas, tirones y tiempos. Para ir más a fondo, una traza de perfetto (`perfetto ... sched freq gfx view -a com.miui.home`) enseña si un hilo espera CPU (estado R) o búfer.
- **Ajustes reales:** depende del APK.
  - Con libxposed (102): en `shared_prefs` de la app, lo normal.
  - Con el APK legacy (93): LSPosed los redirige a `/data/misc/<id>/prefs/com.icehome.up/` (porque `xposedminversion` es 93).
  - Desde la 2.13.0 ya no se importan solos los ajustes del APK legacy ni de la 1.0.x (se quitó `Migrate.java`): hay que exportarlos e importarlos.
  - Si los tocas con root, escribe con `cat >` (conserva dueño y etiqueta SELinux), nunca con `sed -i`.
- **Avisos de la app a los procesos enganchados:** siempre con `Remote.send` (dirigido a cada paquete). Con `sendBroadcast(i, PERMISSION)` el launcher no lo recibe.
- **Listeners de `ViewTreeObserver`:** se pierden si la ventana se quita y se vuelve a poner. Vuelve a registrarlos con `OnAttachStateChangeListener`, como en `CcGlass.watchRoot` y `Skin.track`.
- **Comentarios:** en español, explicando el porqué. El usuario aprende leyéndolos.
- **Vídeos y capturas para el README:** revisa que no salgan datos personales (nombre de la cuenta, red Wi-Fi, notificaciones) antes de publicarlos.
- **Licencia Apache-2.0.** Si incorporas código de otro proyecto o fork, acredítalo en `NOTICE` y en el `CHANGELOG`. Ya hay un fork comunitario con soporte para POCO Launcher: github.com/ToolDroidYT/hyperspring.

## Pendiente

- **Hoja de ruta completa:** `docs/ROADMAP.md` y `docs/ROADMAP.es.md`.
- **De la sesión del 16-17/11:** ver «Pendiente» en `docs/CONTEXTO_IA.md` (confirmar que no hay tirones en las notificaciones de cristal, cambiar los vídeos del README, ver correr el CI…).
- **Recarga en caliente (libxposed 102):** siguiente paso. Hay que implementar `onHotReloading`/`onHotReloaded` en `ModernEntry`: parar los hilos del módulo, quitar listeners y receptores, y volver a instalar los hooks con el código nuevo (`replaceHook`). Ahora devuelve `false` (valor por defecto) y el framework no recarga.
- **Carpetas, pendiente de mirar con el usuario:** desde la 2.0.0 el hook de `BaseLauncher.bindFolderResize` sí se instala (antes fallaba al listar los métodos). No da errores en el registro, pero el redimensionado de carpetas con el dedo no se ha probado a mano.
- **Confirmado en el móvil:** las correcciones de la 1.0.2 funcionan (volumen, centro de control, genio clásico, recientes).
- **Navegación:** no está comprobado qué animación usa MIUI al volver al escritorio con el gesto o el botón de atrás en la última pantalla de una app. El registro anota cada tipo de animación que ve ("animación X por Y" y "sin genio en X"). Pide ese registro antes de añadir tipos nuevos en `WindowAnim.onFrame`.
