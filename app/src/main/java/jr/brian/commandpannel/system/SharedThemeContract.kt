package jr.brian.commandpannel.system

/**
 * The shared theme contract, version 1: how another app reads the overlay's resolved colors from [ThemeProvider].
 * The full spec is `docs/SHARED_THEME.md` in the Command Panel repo.
 *
 * Constants only, with no Android or Command Panel imports, so a client can copy this file as is (keeping or changing
 * the `package` line). Version 1 is frozen: never add, remove, rename or reorder a column. Any change to the row means
 * a new [VERSION].
 */
object SharedThemeContract {

    /** The contract version this file describes, also returned in [COLUMN_VERSION]. */
    const val VERSION = 1

    /** The provider's authority is the Command Panel package plus this suffix. */
    const val AUTHORITY_SUFFIX = ".theme"

    /** The one readable path: `content://<package>.theme/palette`. Returns one row. */
    const val PATH = "palette"

    /** The only package a release client may trust. */
    const val RELEASE_PACKAGE = "jr.brian.commandpannel"

    /** For debug clients only. A release client must never read from or trust this package. */
    const val DEBUG_PACKAGE = "jr.brian.commandpannel.debug"

    /** SHA-256 of Command Panel's release signing certificate (lowercase hex, no separators). Debug builds differ. */
    const val RELEASE_CERT_SHA256 = "8ebd8e6ff20a29019a857fe25a98532649d66bf3fd778a64150db8085d988927"

    // ---- columns, all integers ----

    const val COLUMN_VERSION = "version"

    /** 1 for a dark palette, 0 for a light one. */
    const val COLUMN_IS_DARK = "is_dark"

    // Colors, as ARGB ints.
    const val COLUMN_BG = "bg"
    const val COLUMN_RAIL = "rail"
    const val COLUMN_SURFACE = "surface"
    const val COLUMN_SURFACE_HIGH = "surface_high"
    const val COLUMN_BORDER = "border"
    const val COLUMN_ACCENT = "accent"
    const val COLUMN_ACCENT_SOFT = "accent_soft"
    const val COLUMN_ACCENT_FOCUSED = "accent_focused"
    const val COLUMN_ON_ACCENT = "on_accent"
    const val COLUMN_TEXT_PRIMARY = "text_primary"
    const val COLUMN_TEXT_SECONDARY = "text_secondary"
    const val COLUMN_TEXT_TERTIARY = "text_tertiary"
    const val COLUMN_DANGER = "danger"
    const val COLUMN_SUCCESS = "success"

    /** Every column, in the order a query without a projection returns them. */
    val COLUMNS = listOf(
        COLUMN_VERSION, COLUMN_IS_DARK,
        COLUMN_BG, COLUMN_RAIL, COLUMN_SURFACE, COLUMN_SURFACE_HIGH, COLUMN_BORDER,
        COLUMN_ACCENT, COLUMN_ACCENT_SOFT, COLUMN_ACCENT_FOCUSED, COLUMN_ON_ACCENT,
        COLUMN_TEXT_PRIMARY, COLUMN_TEXT_SECONDARY, COLUMN_TEXT_TERTIARY,
        COLUMN_DANGER, COLUMN_SUCCESS,
    )
}
