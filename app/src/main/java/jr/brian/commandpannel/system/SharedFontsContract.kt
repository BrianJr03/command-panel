package jr.brian.commandpannel.system

/**
 * The shared fonts contract, version 2: which fonts the applied look uses, read from [ThemeProvider] next to the
 * version 1 palette ([SharedThemeContract]). The full spec is `docs/SHARED_THEME.md` in the Command Panel repo.
 *
 * Constants only, with no Android or Command Panel imports, so a client can copy this file as is. Version 2 is frozen
 * the same way version 1 is: any change to the row means a new [VERSION].
 */
object SharedFontsContract {

    /** The contract version this file describes, also returned in [COLUMN_VERSION]. */
    const val VERSION = 2

    /** The fonts row: `content://<package>.theme/fonts`. Returns one row. */
    const val PATH = "fonts"

    /** An imported font's file: `content://<package>.theme/fonts/<id>`, opened read-only by a trusted app only. */
    const val FILE_PATH = "fonts"

    /** MIME type of an imported font file (TrueType or OpenType). */
    const val FILE_MIME_TYPE = "font/sfnt"

    // ---- columns ----

    /** Integer, always [VERSION]. */
    const val COLUMN_VERSION = "version"

    /** The applied look: a built-in look's name (`TERMINAL`) or `custom:<id>` for a saved look. */
    const val COLUMN_LOOK_ID = "look_id"

    /** The applied look's name as the user sees it: a saved look's own name, or a built-in look's display name. */
    const val COLUMN_LOOK_NAME = "look_name"

    /** Font for big numbers and titles, as a font value (see [PREFIX_BUNDLED] and [PREFIX_USER]), or null. */
    const val COLUMN_DISPLAY_FONT = "display_font"

    /** SHA-256 of the display font's file (lowercase hex) when it's a `user:` font, otherwise null. */
    const val COLUMN_DISPLAY_FONT_SHA256 = "display_font_sha256"

    /** Font for labels and body text, as a font value, or null. */
    const val COLUMN_LABEL_FONT = "label_font"

    /** SHA-256 of the label font's file (lowercase hex) when it's a `user:` font, otherwise null. */
    const val COLUMN_LABEL_FONT_SHA256 = "label_font_sha256"

    /** Every column, in the order a query without a projection returns them. */
    val COLUMNS = listOf(
        COLUMN_VERSION, COLUMN_LOOK_ID, COLUMN_LOOK_NAME,
        COLUMN_DISPLAY_FONT, COLUMN_DISPLAY_FONT_SHA256,
        COLUMN_LABEL_FONT, COLUMN_LABEL_FONT_SHA256,
    )

    // ---- font values ----

    /** `bundled:<key>`: one of the bundled families below. The client ships the same open font files. */
    const val PREFIX_BUNDLED = "bundled:"

    /** `user:<id>`: a font the user imported, read from [FILE_PATH]`/<id>`. */
    const val PREFIX_USER = "user:"

    /** Barlow Condensed SemiBold, Bold and ExtraBold. */
    const val BUNDLED_CONDENSED = "condensed"

    /** Barlow Condensed ExtraBold only, for big numbers. */
    const val BUNDLED_CONDENSED_DISPLAY = "condensed_display"

    /** JetBrains Mono Regular and Bold. */
    const val BUNDLED_MONO = "mono"

    /** DSEG7 Classic Bold, seven-segment digits. */
    const val BUNDLED_SEGMENT = "segment"

    /** Baloo 2 ExtraBold. */
    const val BUNDLED_ROUNDED = "rounded"

    /** Black Ops One. */
    const val BUNDLED_STENCIL = "stencil"

    /** Bangers. */
    const val BUNDLED_COMIC = "comic"

    /** Michroma. */
    const val BUNDLED_CHROME_WIDE = "chrome_wide"

    /** Literata Regular and Italic. */
    const val BUNDLED_SERIF = "serif"

    /** Every bundled key. A key is never renamed or reused; a new bundled family adds a key. */
    val BUNDLED_KEYS = listOf(
        BUNDLED_CONDENSED, BUNDLED_CONDENSED_DISPLAY, BUNDLED_MONO, BUNDLED_SEGMENT, BUNDLED_ROUNDED,
        BUNDLED_STENCIL, BUNDLED_COMIC, BUNDLED_CHROME_WIDE, BUNDLED_SERIF,
    )
}
