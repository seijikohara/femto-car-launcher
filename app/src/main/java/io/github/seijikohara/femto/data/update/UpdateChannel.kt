package io.github.seijikohara.femto.data.update

/**
 * The release channel a build follows. A build reads only its own channel's
 * feed and acts only on a manifest naming that same channel: the nightly APK
 * carries a different application id, so it could never replace a stable
 * install anyway.
 */
internal enum class UpdateChannel(
    /** The product-flavor name, and the `channel` value of this channel's manifest. */
    val id: String,
) {
    STABLE("stable"),
    NIGHTLY("nightly"),
    ;

    companion object {
        /**
         * The channel a build of [flavor] (`BuildConfig.FLAVOR`) follows, or null
         * for a flavor with no feed of its own — a second flavor dimension would
         * turn the combined name into e.g. "stableFoss".
         */
        fun fromFlavorOrNull(flavor: String): UpdateChannel? = entries.firstOrNull { it.id == flavor }
    }
}
