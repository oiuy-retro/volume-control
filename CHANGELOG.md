# Changelog

## 0.1.5 (diagnostic, versionCode 18)

- Replaced the main screen with a Samsung fine-volume diagnostic. It shows device and Android version, public media volume index, minimum, maximum, mute state and media output type, and the raw value from Samsung's `semGetFineVolume(int)` for music.
- Added a "Test one fine step quieter" button. It is the only thing in this build that changes volume: each tap reads the current fine value and calls `semSetFineVolume(int, int, int)` once with that value minus one, then reads back. No automatic restore, retry, or fallback to normal volume steps.
- The methods are looked up with `Class.getMethod` only on devices whose manufacturer is Samsung. Lookup and call failures are shown on screen.
- The dial is not shown. The home-screen widget is read-only; the + and − actions of widgets placed by earlier builds do nothing.
- Added the normal `MODIFY_AUDIO_SETTINGS` permission.
- Built from `main`, not from the 0.1.4 gain experiment. No fine-step dial yet.

## Unreleased

- Stationary bezel ticks: 100 fine marks stay fixed on the dial rim; only the knob and indicator rotate with volume.
- Twelve o'clock is the landmark for each 10% volume step (0, 10, 20, …, 100). Major marks every 36° (1% of max / whole display percent); the noon mark is emphasized.
- Tiny whole-percent magnet: while the raw percent is within ±0.1% of an integer, or a fast frame sweeps across a whole-percent mark, the reported value (and STREAM_MUSIC) overlays onto that whole once. No finger-speed gate. After a snap engages, the magnet will not re-engage until raw has moved 0.5% (5 fine ticks) away (anti-jitter). Finger deltas always update a continuous raw accumulator first; the magnet never rebuilds angle/percent from the snapped value.
- Two-tier haptics: fine ticks use the platform tick constant plus a light 5 ms / amplitude 65 one-shot; whole-percent majors use LONG_PRESS plus a 28 ms / amplitude 255 one-shot. Anti-buzz still rate-limits fines (and majors vs majors) to about 12 ms, but a major is not dropped because a fine fired recently, and fines are suppressed for the 28 ms major duration so they cannot replace an in-flight major vibration. One pulse per frame; multi-tick jumps play one major when any crossed tick is major.
- Hard stops: counterclockwise rotation is ignored at 0%, clockwise at 100%. Blocked motion does not fire haptics.
- Indicator angle follows fine percent within the current 10% decade so landmarks line up with twelve o'clock.
- System volume panel (`FLAG_SHOW_UI`) is rate-limited to about once every 120 ms.
- Home-screen widget updates on drag end only, not on every drag pixel.
- ContentObserver no longer calls `syncFromSystem()` while the dial is being dragged (avoids freezing / jumping the fine level).
- Bumped versionName to 0.1.1 and versionCode to 11 for the next device-test Pre-release (installs over versionCode 10).

## 0.1.0

- Initial Material 3 Expressive volume dial app.
- Full 360° dial rotation maps to ±10% of `STREAM_MUSIC` max; fractional turns apply proportionally.
- Edge-to-edge single-activity Compose UI with large centered dial and percent readout.
- Dynamic color on API 31+ with expressive light / dark fallbacks.
- Glance home-screen widget with percent display, tap-to-open, and ±10% actions.
- Dial face showed 100 indents that rotated with the dial; light haptic per indent; fine percent readout; system volume panel on tick crossings.
- Unit tests for dial math.
- Apache-2.0 license and third-party attributions.
