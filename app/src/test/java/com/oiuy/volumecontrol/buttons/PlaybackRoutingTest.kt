package com.oiuy.volumecontrol.buttons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRoutingTest {
    private fun pass(defaults: Set<Int>?, vararg playbacks: Int?) =
        PlaybackRouting.separateOutputsActive(PlaybackRoutes(defaults, playbacks.toList()))

    @Test fun bluetoothOnlyIsHandled() {
        assertFalse(pass(setOf(BT_A2DP), BT_A2DP))
        assertFalse(pass(setOf(BT_A2DP), BT_A2DP, BT_A2DP))
        assertFalse(pass(setOf(BLE_HEADSET), BLE_HEADSET))
    }

    @Test fun bluetoothPlusSpeakerPlaybackPasses() {
        assertTrue(pass(setOf(BT_A2DP), BT_A2DP, SPEAKER))
        assertTrue(pass(setOf(BLE_HEADSET), SPEAKER, BLE_HEADSET))
    }

    @Test fun onlyAppOnSpeakerWhileDefaultIsBluetoothPasses() {
        // Bluetooth is connected and default, but the only playback (browser) is on the speaker.
        assertTrue(pass(setOf(BT_A2DP), SPEAKER))
        assertTrue(pass(setOf(USB_HEADSET), EARPIECE))
    }

    @Test fun speakerOnlyWithoutBluetoothIsHandled() {
        assertFalse(pass(setOf(SPEAKER), SPEAKER))
        assertFalse(pass(setOf(SPEAKER)))
    }

    @Test fun noPlaybackIsHandled() {
        assertFalse(pass(setOf(BT_A2DP)))
        assertFalse(pass(null))
    }

    @Test fun nullDeviceInfoKeepsCurrentBehavior() {
        assertFalse(pass(setOf(BT_A2DP), null))
        assertFalse(pass(setOf(BT_A2DP), null, BT_A2DP))
        assertFalse(pass(setOf(BT_A2DP), null, null))
        assertFalse(PlaybackRouting.separateOutputsActive(null))
    }

    @Test fun unknownDefaultPassesOnlyWhenClearlyMixed() {
        assertFalse(pass(null, SPEAKER))
        assertFalse(pass(emptySet(), BT_A2DP))
        assertTrue(pass(null, BT_A2DP, SPEAKER))
        assertTrue(pass(null, null, BT_A2DP, SPEAKER))
    }

    @Test fun multipleDeviceTypesPass() {
        assertTrue(pass(setOf(SPEAKER), SPEAKER, WIRED_HEADPHONES))
        assertTrue(pass(setOf(BT_A2DP), BT_A2DP, USB_HEADSET, SPEAKER))
    }

    @Test fun defaultWithSeveralDevicesAcceptsAnyOfThem() {
        // e.g. Bluetooth plus an LE broadcast as the default media route.
        assertFalse(pass(setOf(BLE_HEADSET, BLE_BROADCAST), BLE_HEADSET))
    }

    @Test fun onlyMediaUsagesCount() {
        assertTrue(PlaybackRouting.isMediaUsage(USAGE_MEDIA))
        assertTrue(PlaybackRouting.isMediaUsage(USAGE_GAME))
        assertTrue(PlaybackRouting.isMediaUsage(USAGE_UNKNOWN))
        assertFalse(PlaybackRouting.isMediaUsage(USAGE_NOTIFICATION))
        assertFalse(PlaybackRouting.isMediaUsage(USAGE_ASSISTANCE_SONIFICATION))
        assertFalse(PlaybackRouting.isMediaUsage(USAGE_ALARM))
    }

    private companion object {
        // android.media.AudioDeviceInfo types.
        const val EARPIECE = 1
        const val SPEAKER = 2
        const val WIRED_HEADPHONES = 4
        const val BT_A2DP = 8
        const val USB_HEADSET = 22
        const val BLE_HEADSET = 26
        const val BLE_BROADCAST = 30
        // android.media.AudioAttributes usages.
        const val USAGE_UNKNOWN = 0
        const val USAGE_MEDIA = 1
        const val USAGE_ALARM = 4
        const val USAGE_NOTIFICATION = 5
        const val USAGE_ASSISTANCE_SONIFICATION = 13
        const val USAGE_GAME = 14
    }
}
