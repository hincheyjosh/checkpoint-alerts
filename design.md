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
- Tennessee law requires agencies to announce sobriety checkpoints in advance. THP publishes the full month's list as a PDF on tn.gov: `tn.gov/content/dam/tn/safety/documents/thp-checkpoints/{YYYY}/{MONTH}.pdf` (e.g. `2026/NOVEMBER.pdf`). All 8 districts, DATE / COUNTY / LOCATION / TYPE rows. The app fetches the current month + next month.
- **Tennessee uses ONLY that official list.** v1.0 also read aggregators (duiblock.net, duicheckpointlocator.com) that republish the same announcements; the two copies worded each location differently, so the same checkpoint showed up twice. One official source = one entry per checkpoint. The parser was validated against the real November 2026 PDF (75 rows → 72 checkpoints after dropping 3 seat-belt rows, 0 duplicates), including its wrapped locations and the PDF's own "Sobreity" typo.
- No single official state API/feed. Per-state source handling required; some states ban sobriety checkpoints entirely.
- Example (Fri Nov 20, 2026): Sevier County — Collier Drive at Rainbow Road; Jefferson County — SR 113 near Nina Road.

## v1.1 (Nov 20, 2026)
- Tennessee source switched to the official THP monthly PDF only (above) — fixes the double-reporting Joshua found after installing v1.0.
- Added a notifications on/off switch in Settings (on by default). When off, the list still refreshes daily — it just never notifies.
- PDF parsing via pdfbox-android (PDFTextStripper, sortByPosition); parser unit tests include the real November 2026 PDF text.

## v1.2 (Nov 20, 2026)
- Notifications now warn on BOTH days around a checkpoint: the day before and the day of. Checkpoints usually run around midnight, so a checkpoint dated tomorrow warns today ("tomorrow") and again tomorrow ("today") — one warning per checkpoint per day, via the existing per-day notified set.
- Every screen (Home, Details, Settings) has a "Made with 🌿" footer at the bottom.

## Build
- Native Android (Kotlin). Toolchain setup (JDK 17 + Android SDK) delegated to subagent Nov 20, 2026 — pending report.
- Deliverable: APK installable on the user's moto g 2025.

## Toolchain (ready Nov 20, 2026 — test APK built OK)
- JDK: /home/hatch/jdk/jdk-17.0.21.1+2 (Eclipse Temurin 17.0.21.1+2); Gradle 8.7 at /home/hatch/gradle/gradle-8.7; Android SDK at /home/hatch/Android/Sdk (cmdline-tools 17.0, platform-tools 37.0.3, build-tools 34.0.0, android-34). Env: ~/android-env.sh (source it first — sets JAVA_HOME/ANDROID_HOME/PATH and starts the local proxy the VM needs for network).
- Test APK built Nov 20 (gradle assembleDebug, BUILD SUCCESSFUL in ~3 min): ~/workspace/android-test-app/app/build/outputs/apk/debug/app-debug.apk. Full notes: ~/workspace/android-setup.md.
- Still missing: no emulator images (test on physical phone + adb), no release keystore (debug signing only), Kotlin plugin to be added by the app build. Gradle heap capped at 1536 MB, daemon off.
