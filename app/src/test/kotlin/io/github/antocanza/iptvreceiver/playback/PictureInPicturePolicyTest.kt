package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureInPicturePolicyTest {
    @Test fun `live retry delays keep Pause available so the viewer can cancel recovery`() {
        assertTrue(showPictureInPicturePause(true, Player.STATE_IDLE, true, PlaybackRecoveryState.RECONNECTING))
        assertTrue(showPictureInPicturePause(true, Player.STATE_ENDED, false, PlaybackRecoveryState.RECONNECTING))
        assertFalse(showPictureInPicturePause(false, Player.STATE_IDLE, true, PlaybackRecoveryState.NONE))
        assertFalse(showPictureInPicturePause(true, Player.STATE_IDLE, true, PlaybackRecoveryState.FAILED))
        assertFalse(showPictureInPicturePause(true, Player.STATE_ENDED, false, PlaybackRecoveryState.NONE))
    }
    @Test fun `leaving while playing buffering or reconnecting keeps the intended video visible`() {
        assertTrue(auto(Player.STATE_READY))
        assertTrue(auto(Player.STATE_BUFFERING))
        assertTrue(auto(Player.STATE_IDLE, recovery = PlaybackRecoveryState.RECONNECTING))
    }

    @Test fun `pause completion failure and focus suppression never auto enter`() {
        for (state in listOf(Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED)) {
            assertFalse(auto(state, playing = false))
            assertFalse(auto(state, suppressed = true))
            assertFalse(auto(state, recovery = PlaybackRecoveryState.FAILED))
        }
        assertFalse(auto(Player.STATE_IDLE))
        assertFalse(auto(Player.STATE_ENDED))
        assertFalse(auto(Player.STATE_IDLE, playing = false, recovery = PlaybackRecoveryState.RECONNECTING))
    }

    @Test fun `video dimensions respect pixel shape and the selected aspect override`() {
        assertEquals(17_777, pictureInPictureAspect(1920, 1080, 1f, null))
        assertEquals(13_333, pictureInPictureAspect(720, 576, 16f / 15, null))
        assertEquals(13_333, pictureInPictureAspect(1920, 1080, 1f, 4f / 3))
    }

    @Test fun `unknown invalid and extreme stream ratios cannot crash entry or resize`() {
        assertEquals(17_777, pictureInPictureAspect(0, 0, 1f, null))
        assertEquals(17_777, pictureInPictureAspect(1920, 1080, Float.NaN, null))
        assertEquals(17_777, pictureInPictureAspect(1920, 1080, 0f, null))
        assertEquals(4_185, pictureInPictureAspect(1, 10_000, 1f, null))
        assertEquals(23_900, pictureInPictureAspect(10_000, 1, 1f, null))
    }

    private fun auto(state: Int, playing: Boolean = true, suppressed: Boolean = false,
        recovery: PlaybackRecoveryState = PlaybackRecoveryState.NONE) =
        shouldAutoEnterPictureInPicture(playing, state, suppressed, recovery)
}
