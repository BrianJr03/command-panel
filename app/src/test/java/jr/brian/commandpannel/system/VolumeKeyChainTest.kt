package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.VolumeKeyChain.Companion.KEY_DOWN
import jr.brian.commandpannel.system.VolumeKeyChain.Companion.KEY_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers stock's volume key chaining from the music volume into the bottom level. */
class VolumeKeyChainTest {

    private fun VolumeKeyChain.press(key: String?, now: Long, music: Int, bottom: Int) =
        onKey(key, now, music = music, musicMin = 0, musicMax = 15, bottom = bottom)

    @Test fun upAtMusicMaxRaisesBottom() = assertEquals(8, VolumeKeyChain().press(KEY_UP, 0, music = 15, bottom = 7))

    @Test fun upBelowMusicMaxLeavesBottom() = assertNull(VolumeKeyChain().press(KEY_UP, 0, music = 14, bottom = 7))

    @Test fun upStopsAtBottomMax() = assertNull(VolumeKeyChain().press(KEY_UP, 0, music = 15, bottom = 15))

    @Test fun downAtMusicMinLowersBottom() = assertEquals(6, VolumeKeyChain().press(KEY_DOWN, 0, music = 0, bottom = 7))

    @Test fun downAboveMusicMinLeavesBottom() = assertNull(VolumeKeyChain().press(KEY_DOWN, 0, music = 1, bottom = 7))

    @Test fun downStopsAtZero() = assertNull(VolumeKeyChain().press(KEY_DOWN, 0, music = 0, bottom = 0))

    @Test fun usesTheStreamMinNotZero() =
        assertEquals(6, VolumeKeyChain().onKey(KEY_DOWN, 0, music = 1, musicMin = 1, musicMax = 15, bottom = 7))

    @Test fun pressesInsideTheDebounceAreDropped() {
        val chain = VolumeKeyChain(debounceMs = 30)
        assertEquals(8, chain.press(KEY_UP, 1_000, music = 15, bottom = 7))
        assertNull(chain.press(KEY_UP, 1_029, music = 15, bottom = 8))
        assertEquals(9, chain.press(KEY_UP, 1_030, music = 15, bottom = 8))
    }

    @Test fun theDebounceIsSharedByBothKeysAndCountsPressesThatMoveNothing() {
        val chain = VolumeKeyChain(debounceMs = 30)
        assertNull(chain.press(KEY_UP, 1_000, music = 10, bottom = 7)) // handled, but music isn't at max
        assertNull(chain.press(KEY_DOWN, 1_010, music = 0, bottom = 7))
    }

    @Test fun otherKeyTypesAreIgnoredAndDoNotTouchTheDebounce() {
        val chain = VolumeKeyChain(debounceMs = 30)
        assertNull(chain.press("KEYCODE_VOLUME_MUTE", 1_000, music = 15, bottom = 7))
        assertNull(chain.press(null, 1_001, music = 15, bottom = 7))
        assertEquals(8, chain.press(KEY_UP, 1_002, music = 15, bottom = 7))
    }
}
