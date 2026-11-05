# Changelog

## Unreleased

- Dial face now shows 100 indents (one every 3.6°), with every 10th indent longer.
- Each indent is a fine position worth 0.1% of the media stream maximum; one full turn is still 10%.
- Light haptic tick when passing each indent, counted from unwrapped accumulated rotation, limited to one tick per frame and one every 12 ms.
- Replaced the previous haptic that fired on each 10% boundary.
- Added the `VIBRATE` permission for reliable ticks when system touch feedback is off.
- Haptic step approach informed by HeliBoard-custom's slider feedback; the code here is an original Apache-2.0 implementation.
- Bumped versionCode to 2 for the next device-test Pre-release (versionName still 0.1.0).

## 0.1.0

