package jr.brian.commandpannel.plugin

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Which Panel plugins Command Panel lists, and which contract version it speaks with each. The contract is open, but
 * v1 only lists plugins signed with a key on [ALLOWED_CERTS]. The checks are pure so they can be unit tested; only
 * [signerDigests] touches Android.
 */
object PanelPluginTrust {

    /** The newest contract version this build speaks. */
    const val SUPPORTED_VERSION = PanelPluginContract.VERSION

    /** ThorKBM's package; its release key is the one allowlisted. */
    const val THORKBM_PACKAGE = "jr.brian.thorkbm"

    /** ThorKBM's release signing certificate (its `thor-ergo` key), from `apksigner verify --print-certs` on v2.2.1. */
    const val THORKBM_CERT_SHA256 = "988a63897e69f0e080d6a9cb8acf5a0d1e01deb6fd8c6337a33778b5cbf44379"

    /** SHA-256 digests of the signing certificates whose plugins are listed (lowercase hex). */
    val ALLOWED_CERTS: Set<String> = setOf(THORKBM_CERT_SHA256)

    /**
     * The plugin's `META_VERSION` meta-data as a version, or null when it's missing or not a positive integer. Accepts
     * an Int (`android:value="1"`) or a numeric String (`android:value="@string/..."`).
     */
    fun parseVersion(raw: Any?): Int? = when (raw) {
        is Int -> raw
        is String -> raw.trim().toIntOrNull()
        else -> null
    }?.takeIf { it > 0 }

    /**
     * The version a session with a plugin declaring [pluginVersion] uses: the plugin's own, or null when Command Panel
     * ignores the plugin (no valid version, or one newer than [supported]).
     */
    fun sessionVersion(pluginVersion: Int?, supported: Int = SUPPORTED_VERSION): Int? =
        pluginVersion?.takeIf { it in 1..supported }

    /** [digest] as lowercase hex without `:` separators or spaces, the form [ALLOWED_CERTS] uses. */
    fun normalizeDigest(digest: String): String = digest.filterNot { it == ':' || it.isWhitespace() }.lowercase()

    /** SHA-256 of a certificate's encoded bytes, as lowercase hex. */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * True when every current signer of a package is on [allowed]. A package with no signers, or with several where
     * any one is unknown, isn't trusted.
     */
    fun isTrusted(signerDigests: Collection<String>, allowed: Set<String> = ALLOWED_CERTS): Boolean =
        signerDigests.isNotEmpty() && signerDigests.all { normalizeDigest(it) in allowed }

    /**
     * SHA-256 digests of [info]'s current signers. [info] must come from a `getPackageInfo` / `getPackageArchiveInfo`
     * call with [PackageManager.GET_SIGNING_CERTIFICATES]; anything else gives an empty list (not trusted).
     */
    fun signerDigests(info: PackageInfo?): List<String> {
        val signing = info?.signingInfo ?: return emptyList()
        // With key rotation, signingCertificateHistory runs oldest first; the current signer is the last one.
        val current = if (signing.hasMultipleSigners()) signing.apkContentsSigners.orEmpty().toList()
        else listOfNotNull(signing.signingCertificateHistory?.lastOrNull())
        return current.map { sha256Hex(it.toByteArray()) }
    }

    /**
     * Whether a plugin is listed: signed only with allowlisted keys ([trusted]), or, in a debug Command Panel build
     * ([debugBuild]) only, a debuggable app ([debuggable]), so a debug plugin build gets a real tile for testing.
     */
    fun isListed(trusted: Boolean, debuggable: Boolean, debugBuild: Boolean): Boolean =
        trusted || (debugBuild && debuggable)

    /** True when installed [packageName] is signed only with allowlisted keys. */
    fun isTrustedPackage(pm: PackageManager, packageName: String, allowed: Set<String> = ALLOWED_CERTS): Boolean {
        val info = runCatching { pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
        return isTrusted(signerDigests(info), allowed)
    }
}
