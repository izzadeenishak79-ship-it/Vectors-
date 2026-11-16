# AeroVector Studio APK wrapper

This repository contains a minimal Android app that loads the provided HTML simulation in a WebView and packages it as an Android APK.

## Build

1. Install Android Studio.
2. Open this folder as an Android project.
3. Let Gradle sync.
4. Build > Build APK.

Or from the command line:

```bash
./gradlew assembleDebug
```

The generated APK will be in:

```bash
app/build/outputs/apk/debug/app-debug.apk
```

## Files

- `app/src/main/assets/index.html` — the simulation UI you provided.
- `app/src/main/java/com/example/aerovector/MainActivity.kt` — loads the HTML in a WebView.
