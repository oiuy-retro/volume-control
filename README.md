# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume (`STREAM_MUSIC`). The dial shows the actual Android media volume level, for example "Level 7 of 15" and "47% volume level".

## How Android media volume works

Android media volume is not continuous. It is a whole-number level from 0 to a maximum that the phone maker chooses (`AudioManager.getStreamMaxVolume`). Stock Android defaults to 15 levels; many phones use a different number. Apps cannot set anything in between two levels for the global media stream. How loud each level sounds is decided by the phone's volume curves, so the percentage shown is the position in the level range (level / max), not measured loudness.

With Bluetooth speakers and headphones the steps can feel coarser or uneven. When the speaker supports Bluetooth absolute volume, Android sends the speaker a volume value scaled from the phone's whole level (for classic Bluetooth AVRCP that value is 0 to 127), so there are still only as many distinct values as phone levels. The speaker's own firmware then applies its own gain steps and curve, and the phone and speaker can briefly disagree while they sync. Without absolute volume the phone scales the audio itself, still in whole levels. What applies depends on the phone, the speaker, and the connection type.

## Dial behavior

- Gesture tracking is relative to the dial center. Clockwise raises volume; counterclockwise lowers it.
- Turning is deliberately slow: one full 360° turn covers about 10% of the level range (`(degrees / 360) * 0.10 * max` levels). Rotation accumulates internally between levels, but that in-between position is never shown and does not produce feedback, because the audio does not change until the next whole level.
- A new level is requested once the turn passes halfway to it plus a small dead band (30° of rotation, at most a quarter level), so a wobble at a boundary does not flip the volume back and forth.
- After each request the app reads the level back from Android and shows that. If Android refuses or caps a level (for example a hearing-safety limit), the dial stops there for the rest of that gesture and tries again on the next drag.
- Hard stops at level 0 and the maximum level. Blocked motion does nothing.
- The stationary bezel has one mark per actual level, read from the system (not hardcoded). Twelve o'clock is both level 0 and the maximum. The knob indicator and progress ring point at the actual level and only move when it changes.
- Each actual level change plays one firm haptic detent (`LONG_PRESS` plus a 28 ms / amplitude 255 vibrator one-shot). Several levels in one input frame play one pulse, and back-to-back pulses are limited to about one per 12 ms. The app requests the `VIBRATE` permission for this.
- A level change also brings up the Android system media-volume panel (`AudioManager.FLAG_SHOW_UI`), rate-limited to about once every 120 ms.
- Changes made elsewhere (volume keys, other apps, a Bluetooth device reporting its volume) update the readout, and the dial continues from the new level.

## Home-screen widget

The matching widget shows the actual level (for example `7/15`) and its percentage of the level range (for example `47% level`).

Android app widgets cannot receive continuous rotation gestures. On the widget:

- Tap the dial face to open the app.
- Use the + and − controls to move media volume by about 10% of the level range, in whole levels (rounded, at least one level; for 15 levels that is 2 levels per press).
- The widget refreshes after those actions, after each dial gesture, and when the system volume changes, when the platform delivers that broadcast.

### Adding the widget

1. Long-press the home screen.
2. Choose Widgets.
3. Find Volume Dial under Volume Control.
4. Place it on the home screen.

## Install

### Debug APK

Build a debug APK (unsigned debug keystore) and sideload it:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

Create `keystore.properties` from `keystore.properties.example`, then:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Do not commit `keystore.properties`, `*.jks`, or built APKs.

## Building from source

Requirements:

- JDK 21
- Android SDK with platforms android-36 and android-37 and build-tools 36.0.0
- A `local.properties` file with `sdk.dir=...` (gitignored)

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

See [docs/BUILDING.md](docs/BUILDING.md) for concrete steps used on the project build box and for release signing.

## Known limitations

- Volume changes in whole Android levels only. The number of levels is device-dependent, and this app cannot make the global media output smoother than that.
- Bluetooth speakers may add their own coarser or uneven steps on top of the phone's levels.
- The percentage is position in the level range, not loudness.
- The home-screen widget cannot implement continuous dial rotation; only tap and +/− actions are supported.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `0.1.2`
- versionCode: `12`
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
