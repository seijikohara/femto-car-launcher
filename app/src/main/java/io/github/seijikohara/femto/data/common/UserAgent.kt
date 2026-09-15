package io.github.seijikohara.femto.data.common

import io.github.seijikohara.femto.BuildConfig

/**
 * The identifying User-Agent token every network client sends: an app name with
 * its version and a contact URL. The weather and geocoding hosts reject stock
 * or generic agents outright, and the keyless tile hosts can only reach out to
 * a project they can identify — an anonymous WebView origin leaves them no way
 * to raise a problem short of blocking it. One home, so the token cannot drift
 * between clients.
 */
internal val femtoUserAgent: String =
    "FemtoCarLauncher/" + BuildConfig.VERSION_NAME + " (+https://github.com/seijikohara/femto-car-launcher)"
