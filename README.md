# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume. One full 360° rotation changes `STREAM_MUSIC` by 10% of the stream maximum. Clockwise raises volume; counterclockwise lowers it. Fractional turns apply proportionally.

## Dial behavior

- Gesture tracking is relative to the dial center.
- Volume delta: `(degrees / 360) * 0.10 * maxVolume`.
- Values are clamped to `0..maxVolume`. Rotation that would go below 0% or above 100% is ignored (hard stops); blocked motion does not fire haptics.
- A stationary bezel draws 100 fine ticks (one every 3.6°). The ticks do not rotate with the knob. Every 10th tick (36°) is longer; twelve o'clock is emphasized as the landmark for each 10% volume step (0, 10, 20, …, 100).
- Only the knob and indicator rotate. Indicator angle follows fine volume within the current 10% decade, so the indicator points at twelve o'clock at every 10% landmark.
- Each fine tick is one position: 100 positions per turn, each worth 0.1% of the stream maximum. Volume is applied continuously underneath the ticks.
- Tick crossings are counted from the fine percent (0.1% steps). A light haptic fires when a tick is crossed. At most one tick fires per input frame and ticks are rate-limited to one every 12 ms.
- Haptics use the view feedback constant (`SEGMENT_FREQUENT_TICK` on API 34+, `TEXT_HANDLE_MOVE` on API 27+, otherwise `CLOCK_TICK`) together with a short vibrator effect (`PRIMITIVE_LOW_TICK` where supported on API 31+, otherwise a 5 ms one-shot). The app requests the `VIBRATE` permission for this.
- The center readout shows the fine fractional media volume to one decimal place (for example `47.3%`), updating on every tick.
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

- The home-screen widget cannot implement continuous dial rotation; only tap and ±10% actions are supported.
- Media stream step count is device-dependent (often around 15 steps), so a 10% turn may map to one or two integer stream indices.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `0.1.1`
- versionCode: `4`
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
