# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume. One full 360° rotation changes `STREAM_MUSIC` by 10% of the stream maximum. Clockwise raises volume; counterclockwise lowers it. Fractional turns apply proportionally.

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
- The app shows the real Android level under the dial. It no longer calls the system-volume UI on every tick, avoiding another vendor volume-update path. Haptics and magnet behavior are unchanged.

## Fine volume between Android levels

Android media volume only has whole levels (often 15). The hybrid mode requests positions between two levels by setting the next level up and asking a global audio effect to attenuate by the matching number of decibels. It uses the device volume curve when usable; otherwise it uses an explicitly labeled 2.5 dB-per-step estimate. This is an experiment, not a guarantee of smooth audible output, especially over Bluetooth.

The readout separates **requested gain** from **API read-back** and shows effect attachment, enabled state, control, errors, and backend attempts. Matching API values prove only that the API accepted the parameter, not that audio on the current route passed through the effect. If an effect is unavailable, disabled, or not controlled, dial changes use whole Android levels; if a cut fails confirmation, an upward change to the upper level is held rather than made without its cut.

Own stream changes keep the raw finger position while Android settles. The cut is force-written after a stream write, on volume broadcasts, on resume/output changes, and at four finite delayed checks (80, 250, 600, 1150 ms). There is no volume-writing observer or perpetual retry loop. Deferred downward writes retain a compensating cut until the lower index is read back. A clamped/unsettled write is reported after about a second (or finger release), without automatically retrying the stream or removing the cut. Hardware-button changes retain the cut and rebase the dial; widget +/- explicitly clears it.

### Step calibration (per output)

Many outputs, especially Bluetooth speakers using absolute volume, do not tell Android how loud each level is. The app then assumes 2.5 dB per level, which is usually wrong, so the in-between positions feel uneven and the rest of the real step lands as a jump. Calibration measures the real step sizes by ear.

1. Play steady music on the output you want to calibrate. Set Android volume to a comfortable level (at least level 2). The app will not raise it for you.
2. Tap **Calibrate steps for this output**. You start on **B**: the current level N+1 with a cut C. The starting cut is at least as deep as the one already playing, so starting never gets louder.
3. Tap **Play A / Play B** to switch. A is the level below (N) with no cut. B is level N+1 with cut C.
4. Turn the dial to change C in 0.5 dB detents (one whole-percent haptic per 0.5 dB). Clockwise means less cut. The dial stops at 0 dB and at the effect's floor.
5. When A and B sound equally loud, tap **Save this step**. That stores the step size for N → N+1.
6. Optionally use **Lower pair** / **Higher pair (louder)** to calibrate more pairs, then **Done**.

Saved steps are kept per output (device type plus Bluetooth address or product name, and the number of Android levels). Pairs you did not calibrate use the nearest calibrated value; with one saved pair every step uses that size. The readout then says "calibrated N of 14 steps" (for 15 levels; mute to level 1 cannot be matched by ear and uses a fixed fade). **Reset this output** deletes the saved steps for the current output. Calibrated steps take priority over the curve Android reports.

If B is still louder with C at the effect's floor, the real step is bigger than the effect can cut. The app says so; that step will still jump by the difference.

Switching uses a safe order: A → B applies and confirms the cut before raising the level; B → A lowers the level first and removes the cut afterwards. On Bluetooth the cut is kept for about 350 ms after a lower level is read back, because the speaker may change its own volume later than the phone reports. That window is an estimate, not measured per speaker. If something else changes Android volume during calibration, calibration stops without saving.

### Boundary timing

When the dial crosses up into the next Android level, the deeper cut for the new level is confirmed before the level is raised, and it is kept while the level change is still pending. If Android or the speaker is slow, this can be briefly quieter, never louder. When crossing down on Bluetooth, the cut is computed as if the old, higher level were still playing for about 350 ms, then relaxed.

### Bluetooth diagnostic

1. Start music on the Bluetooth speaker, at a safe Android level. Open the app and note the actual Android index.
2. If needed, tap **Enable / reattach effect** while that route is playing. Reattaching releases the old cut briefly and can make sound louder; it does not change the Android index.
3. Tap **Test effect at fixed Android level: −12 dB**. The dial locks and the app stops all stream writes. Hardware buttons and other apps can still change the level.
4. Toggle **Test 0 dB (louder)** and **Test −12 dB**. The negative test is clamped to the backend floor. Compare the same playing audio and check requested versus API read-back, enabled/control state and errors.
5. If both API values change but the sound does not, the global effect is not audibly reaching that route; fine-volume math alone cannot fix that. Send the diagnostic text and route/player details. This does not prove which vendor/offload/Bluetooth path bypassed it.
6. **End test** retains the last cut to avoid an automatic 12 dB rise. Turning the dial resumes hybrid control.

Session 0 is deprecated and vendor-dependent. Another app may take control. API confirmation cannot detect an audio route bypassing the effect. Automatic route detection uses available output callbacks and route checks on volume events/resume; older Android versions expose connected devices rather than the exact media route. Manual reattach may be needed after changing outputs or starting a player.

If the process dies, Android releases the cut. Normal hybrid output can return to the upper level (usually up to one step louder); after the diagnostic or a retained deep cut the rise may be much larger, up to the current cut. Stream/effect writes are not atomic, so brief vendor transients cannot be ruled out. Do not test at an unsafe volume.

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
./gradlew --no-configuration-cache :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

See [docs/BUILDING.md](docs/BUILDING.md) for concrete steps used on the project build box and for release signing.

## Known limitations

- The home-screen widget cannot implement continuous dial rotation; only tap and ±10% actions are supported.
- Media stream step count is device-dependent (often around 15 steps), so a 10% turn may map to one or two integer stream indices.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `0.1.4`
- versionCode: `17` (planned `v0.1.4-pre.3`; not device-verified)
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
