# Glass Nav
iOS 26 style home bar + back gesture for Android (Accessibility Service, no root).
Library: io.github.kyant0:backdrop:2.0.0 (Liquid Glass).

1. Open the folder in Android Studio (JDK 21). Let Gradle sync.
2. Run on the phone (Android 13+).
3. Open the app > "Enable the service" > Installed apps > Glass Nav > ON.
4. System > Navigation mode: 3-button, then enable "Cover the 3-button nav bar".

## Build notes
- AGP 9.2.0 + Gradle 9.4.1 + compileSdk 37 (needed by Backdrop 2.0.0 -> Compose 1.12). Use Android Studio with SDK Platform 37 installed.
- Fixed signing: app/glassnav.keystore (alias glassnav, password glassnav123) signs both debug and release.
