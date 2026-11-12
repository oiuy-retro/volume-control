# Volume Control

A Material 3 Expressive Android app with a large centered dial that changes media volume. The dial face has 50 tick marks per turn, and every tick changes the volume by one unit. On a Samsung phone with 15 media steps that is 150 fine units, so the full range is 3 turns. Clockwise raises volume; counterclockwise lowers it.

## Samsung fine volume

On Samsung phones the dial drives Samsung's fine media volume, the finer setting that Samsung's own volume bar uses, instead of Android's whole volume steps.

- Fine range is Android's media maximum times 10. On a phone with 15 media steps that is 0 to 150. The app computes it from the phone; it is not fixed in the code.
- Each tick on the dial is one fine unit and one light haptic tick. One full turn is 50 fine units, with a strong tick at every full turn (0, 50, 100, 150). Number of turns = fine maximum / 50, so 3 turns at 150.
- The app writes a new fine value only when the whole fine unit under the dial changes, at most about once every 30 ms while dragging, always the latest value. It never uses Android's whole-step setter in this mode, because that would round the value to a whole step.
- Opening the app, returning to it, and volume changes from elsewhere only read the fine value to move the dial.
- The widget + and − buttons change the fine value by 10% of the range (15 units on a 15-step phone). The widget percent comes from the fine value.
- A line under the dial shows the mode, for example `Fine volume: Samsung (73 / 150)`.
- The system volume panel is not opened from the dial in this mode.

Fallback: on other phones, when Samsung's methods are missing, or when a fine read or write fails, the app uses Android's normal whole volume steps for the rest of that session and the line under the dial says so and why.

Caveat: Samsung's methods (`semGetFineVolume(int)` and `semSetFineVolume(int, int, int)`) are not part of a documented Android or Samsung SDK. The app looks them up with the ordinary `Class.getMethod` call and does not use hidden-API workarounds, root, Shizuku or audio effects. A Samsung update could remove or block them; the app then falls back as described. Verified on a Galaxy S24+ (SM-S926U) running Android 17 (API 37), build CP2A.260605.016.S926USQU6EZIF, with the 0.1.5-pre.1 diagnostic: both methods were found, the fine value ran from 150 to 0, and setting one unit lower worked. The fine dial itself (0.1.5-pre.2, the same code as 1.0.0 apart from the version number) was then tested on the same phone.

## Volume buttons

Volume Control can take over how far the phone's volume up and volume down buttons move media volume, on Samsung phones with fine volume.

- Pick a step from 1 to 15 fine units per press in the card under the dial. The label shows it against the phone's fine maximum, for example `1 of 150 per press`. The default is 1, the same size as one dial tick.
- This uses an accessibility service called Volume Control buttons. Turn it on in Settings > Accessibility (tapping the card's on/off row when it is off opens that screen). Android shows its usual accessibility warning when you turn it on. The service reads only the volume up and volume down buttons. It asks for no accessibility events and cannot read anything on screen, other keys, or what you type.
- Fine steps work only while the screen is on. The app treats the screen as on only when Android reports it is interactive and the display is fully on, so Always On Display and doze count as off. It checks this on every button press.
- Key filtering is switched on only while it is needed. The service starts with it off and turns it on only while the screen is fully on, the card's switch is on, and Samsung fine volume is available. It turns it off again when the screen turns off, when the switch is turned off, or when fine volume is missing. It watches the screen-off, screen-on and unlock broadcasts and display state changes, and it rechecks on every key. On the Galaxy S24+, having key filtering on with the screen off broke the volume buttons, whatever the service did with the presses, so with the screen off the service now does not ask for the buttons at all and Android handles them as usual. Any button press that still reaches the service while filtering should be off goes straight back to Android.
- If the volume buttons still misbehave with the screen off, turn off Volume Control buttons in Settings > Accessibility. The dial and widget keep working without it.
- Each press reads Samsung's fine value, moves it by the step, keeps it between 0 and the fine maximum, and writes it with Samsung's fine setter. It asks Samsung to show its volume panel on each write. Holding a button keeps stepping: Android's own key repeats are used when it sends them, otherwise the app repeats after about 0.5 s and then about every 50 ms.
- Samsung's Separate app sound: if an app is playing on a different output than the rest of your media, for example a browser video on the phone speaker while music plays over Bluetooth, the presses go to Samsung's normal steps, so Samsung decides which output they change. At each new press the app checks which outputs media is playing on, using Android's public playback list, and hands the press back if media is playing on two or more outputs, or on an output that is not the default media output. Notification, touch and alarm sounds are not counted. This needs Android 12 or later (Android 13 or later to know the default output). A paused video may still count as playing if the app keeps its player open, and the app cannot tell which app is in focus. Once a press is decided, its repeats keep the same decision.
- With the screen on, the buttons go back to Android's normal behavior when the card's switch is off, on phones without Samsung fine volume, when a fine read or write fails, and during a call, while ringing, or while a voice or video call app is active.
- While it is on and the screen is on, the buttons change media volume even when nothing is playing (Android would otherwise change ring volume in that case on some settings).
- The switch and step are stored on the phone in the app's SharedPreferences.

## Dial behavior

- Gesture tracking is relative to the dial center.
- Volume delta: `(degrees / 360) * ticksPerTurn` units. In Samsung fine mode ticksPerTurn is 50 fine units (7.2° per tick). In Android-steps mode it is 5 whole steps (72° per tick), so every mark is a real step there too and the number of turns to cover the range is about the same.
- A stationary bezel draws one mark per tick (50 in fine mode, 5 in Android-steps mode). Twelve o'clock is emphasized; in fine mode every 5th mark is longer. The marks do not rotate with the knob.
- Only the knob and indicator rotate. The indicator shows the position within the current turn and points at twelve o'clock at each full turn. The arc around the dial shows progress over the whole range. The dial stops at 0 and at the maximum.
- The volume written is the whole unit under the dial, so the value changes exactly as a tick passes.
- A small magnet (±0.1 tick) can hold the drawn indicator on a tick when the finger stops close to it or a fast frame sweeps across it. After snapping, it will not snap again until the finger has moved 2 ticks (2 fine units, 14.4°) away from that tick. The magnet only affects drawing, never the value written.
- Tick haptics use the view feedback constant (`SEGMENT_FREQUENT_TICK` on API 34+, `TEXT_HANDLE_MOVE` on API 27+, otherwise `CLOCK_TICK`) plus a light 5 ms / amplitude 65 vibrator one-shot. Full-turn majors use `LONG_PRESS` plus a 28 ms / amplitude 255 one-shot. At most one pulse per input frame. Light ticks are rate-limited (~12 ms) and are suppressed for 28 ms after a major so they cannot cut the major short. The app requests the `VIBRATE` permission for this.
- The center readout shows the volume as a percent to one decimal place, updating on every tick (one fine tick is about 0.7% at 150 units).
- In Android-steps mode, crossing a tick also brings up the Android system media-volume panel (`AudioManager.FLAG_SHOW_UI`), rate-limited to about once every 120 ms. In Samsung fine mode the panel is not opened, and haptic ticks follow fine units as described above.

## Home-screen widget

The matching widget shows the current media volume percent (from the Samsung fine value when that is active) and a dial face.

Android app widgets cannot receive continuous rotation gestures. On the widget:

- Tap the dial face to open the app.
- Use the + and − controls to change media volume by 10% of the range (Samsung fine units or Android steps).
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

- Samsung fine volume relies on undocumented Samsung methods (see above).

- The home-screen widget cannot implement continuous dial rotation; only tap and ±10% actions are supported.
- The volume button step works only while the screen is on, only with Samsung fine volume, and only after you turn on its accessibility service.
- Without Samsung fine volume, the media step count is device-dependent (often around 15 steps); the dial then has 5 ticks per turn, one per whole step.
- Dynamic color (Material You) requires API 31+. Older devices use the expressive light scheme or the dark color scheme.

## Package

- applicationId / namespace: `com.oiuy.volumecontrol`
- versionName: `1.0.2`
- versionCode: `25`
- minSdk: 26
- compileSdk: 37 (required by Material 3 1.5.0-rc01); targetSdk: 36

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Third-party libraries and their licenses are listed in [THIRD_PARTY.md](THIRD_PARTY.md). Credit is given only where due (AndroidX, Material, Kotlin, Glance, and related tooling).
