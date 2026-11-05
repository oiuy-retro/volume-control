# Changelog

## Unreleased

- Center percent readout now tracks the fine fractional volume to one decimal place and updates on every dial tick (0.1% of max), not only when the coarse STREAM_MUSIC integer index changes.
- Crossing a dial tick shows the Android system media-volume panel (`FLAG_SHOW_UI`), matching hardware volume keys; at most once per input frame when several ticks are crossed together.
- Bumped versionCode to 3 for the next device-test Pre-release (versionName still 0.1.0).
- Dial face now shows 100 indents (one every 3.6°), with every 10th indent longer.
- Each indent is a fine position worth 0.1% of the media stream maximum; one full turn is still 10%.
- Light haptic tick when passing each indent, counted from unwrapped accumulated rotation, limited to one tick per frame and one every 12 ms.
- Replaced the previous haptic that fired on each 10% boundary.
- Added the `VIBRATE` permission for reliable ticks when system touch feedback is off.
- Haptic step approach informed by HeliBoard-custom's slider feedback; the code here is an original Apache-2.0 implementation.

## 0.1.0

- Initial Material 3 Expressive volume dial app.
- Full 360° dial rotation maps to ±10% of `STREAM_MUSIC` max; fractional turns apply proportionally.
- Edge-to-edge single-activity Compose UI with large centered dial and percent readout.
- Dynamic color on API 31+ with expressive light / dark fallbacks.
- Glance home-screen widget with percent display, tap-to-open, and ±10% actions.
- Unit tests for dial math.
- Apache-2.0 license and third-party attributions.
