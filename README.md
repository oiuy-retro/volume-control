# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume. One full 360° rotation changes `STREAM_MUSIC` by 10% of the stream maximum. Clockwise raises volume; counterclockwise lowers it. Fractional turns apply proportionally.

## Experimental build: global output effect (0.1.3)

This branch is a test of the old "global output mix" audio effect route. In this build the dial does not change Android media volume at all. It asks an audio effect attached to audio session 0 to attenuate (turn down) the signal by a number of decibels. The sections further down describe the regular volume dial on `main`, which this build does not use.

- Session 0 effects are deprecated by Android ("attaching insert effects (equalizer, bass boost, virtualizer) to the global audio output mix by use of session 0 is deprecated", https://developer.android.com/reference/android/media/audiofx/AudioEffect). Whether a device still applies them, and to which apps and outputs, depends on the manufacturer's audio framework. Nothing in this app can verify that you actually hear a change.
- Preferred backend (Android 9 and later): `DynamicsProcessing` on session 0 with every stage off except the per-channel input gain, which is a broadband gain in dB (https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing). The value shown as "Effect API reports" is read back with `getInputGainByChannelIndex`, which queries the effect engine.
- Fallback: `Equalizer` on session 0 with every band set to the same negative level, limited to the band range the device reports. This is equal band reduction, not a guaranteed flat gain; band filter shapes can leave some ripple. Read-back uses `getBandLevel`.
- If neither can be attached, the app says so and stays off. It never falls back to changing media volume.
- Range: 0 dB down to −30 dB (or the Equalizer's reported minimum if that is shallower). No boost. Values move in 0.1 dB steps; one full turn is 6 dB; a haptic click marks each whole dB. Hard stops at 0 dB and at the limit.
- Starts neutral every time: effect off, 0 dB. Nothing is saved. "Enable experiment" attaches the effect at 0 dB, "Reset to 0 dB" returns to neutral, "Disable and release" sets 0 dB, disables, and releases the effect.
- The effect is held by the app process, so you can switch to a music app and keep it. If Android stops the app in the background the effect is released; the app then shows it as off when you return.
- Another app can take control of the same effect. The app shows "Control: lost to another app" when Android reports that.
- Uses only the normal install-time permission `MODIFY_AUDIO_SETTINGS`. No recording, audio capture, adb, root, or hidden APIs.
- The widget in this build is read-only: it shows the requested attenuation or "Off" and opens the app when tapped. It has no +/- controls and never changes media volume.
- Known reports from other apps: Wavelet's "Legacy mode" uses the same session 0 route and "might work, depending on your device manufacturer's audio framework implementation" (https://pittvandewitt.github.io/Wavelet/Settings/). Some users report needing to turn off LE Audio on recent Samsung phones for Wavelet over Bluetooth; that is a report, not something this app checks.

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

- The home-screen widget cannot implement continuous dial rotation; only tap and ±10% actions are supported.
- Media stream step count is device-dependent (often around 15 steps), so a 10% turn may map to one or two integer stream indices.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `0.1.3` (experimental global output effect build)
- versionCode: `13`
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
