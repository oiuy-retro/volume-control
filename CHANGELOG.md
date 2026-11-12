# Changelog

## 1.0.2 (versionCode 27, pre-release 1.0.2-pre.5)

- Combines the volume button feature (1.0.2-pre.1 to pre.4, now on main) with the major-mark dial fix from 1.0.1-pre.2. The changes are the same as in those builds, listed below.
- versionCode 27 installs over both 1.0.1-pre.2 (26) and 1.0.2-pre.4 (25).

## 1.0.1 (versionCode 26, pre-release 1.0.1-pre.2)

- Fixed: since the 50-tick dial, only the twelve o'clock mark had a magnet snap and a strong haptic. The other major marks drawn on the bezel (every 5 fine units, 10 per turn, every 36°) had neither. The magnet was attached to every single unit tick with a ±0.1-unit zone, and its 2-tick deadzone after each tiny snap usually blocked the major marks. The strong haptic only fired at full turns.
- The magnet now snaps only to the major marks (every 5 fine units, plus 0 and the maximum), catching within ±0.5 units (±3.6°, the same angle as before the 50-tick dial), with the same 2-tick deadzone after a jump.
- A strong haptic now plays on landing on or crossing every major mark, twelve o'clock included.
- The major marks, magnet and haptic all come from one constant, so they cannot drift apart. Android-steps mode is unchanged (a magnet on every step, strong haptic at each full turn).
- versionCode 26 is higher than the 1.0.2 test builds (22 to 25), so it installs over them. It does not include the volume button feature from 1.0.2-pre.x.

## 1.0.2 (versionCode 25, pre-release 1.0.2-pre.4)

- Fixed: the volume buttons broke Samsung's Separate app sound. With an app assigned to the phone speaker and music on Bluetooth, the service always wrote the default (Bluetooth) fine volume. Now each new press first checks where media is playing, using `AudioManager.getActivePlaybackConfigurations()` with each playback's output device (Android 12+) and `getAudioDevicesForAttributes()` for the default media output (Android 13+). The press goes to Samsung's normal handling if media plays on two or more output types, or on an output other than the default. Only media and game usages count. When the device information is missing, it falls back to the earlier behavior unless the outputs are clearly mixed.
- The decision is made once per press on its first DOWN and kept for that press's repeats and UP. Repeat ticks do not re-read the playback list.
- Limits: Android's public playback list can include paused players, so a paused video whose app keeps its player open may still count. The app cannot see which app is in focus.

## 1.0.2 (versionCode 24, pre-release 1.0.2-pre.3)

- Fixed (second attempt): screen-off volume buttons stopped working while the accessibility service was on, even with the in-app switch off. On the Galaxy S24+ it is the key filter itself that breaks them. The service now starts without key filtering (`flagRequestFilterKeyEvents` is no longer in the XML flags; `canRequestFilterKeyEvents` stays) and turns filtering on at runtime with `setServiceInfo` only while the screen is fully on, the switch is on, and Samsung fine volume is available. It turns filtering off on screen off, when the switch is turned off (right away, through a preference listener), and when fine volume is missing. It watches the screen-off, screen-on and unlock broadcasts and display changes, and it checks again when it connects and on every key.
- Removed the 1.0.2-pre.2 screen-off fallback, which made Android's one-step change itself. A key that arrives while filtering should be off goes straight back to Android.
- The press pairing and repeat fixes from pre.2 are kept.
- The in-app note and the service description now say to turn the service off in Accessibility settings if screen-off buttons still misbehave.

## 1.0.2 (versionCode 23, pre-release 1.0.2-pre.2)

- Fixed: with the screen off, the volume buttons did nothing while the accessibility service was on (1.0.2-pre.1). The service already handed those presses back to Android, but on the Galaxy S24+ a handed-back screen-off press was lost. Now a screen-off press that reaches the service makes Android's normal one-step change: media volume while something plays, call volume during a call. With nothing playing and no call it is still handed back. Fine steps remain screen-on only.
- The screen counts as on only when Android reports it is interactive and the display is fully on (not off, doze, or Always On Display). This is checked on every key event, not cached.
- A held button's app repeat stops as soon as the screen turns off. A held press whose screen turned off part-way no longer makes fine steps.
- Presses are paired by their down time. An UP is consumed only when its own DOWN was consumed, and repeats or an UP from a press whose first DOWN the service never saw go to Android, so a press whose UP was lost can no longer block later presses.

## 1.0.2 (versionCode 22, pre-release 1.0.2-pre.1)

- New: the hardware volume buttons can move Samsung's fine media volume by a step you choose, from 1 to 15 fine units per press (default 1, shown as for example "1 of 150 per press"). This uses an accessibility service inside Volume Control that you turn on in Settings > Accessibility. It reads only the volume up and volume down buttons, asks for no accessibility events, and cannot read anything on screen.
- Works only while the screen is on. With the screen off Android does not pass volume buttons to accessibility services, so they keep Android's normal steps.
- The buttons are passed to Android unchanged when the in-app switch is off, on non-Samsung phones, when Samsung's fine methods are missing, when a fine read or write fails, and during a call, while ringing, or in a voice or video call app.
- While the feature is on and the screen is on, the buttons always change media volume, even when nothing is playing.
- Holding a button keeps stepping. Android's own key repeats are used when it sends them; otherwise the app repeats after about 0.5 s and then about every 50 ms. Each write asks Samsung to show its volume panel.
- New settings card under the dial: an on/off row (it opens Accessibility settings when the service is not on) and a step slider. Settings are kept in SharedPreferences.
- The dial follows button presses while the app is open and rebases when you return to it. The widget is unchanged.

## 1.0.1 (versionCode 21)

- The dial face now has 50 ticks per turn, and every tick changes the volume. In Samsung fine mode one tick is one fine unit, so 150 units take 3 turns (turns = fine maximum / 50). Previously one turn was 10% of the range and most of the 100 marks did not change the volume.
- One light haptic per tick and a strong haptic at each full turn (every 50 units).
- In Android-steps mode the dial has 5 ticks per turn, one per whole step.
- The magnet snaps the indicator onto ticks and will not snap again until the finger has moved 2 ticks (14.4°) away. It no longer changes the value written.
- Widget + and − are unchanged (10% of the range). The fine-volume write path and fallback are unchanged.

## 1.0.0 (versionCode 20)

- First full release. Same app as 0.1.5-pre.2, which was tested on a Galaxy S24+ (SM-S926U, Android 17): the dial and widget drive Samsung's fine media volume (150 steps on that phone), with Android's whole volume steps as the fallback on other phones.
- Version number changed to 1.0.0 (versionCode 20). No code changes from 0.1.5-pre.2.

## 0.1.5 (versionCode 19, pre-release 0.1.5-pre.2)

- The dial and widget are back, and on Samsung phones they drive Samsung's fine media volume (Android's media maximum times 10, 150 on a 15-step phone) through `semGetFineVolume(int)` / `semSetFineVolume(int, int, int)`, looked up with `Class.getMethod`.
- One full turn is still 10% of the range. In fine mode each fine unit is one haptic tick and every 10% is a strong tick; the whole-percent magnet is unchanged.
- Writes happen only when the whole fine unit changes, coalesced to the latest value at most about every 30 ms, and flushed when the finger lifts. The whole-step setter is never used in fine mode.
- Opening or returning to the app, and volume broadcasts, only read the fine value to move the dial.
- Widget + and − change 10% of the fine range; the widget percent comes from the fine value.
- Falls back to Android's whole steps on other phones, when the methods are missing, or after any fine failure, with a status line under the dial.
- Removed the diagnostic test screen; its result was verified on a Galaxy S24+ (SM-S926U, Android 17).
- Fixed a lint error in the widget description string.

## 0.1.5-pre.1 diagnostic (versionCode 18)

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
