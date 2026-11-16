# AeroVector Studio — Native Android app

The launcher is implemented with native Android views and custom Android Canvas rendering; it no longer loads the HTML app in a WebView. The original HTML file remains in `app/src/main/assets` as a reference, but is not used by the Android app.

## Native features

- Flight phase presets (rest, flight, landing)
- Thrust, lift, drag, weight, and braking controls
- Live force, speed, altitude, displacement, and acceleration readouts
- Canvas-rendered aircraft, force arrows, runway, and velocity history graph
- Drag-to-orbit-style scene rotation, vector visibility controls, zoom controls, and immersive fullscreen

## Build APK

Open this repository in Android Studio and run **Build > Build APK(s)**, or run:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
