# Changelog

## Unreleased

- Stationary bezel ticks: 100 fine marks stay fixed on the dial rim; only the knob and indicator rotate with volume.
- Twelve o'clock is the landmark for each 10% volume step (0, 10, 20, …, 100). Major marks every 36° (1% of max / whole display percent); the noon mark is emphasized.
- Tiny whole-percent magnet: while the raw percent is within ±0.1% of an integer, the reported value (and STREAM_MUSIC) overlays onto that whole percent. No finger-speed gate. After a snap engages, the magnet will not re-engage until raw has moved 0.5% (5 fine ticks) away from that whole (anti-jitter). Finger deltas always update a continuous raw accumulator first; the magnet never rebuilds angle/percent from the snapped value.
- Two-tier haptics: fine ticks use the platform tick constant plus a light 5 ms / amplitude 65 one-shot; whole-percent majors use LONG_PRESS plus a 28 ms / amplitude 255 one-shot. Shared 12 ms rate limit; major pulse resets the timer. Major haptic fires edge-triggered when the fine tick index enters or crosses a major; not while holding on that tick.
- Hard stops: counterclockwise rotation is ignored at 0%, clockwise at 100%. Blocked motion does not fire haptics.
- Indicator angle follows fine percent within the current 10% decade so landmarks line up with twelve o'clock.
- System volume panel (`FLAG_SHOW_UI`) is rate-limited to about once every 120 ms.
- Home-screen widget updates on drag end only, not on every drag pixel.
- ContentObserver no longer calls `syncFromSystem()` while the dial is being dragged (avoids freezing / jumping the fine level).
- Bumped versionName to 0.1.1 and versionCode to 10 for the next device-test Pre-release (installs over versionCode 9).

## 0.1.0

- Initial Material 3 Expressive volume dial app.
- Full 360° dial rotation maps to ±10% of `STREAM_MUSIC` max; fractional turns apply proportionally.
- Edge-to-edge single-activity Compose UI with large centered dial and percent readout.
- Dynamic color on API 31+ with expressive light / dark fallbacks.
- Glance home-screen widget with percent display, tap-to-open, and ±10% actions.
- Dial face showed 100 indents that rotated with the dial; light haptic per indent; fine percent readout; system volume panel on tick crossings.
- Unit tests for dial math.
- Apache-2.0 license and third-party attributions.
