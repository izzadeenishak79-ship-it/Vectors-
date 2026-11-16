# AeroVector Studio — Native Android app

The launcher uses native Android views and custom Canvas rendering; it no longer loads the HTML app in a WebView. The original HTML file remains in `app/src/main/assets` as a reference, but is not used by the Android app.

## Native features

- Flight phase presets (rest, flight, landing)
- Thrust, lift, drag, weight, and braking controls
- Live force, speed, altitude, displacement, and acceleration readouts
- Canvas-rendered aircraft, force arrows, runway, and velocity history graph
- Scene rotation, vector visibility controls, zoom controls, and immersive fullscreen

## Debug APK

Open this repository in Android Studio and run **Build > Build APK(s)**, or run:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Signed release APK

A release APK is signed only when a private keystore and its credentials are configured. Keep the keystore and passwords private; do not commit them or send them in chat.

### Configure locally

1. Create a signing key once and keep a secure backup. For example, with a JDK installed:

   ```bash
   keytool -genkeypair -v \
     -keystore release-keys/aerovector-release.jks \
     -alias aerovector \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Choose and store the passwords securely. Losing this key may prevent you from publishing app updates under the same Android application ID.

2. Copy `signing.properties.example` to `signing.properties`, then set the real keystore path, store password, alias, and key password. The keystore path can be absolute or relative to the repository root. Both files are ignored by Git.

3. Build the signed APK:

   ```bash
   ./gradlew assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`.

Alternatively, set these environment variables instead of using `signing.properties`:

- `AEROVECTOR_STORE_FILE`
- `AEROVECTOR_STORE_PASSWORD`
- `AEROVECTOR_KEY_ALIAS`
- `AEROVECTOR_KEY_PASSWORD`

Environment variables take precedence over values in the local properties file. The release packaging task checks that the credentials and keystore file are present and fails with a configuration message if they are missing.

The project contains the signing configuration, but no APK or signing key is generated here because that requires your private key and Android build environment.
