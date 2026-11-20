# Checkpoint Alerts — Android App Design Notes

## Concept
Modern Android app that checks for law enforcement checkpoints (sobriety + license/driver license checkpoints) and notifies the user on the day one is taking place near them.

## Decisions (from Joshua)
- Start with Tennessee (user's state). Add other states one by one, each with its own data source.
- Alert scope is a USER-SETTABLE option:
  - Whole state, OR
  - My county + surrounding/nearby counties
- Covers both sobriety checkpoints and license checkpoints.
- Checks for new announcements in the background, daily.
- Notification on the day a checkpoint is happening, with location and time.

## Data sources (verified Nov 20, 2026)
- Tennessee law requires agencies to announce sobriety checkpoints in advance. THP publishes dates + exact locations via press releases (picked up by local news: WVLT, Crockett Chronicle, etc.).
- No single official state API/feed. Per-state source handling required; some states ban sobriety checkpoints entirely.
- Aggregators that republish announced TN checkpoints: duiblock.net, duicheckpointlocator.com.
- Example (Fri Nov 20, 2026): Sevier County — Collier Drive at Rainbow Road; Jefferson County — SR 113 near Nina Road.

## Build
- Native Android (Kotlin). Toolchain setup (JDK 17 + Android SDK) delegated to subagent Nov 20, 2026 — pending report.
- Deliverable: APK installable on the user's moto g 2025.

## Toolchain (ready Nov 20, 2026 — test APK built OK)
- JDK: /home/hatch/jdk/jdk-17.0.21.1+2 (Eclipse Temurin 17.0.21.1+2); Gradle 8.7 at /home/hatch/gradle/gradle-8.7; Android SDK at /home/hatch/Android/Sdk (cmdline-tools 17.0, platform-tools 37.0.3, build-tools 34.0.0, android-34). Env: ~/android-env.sh (source it first — sets JAVA_HOME/ANDROID_HOME/PATH and starts the local proxy the VM needs for network).
- Test APK built Nov 20 (gradle assembleDebug, BUILD SUCCESSFUL in ~3 min): ~/workspace/android-test-app/app/build/outputs/apk/debug/app-debug.apk. Full notes: ~/workspace/android-setup.md.
- Still missing: no emulator images (test on physical phone + adb), no release keystore (debug signing only), Kotlin plugin to be added by the app build. Gradle heap capped at 1536 MB, daemon off.
