# PocketOps AI — Android starter

A Kotlin + Jetpack Compose starter for a read-only Android device audit app.

## Current status (v0.1.0)
- Compose dashboard
- Installed application inventory using PackageManager
- Shizuku provider/API dependency and connection status check
- Claude audit screen placeholder
- No uninstall, disable, delete, or settings-changing action is implemented

## Build
Use JDK 17, Android SDK platform 35, and Gradle 8.9 or a compatible Android build setup.
From the project root:

    gradle assembleDebug

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

If your Termux setup uses a different Gradle installation or Android SDK path,
set `ANDROID_HOME`/`ANDROID_SDK_ROOT` to your installed SDK location.

## Shizuku
Install and start Shizuku separately, then grant PocketOps permission from inside
the app. Shizuku's ADB-backed permissions are limited and vary by Android version.
The starter only checks connection/permission; it does not run privileged commands.

## Claude backend
See `server/README.md`. The backend is a development placeholder, not production
ready. Keep the API key on the server. Do not paste it into Kotlin source, Gradle
files, GitHub, or the APK.

## Safety and privacy
- App inventory stays local unless the user explicitly initiates an AI audit.
- The planned audit sends only app label, package name, and system/user classification.
- Do not send personal file contents, contacts, messages, photos, or location to Claude.
- Every future device-changing action must be independently validated and approved
  in the app before execution.
- Core phone, launcher, notification, Bluetooth, GPS, mobile data, and Google
  services components must be protected by a local denylist.
