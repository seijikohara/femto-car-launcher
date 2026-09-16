package io.github.seijikohara.femto.ui.settings

/**
 * An external document a Settings row links out to. The rows name the document,
 * not a URL: resolving one to a URL and opening a browser is the host activity's
 * job (`MainActivity`), which is also the single home for the addresses.
 */
internal enum class SettingsDocument {
    /** The project's privacy policy (`PRIVACY.md`). */
    PRIVACY_POLICY,

    /**
     * The application's terms of service (`TERMS.md`) — the notices Google Maps
     * Platform requires a Customer Application's terms to carry.
     */
    TERMS,

    /**
     * Google's own Maps Platform terms, linked beside the API-key row so the
     * person attaching a billing account can read them before doing so.
     */
    GOOGLE_MAPS_PLATFORM_TERMS,
}
