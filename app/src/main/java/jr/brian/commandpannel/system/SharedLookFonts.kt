package jr.brian.commandpannel.system

import androidx.compose.ui.text.font.FontFamily
import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.ui.theme.BundledFont
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.specForLook

/** The contract key for [font], or `null` for the system font. */
internal fun sharedBundledKey(font: BundledFont): String? = when (font) {
    BundledFont.DEFAULT -> null
    BundledFont.CONDENSED -> SharedFontsContract.BUNDLED_CONDENSED
    BundledFont.CONDENSED_DISPLAY -> SharedFontsContract.BUNDLED_CONDENSED_DISPLAY
    BundledFont.MONO -> SharedFontsContract.BUNDLED_MONO
    BundledFont.SEGMENT -> SharedFontsContract.BUNDLED_SEGMENT
    BundledFont.ROUNDED -> SharedFontsContract.BUNDLED_ROUNDED
    BundledFont.STENCIL -> SharedFontsContract.BUNDLED_STENCIL
    BundledFont.COMIC -> SharedFontsContract.BUNDLED_COMIC
    BundledFont.CHROME_WIDE -> SharedFontsContract.BUNDLED_CHROME_WIDE
    BundledFont.SERIF -> SharedFontsContract.BUNDLED_SERIF
}

/** A built-in look's family as a [FontRef], or `null` when it uses the system font. */
private fun FontFamily?.asBundledRef(): FontRef? =
    this?.let { family -> BundledFont.entries.firstOrNull { it.family == family } }?.let { FontRef.Bundled(it) }

/** The look's label and display font choices before resolving, the way `CustomLook.toSpec` layers them. */
private fun SharedLook.fontRefs(): Pair<FontRef?, FontRef?> {
    val base = specForLook(custom?.baseLook ?: builtIn ?: TccUiLook.DEFAULT).traits
    val label = custom?.labelFont ?: base.labelFontFamily.asBundledRef()
    val display = custom?.displayFont ?: base.displayFontFamily.asBundledRef()
    return label to display
}

/** One font role's published value and, for an imported font, its file's SHA-256. */
private data class SharedFontValue(val value: String, val sha256: String?)

/** `null` for the system font or an imported font whose file is gone, which the overlay also draws in the system font. */
private fun FontRef?.toShared(userFontSha256: (String) -> String?): SharedFontValue? = when (this) {
    null -> null
    is FontRef.Bundled -> sharedBundledKey(font)?.let { SharedFontValue(SharedFontsContract.PREFIX_BUNDLED + it, null) }
    is FontRef.User -> userFontSha256(id)?.let { SharedFontValue(SharedFontsContract.PREFIX_USER + id, it) }
}

/**
 * The fonts row for [look]. A role without its own font falls back to the label font, as the overlay's text styles do,
 * so a client never has to know the fallback rule. [userFontSha256] gives an imported font's digest, or `null` when its
 * file is gone. [builtInName] gives a built-in look's display name.
 */
internal fun sharedFontsRow(
    look: SharedLook,
    builtInName: (TccUiLook) -> String,
    userFontSha256: (String) -> String?,
): Map<String, Any?> {
    val (labelRef, displayRef) = look.fontRefs()
    val label = labelRef.toShared(userFontSha256)
    val display = displayRef.toShared(userFontSha256) ?: label
    return mapOf(
        SharedFontsContract.COLUMN_VERSION to SharedFontsContract.VERSION,
        SharedFontsContract.COLUMN_LOOK_ID to look.id,
        SharedFontsContract.COLUMN_LOOK_NAME to (look.custom?.name ?: builtInName(look.builtIn ?: TccUiLook.DEFAULT)),
        SharedFontsContract.COLUMN_DISPLAY_FONT to display?.value,
        SharedFontsContract.COLUMN_DISPLAY_FONT_SHA256 to display?.sha256,
        SharedFontsContract.COLUMN_LABEL_FONT to label?.value,
        SharedFontsContract.COLUMN_LABEL_FONT_SHA256 to label?.sha256,
    )
}

/** [row]'s values for [columns], in that order. Values may be null; an unknown column is the caller's error. */
internal fun selectSharedColumns(row: Map<String, Any?>, columns: Array<out String>): Array<Any?> =
    Array(columns.size) { i ->
        val column = columns[i]
        require(row.containsKey(column)) { "unknown column: $column" }
        row[column]
    }

// UserFontStore names files by UUID; anything else (dots, slashes, encoded separators) is refused outright.
private val USER_FONT_ID = Regex("[0-9A-Fa-f-]{1,64}")

/** The imported font id a `fonts/<id>` path asks for, or `null` for any other path. */
internal fun sharedFontFileId(pathSegments: List<String>): String? =
    pathSegments.takeIf { it.size == 2 && it[0] == SharedFontsContract.FILE_PATH }
        ?.get(1)?.takeIf { USER_FONT_ID.matches(it) }

/**
 * The apps allowed to open imported font files, by package and pinned signing certificate (SHA-256, lowercase hex).
 * Only Jarngreipr's release build in a release Command Panel; a debug Command Panel also serves Jarngreipr's debug build.
 */
internal object SharedFontReaders {
    const val JARN_PACKAGE = "jr.brian.home"
    const val JARN_DEBUG_PACKAGE = "jr.brian.home.debug"

    /** Jarngreipr's release key, from `apksigner verify --print-certs`. */
    const val JARN_RELEASE_CERT_SHA256 = "a9a6e4628ff67b218be69f39dd4c7843cdcbd565f76b31f93f0c90f3433d56db"

    /** Jarngreipr's committed `app/debug.keystore`. */
    const val JARN_DEBUG_CERT_SHA256 = "d954c2d6139facf3acf555a6e28664e15b0b8f8e93b5339ab8e1fc9c001419a6"

    private val RELEASE = mapOf(JARN_PACKAGE to JARN_RELEASE_CERT_SHA256)
    private val DEBUG = RELEASE + (JARN_DEBUG_PACKAGE to JARN_DEBUG_CERT_SHA256)

    fun allowed(debugBuild: Boolean): Map<String, String> = if (debugBuild) DEBUG else RELEASE

    /**
     * True when one of the calling UID's packages is on [allowed] and every current signer of that package matches its
     * pin. [signerDigests] reads a package's signers; an empty list (not installed, unreadable) is never trusted.
     */
    fun isTrusted(
        callerPackages: Collection<String>,
        allowed: Map<String, String>,
        signerDigests: (String) -> List<String>,
    ): Boolean = callerPackages.any { pkg ->
        val pin = allowed[pkg] ?: return@any false
        PanelPluginTrust.isTrusted(signerDigests(pkg), setOf(pin))
    }
}
