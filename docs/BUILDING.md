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

Dial math coverage lives under `app/src/test/java/com/oiuy/volumecontrol/volume/`.

Diagnostic coverage lives under `app/src/test/java/com/oiuy/volumecontrol/diagnostic/`. `FineVolumeDiagnosticTest` uses fake ports to check that refreshes never write, that a tap writes once with a freshly read value minus one, and that nothing is written when the device is not Samsung, the methods are missing, the value is 0, media is muted, or a read fails. `DiagnosticSourceGuardTest` scans the app sources so that no other code path calls a volume setter.

Lint:

```bash
./gradlew :app:lintDebug --no-configuration-cache
```
