package jr.brian.commandpannel.replay.studio

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Pure helpers for Replay Studio's grid, kept out of the composables so they can be unit tested. */

/** A clip length as `m:ss`, or `h:mm:ss` from an hour up. Negative lengths read as zero. */
internal fun formatClipDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** When a clip was recorded, in the device's locale (for example "Nov 19, 2026, 3:42 PM"). */
internal fun formatClipDate(ms: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(ms).atZone(zone))

/** The badge on a clip card: which screen it came from. */
@StringRes
internal fun ClipOrigin.badgeRes(): Int = when (this) {
    ClipOrigin.TOP -> R.string.studio_badge_top
    ClipOrigin.BOTTOM -> R.string.studio_badge_bottom
    ClipOrigin.BOTH -> R.string.studio_badge_both
}

/**
 * The card that gets focus after [deleted] leaves the grid ([paths] is the grid before the delete): the next card,
 * or the previous one when it was the last. Null when nothing is left (or [deleted] isn't in the grid).
 */
internal fun focusAfterDelete(paths: List<String>, deleted: String): String? {
    val i = paths.indexOf(deleted)
    if (i < 0) return null
    val rest = paths.filterIndexed { j, _ -> j != i }
    if (rest.isEmpty()) return null
    return rest[i.coerceAtMost(rest.lastIndex)]
}

/** The largest power-of-two step that keeps a decoded thumbnail at least [targetWidth] wide. */
internal fun thumbnailSampleSize(width: Int, targetWidth: Int): Int {
    if (width <= 0 || targetWidth <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
