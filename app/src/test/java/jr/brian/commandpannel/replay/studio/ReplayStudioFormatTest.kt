package jr.brian.commandpannel.replay.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ReplayStudioFormatTest {

    @Test
    fun `durations read as minutes and seconds, hours from an hour up`() {
        assertEquals("0:00", formatClipDuration(0))
        assertEquals("0:00", formatClipDuration(-40))
        assertEquals("0:01", formatClipDuration(600))
        assertEquals("0:30", formatClipDuration(29_700))
        assertEquals("1:05", formatClipDuration(65_000))
        assertEquals("59:59", formatClipDuration(3_599_000))
        assertEquals("1:02:03", formatClipDuration(3_723_000))
    }

    @Test
    fun `dates use the locale's medium date and short time`() {
        val zone = ZoneId.of("America/New_York")
        val ms = ZonedDateTime.of(2026, 11, 19, 15, 42, 7, 0, zone).toInstant().toEpochMilli()
        val text = formatClipDate(ms, zone, Locale.US)
        // The JDK's CLDR data puts a narrow no-break space before PM; compare without caring which space it is.
        assertEquals("Nov 19, 2026, 3:42 PM", text.replace(' ', ' '))
    }

    @Test
    fun `focus moves to the next card after a delete`() {
        val grid = listOf("a", "b", "c", "d")
        assertEquals("b", focusAfterDelete(grid, "a"))
        assertEquals("c", focusAfterDelete(grid, "b"))
        assertEquals("d", focusAfterDelete(grid, "c"))
    }

    @Test
    fun `focus moves back a card when the last one is deleted`() {
        assertEquals("c", focusAfterDelete(listOf("a", "b", "c", "d"), "d"))
        assertEquals("a", focusAfterDelete(listOf("a", "b"), "b"))
    }

    @Test
    fun `no focus target when the grid empties or the clip is unknown`() {
        assertNull(focusAfterDelete(listOf("a"), "a"))
        assertNull(focusAfterDelete(emptyList(), "a"))
        assertNull(focusAfterDelete(listOf("a", "b"), "z"))
    }

    @Test
    fun `thumbnails decode at the largest step that stays wide enough`() {
        assertEquals(1, thumbnailSampleSize(480, 480))
        assertEquals(1, thumbnailSampleSize(900, 480))
        assertEquals(2, thumbnailSampleSize(960, 480))
        assertEquals(4, thumbnailSampleSize(1920, 480))
        assertEquals(1, thumbnailSampleSize(0, 480))
        assertEquals(1, thumbnailSampleSize(1920, 0))
    }
}
