# Cairn

Offline-first Android app for capturing research observations where there is no network. A coordinator publishes a form, collectors fill it in offline, devices reconcile when they reconnect.

Kotlin, Jetpack Compose, Room, Supabase.

## Requirements

- JDK 21 (Temurin)
- Android SDK with platform 37 and build tools 37.0.0
- An emulator or device on API 26 or newer

## Configure

Put the server address and a sign-in email in `local.properties` at the repo root. The file is gitignored.

```
sdk.dir=/path/to/android-sdk
cairn.supabase.url=https://<project-ref>.supabase.co
cairn.supabase.key=sb_publishable_…
cairn.dev.email=you@example.test
```

Use the publishable key, never `service_role`. The email only pre-fills the Sign in screen; the password is typed each time. Without these keys the app still builds, and its Sign in screen reports that it has no server address.

## Run

Gradle needs JDK 21. Homebrew's Gradle otherwise starts a daemon on a JDK that AGP rejects.

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

Start the emulator, install, launch:

```
SDK=/opt/homebrew/share/android-commandlinetools
$SDK/emulator/emulator -avd cairn -no-boot-anim &
$SDK/platform-tools/adb wait-for-device
./gradlew :app:installDebug
$SDK/platform-tools/adb shell am start -n app.cairn/.MainActivity
```

Sign in once with a network connection. After that the app runs offline against its own database.

Third-party assets and their licences are listed in `THIRD-PARTY.md`.
