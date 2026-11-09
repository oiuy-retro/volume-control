# Changelog

## 0.1.4-pre.2 (local follow-up; device verification pending)

- Fix integration bookkeeping: preserve raw finger position through own asynchronous stream writes and observers; read back the actual Android index; apply only the final magnet output once per finger event.
- Force the gain request past the cache after stream writes and in bounded settlement checks, volume broadcasts, resume and output changes. This addresses a possible vendor parameter reset, not a proven Bluetooth root cause. Observers never write stream volume; unchanged generic settings notifications cannot create a gain-write loop.
- Require enabled/control/matching API confirmation before increasing to an upper stream step. Distinguish requested gain from API read-back; expose errors, backend attempts and unavailable/held-volume states. No claim of acoustic verification.
- Keep a compensating cut during deferred downward writes. Report clamped/unsettled writes without endless retries or automatically releasing the cut. Route changes refresh the curve and reassert the cut without restoring an old route's stream index.
- Add explicit enable/reattach and fixed-Android-level 0/−12 dB diagnostic (backend-floor clamped), with a locked dial and visible test mode. Exit retains the cut; reattach/process death can release it and increase loudness.
- Remove per-tick ADJUST_SAME / system volume UI calls; retain dial/haptic/magnet math.
- Add fake volume/effect coordinator and readout regressions: 110 unit tests pass; debug assembly and lint pass (0 errors, 21 warnings). VersionName 0.1.4, versionCode 16. No release or device verification yet.

## 0.1.4 (Pre-release, device test)

- Hybrid fine volume: the 100-tick dial (10% per turn) requests cuts intended to fill the gaps between Android's whole media levels. For a position between levels n and n+1, the stream is set to n+1 and a global output effect (session 0; DynamicsProcessing input gain, Equalizer fallback) attenuates by (1 - fraction) times the dB difference between the two levels, so requested loudness moves linearly in dB from level n to n+1. The effect never boosts.
- Level dB values come from `AudioManager.getStreamVolumeDb` for the current output device. If the route reports no usable curve (common with Bluetooth absolute volume), an even 2.5 dB-per-level estimate is used and the screen says so.
- Between mute and level 1 the effect fades over 30 dB, limited to the backend's reported floor.
- Initial write ordering applied attenuation before the stream level went up and after it went down; asynchronous vendor updates were not verified.
- If the effect cannot attach, the dial falls back to whole Android levels and says so under the dial.
- Initial readout showed Android level, requested effect dB (incorrectly described as applied), and backend; pre.2 separates API confirmation.
- Widget +/- steps clear any in-between attenuation.
- Adds `MODIFY_AUDIO_SETTINGS` (install-time permission used by audio effects).
- versionName 0.1.4, versionCode 15.

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
