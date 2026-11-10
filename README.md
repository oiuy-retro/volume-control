# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume. One full 360° rotation changes `STREAM_MUSIC` by 10% of the stream maximum. Clockwise raises volume; counterclockwise lowers it. Fractional turns apply proportionally.

## Diagnostic build 0.1.5 (Samsung fine volume)

Version 0.1.5 (versionCode 18) is a diagnostic build for one question: can this app read and change Samsung's fine media volume, the finer setting that Samsung's own volume bar uses, on the tester's phone? It replaces the earlier gain experiment for diagnosis only. It does not add a fine-step dial; the normal dial is not shown in this build.

What the screen shows:

- Device manufacturer, model, device name, Android version and build name.
- Android's public media volume: current index, minimum (Android 9 and later), maximum, whether it is muted, and the media output type (Android 13 and later).
- Whether the two Samsung methods `semGetFineVolume(int)` and `semSetFineVolume(int, int, int)` exist on `AudioManager`, and the value the getter returns for music. The scale of that value is not assumed; the screen shows the raw number.
- The result of the last test.

What can change volume:

- Only the "Test one fine step quieter" button. Each tap reads the current fine value, and if it is above 0 and media is not muted, asks Samsung's setter for that value minus one, once. It then reads the fine value and the public index again and shows before and after.
- It does not restore the old value, retry, or fall back to normal volume steps.
- Opening the app, rotating the screen, volume changes, output changes and the Refresh button only read values.
- The home-screen widget only shows the percent and opens the app. Its old + and − actions do nothing in this build.

How the methods are used: the app looks them up only when `Build.MANUFACTURER` is `samsung`, using the ordinary public `Class.getMethod` lookup for these two names. It does not use hidden-API exemptions, root, Shizuku, private Binder calls, or a lower target SDK. These methods are not part of a documented Android or Samsung SDK, so a firmware update can remove or block them; the screen reports that as an error. A number that changes on screen is not proof of an audible change.

## Dial behavior

- Gesture tracking is relative to the dial center.
- Volume delta: `(degrees / 360) * 0.10 * maxVolume`.
- Values are clamped to `0..maxVolume`. Rotation that would go below 0% or above 100% is ignored (hard stops); blocked motion does not fire haptics.
- A stationary bezel draws 100 fine ticks (one every 3.6°). The ticks do not rotate with the knob. Every 10th tick (36°) is longer; twelve o'clock is emphasized as the landmark for each 10% volume step (0, 10, 20, …, 100).
- Only the knob and indicator rotate. Indicator angle follows fine volume within the current 10% decade, so the indicator points at twelve o'clock at every 10% landmark.
- Each fine tick is one position: 100 positions per turn, each worth 0.1% of the stream maximum. Volume is applied continuously underneath the ticks.
- Tick crossings are counted from the fine percent (0.1% steps). Finger deltas always update a continuous raw percent. A tiny magnet (±0.1% of each whole percent) may overlay the reported value when raw ends in that zone or a fast frame sweeps across the mark (no finger-speed gate). After snapping once, the magnet will not re-engage until raw moves 0.5% (5 fine ticks) away from that whole.
- Fine tick haptics use the view feedback constant (`SEGMENT_FREQUENT_TICK` on API 34+, `TEXT_HANDLE_MOVE` on API 27+, otherwise `CLOCK_TICK`) plus a light 5 ms / amplitude 65 vibrator one-shot. Whole-percent majors use `LONG_PRESS` plus a 28 ms / amplitude 255 one-shot. At most one pulse per input frame. Fines are rate-limited (~12 ms) and are suppressed for 28 ms after a major so they cannot cut the major short; a major is not dropped because a fine fired recently. Major haptic fires when entering or crossing a major tick (including multi-tick jumps). The app requests the `VIBRATE` permission for this.
- The center readout shows the fine fractional media volume to one decimal place (for example `47.3%`, or `47.0%` while the magnet holds), updating on every tick.
- Crossing a tick also brings up the Android system media-volume panel (`AudioManager.FLAG_SHOW_UI`), rate-limited to about once every 120 ms so rapid ticks do not flood the panel.

## Home-screen widget

The matching widget shows the current media volume percent and a dial face.

Android app widgets cannot receive continuous rotation gestures. On the widget:

- Tap the dial face to open the app.
- Use the + and − controls to change media volume by 10% of the stream max.
- The widget refreshes after those actions and when the system volume changes, when the platform delivers that broadcast.

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

- The 0.1.5 diagnostic build shows only the diagnostic screen. The dial description below applies to the regular builds.

- The home-screen widget cannot implement continuous dial rotation; only tap and ±10% actions are supported.
- Media stream step count is device-dependent (often around 15 steps), so a 10% turn may map to one or two integer stream indices.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `0.1.5` (diagnostic build)
- versionCode: `18`
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
