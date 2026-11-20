# Building Volume Control

## Toolchain

- JDK 21 (Temurin 21.0.13+9 or another JDK 21 distribution)
- Android SDK with:
  - platforms;android-36 (and android-37 for compileSdk)
  - build-tools;36.0.0
- Android Gradle Plugin 9.5.1
- Gradle Wrapper 9.6.0 (included)
- Kotlin Compose compiler plugin 2.2.21

`compileSdk` is 37 because `androidx.compose.material3:1.5.0-rc01` requires it. `targetSdk` remains 36.

## On the project build box

```bash
cd /workspace/volume-control   # or a worktree such as /workspace/volume-control-samsung-diagnostic
export JAVA_HOME=/workspace/android/tools/jdk-21.0.13+9
export PATH="$JAVA_HOME/bin:$PATH"
# local.properties points at /workspace/tools/sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease   # requires keystore.properties
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`

Optional copy location used for sideload hand-off (not committed):

`/workspace/android/out-volume-control/`

## Selawik font download

The app uses the Selawik font (Microsoft, SIL Open Font License 1.1). The font files are third-party binaries, so they are not stored in git. The Gradle task `:app:fetchSelawikFonts` (defined in `app/build.gradle.kts`) runs before resource merging and:

1. Looks for a cached `Selawik_Release.zip` in `~/.gradle/caches/volume-control/selawik-1.01/` (inside the Gradle user home).
2. If it is missing or its SHA-256 does not match, downloads `https://github.com/microsoft/Selawik/releases/download/1.01/Selawik_Release.zip` and checks it against the pinned SHA-256 `3f62c51e05e3b5a1e6241cf92a371f0be2ea1183aa87b30718bbd40832a8d423`. A mismatch stops the build.
3. Extracts the five TTF weights (Light, Semilight, Regular, Semibold, Bold), checks each against its own pinned SHA-256, and writes them as `selawik_*.ttf` into `app/build/generated/selawik/res/font/`, which is added to every variant's resources.

Only the first build needs internet access. To prepare an offline or release build, run it once ahead of time:

```bash
./gradlew :app:fetchSelawikFonts
```

The license text is in `docs/licenses/Selawik-OFL.txt`. The task `:app:selawikLicenseAsset` copies it into the APK as `assets/licenses/Selawik-OFL.txt`, next to the fonts.

## Release signing

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Point `storeFile` at the dedicated release keystore for this app.
3. Fill `storePassword`, `keyAlias`, and `keyPassword`.
4. Keep `keystore.properties` and `*.jks` out of git (they are gitignored).
5. Build:

```bash
./gradlew :app:assembleRelease
```

Release APK:

`app/build/outputs/apk/release/app-release.apk`

## General machine setup

```bash
git clone https://github.com/oiuy-retro/volume-control.git
cd volume-control
git checkout main
echo "sdk.dir=/absolute/path/to/Android/Sdk" > local.properties
export JAVA_HOME=/absolute/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Dial math coverage lives under `app/src/test/java/com/oiuy/volumecontrol/volume/`. The dial's Reveal highlight (`DialGlow`: angular distance, falloff, highlight strength and reach) is tested in `app/src/test/java/com/oiuy/volumecontrol/ui/DialGlowTest.kt`, and the drawn-mark mapping (`DialMarks`: 150 marks per turn, every third on a unit, majors every 5 units) in `DialMarksTest.kt` in the same folder.

Volume backend coverage lives in the same folder: `VolumeEngineTest` (mode choice, fallback, clamping, no public writes in fine mode), `CoalescingWriterTest` (latest-value coalescing), `MediaVolumeControllerTest` (50 units per turn, one write per tick, widget steps, reads never write), `TickDialTest` (50 ticks per turn, 3 turns at 150, fallback ticks, hard stops, magnet with a 2-tick deadzone), `HapticUnitsTest` (one haptic tick per unit, strong tick every full turn), and `VolumeSourceGuardTest` (platform volume writes and method lookups only in `AndroidVolumePorts.kt`, no restricted access techniques).

Lint:

```bash
./gradlew :app:lintDebug --no-configuration-cache
```
