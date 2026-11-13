# FOSS Material 3 App Lock For Android Phones

An on-device Android app locker built with Kotlin, Jetpack Compose, and Material 3. It lets you select installed apps to protect with a PIN or your device biometric.

## Features

- Set a four-digit PIN, or choose six digits.
- Unlock with the PIN or Android's biometric prompt (fingerprint where supported and enrolled).
- Configure two recovery questions and reset the PIN after verifying both answers. Existing installs can enroll recovery questions after confirming their current PIN.
- Choose dark or light appearance.
- Search installed launcher apps and select which ones App Lock monitors.
- Use an Accessibility Service that observes window changes only; it does not request screen-content access.
- Keep PIN and recovery-answer verifiers on-device using an Android Keystore HMAC key.
- No Internet permission; Android backup is disabled.
- Raster PNG launcher icon and supplied dark/light design references.

## Requirements

- JDK 21 (JDK 17 or newer is supported)
- Gradle 8.13
- Android SDK Platform 36 and Android Build Tools 35 or newer
- Android 8.0 / API 26 or newer

## Build

From the project root, with `JAVA_HOME` pointing to the JDK and `ANDROID_HOME` pointing to the Android SDK:

```sh
gradle --no-daemon assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install over an existing debug build with ADB:

```sh
adb install --streaming -r app/build/outputs/apk/debug/app-debug.apk
```

Open App Lock, create a PIN, select apps, then enable **App Lock app monitoring** in Android Accessibility settings. A fingerprint or other device biometric must already be enrolled for biometric unlock to be available.

## Privacy and platform behavior

App Lock keeps its settings on the device. The Accessibility Service needs to know which app is in the foreground to show a lock screen; Android and device manufacturers can restrict background activity launches or stop services to save battery. Behavior can therefore vary between Android versions and phone manufacturers. The app has been built and installed on a device, but broad device and OS-version coverage has not been validated. Test on the devices you intend to use before relying on it for sensitive protection.

Google Play distribution requires completion of its Accessibility API declaration and policy review. This project is not a replacement for Android's built-in device security or per-app security features.

## License

This project is licensed under the MIT License; see [LICENSE](LICENSE).
