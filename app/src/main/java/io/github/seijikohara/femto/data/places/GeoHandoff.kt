package io.github.seijikohara.femto.data.places

import java.util.Locale

/**
 * Return the `geo:` URI that hands [target] to whichever navigation app the
 * user has elected; no provider is named, so any app that handles `geo:`
 * resolves it.
 *
 * - A [PlaceTarget.Query] becomes `geo:0,0?q=<query>`: the `0,0` origin tells
 *   the app to search for the query rather than centre on a position.
 * - A [PlaceTarget.Point] becomes `geo:<lat>,<lng>?q=<lat>,<lng>(<label>)`, the
 *   form that drops a labelled pin; a blank [label] drops the parentheses.
 *
 * The query and the label are percent-encoded as UTF-8 with everything but the
 * RFC 3986 unreserved characters escaped. The `geo:` handlers split the query
 * on `&` and `#`, and read `(` and `)` as the label's delimiters, so a raw one
 * of any of these would cut the destination short.
 */
internal fun geoHandoffUri(
    target: PlaceTarget,
    label: String = "",
): String =
    when (target) {
        is PlaceTarget.Query -> {
            "geo:0,0?q=${percentEncode(target.text)}"
        }

        is PlaceTarget.Point -> {
            val position = "${coordinate(target.latitude)},${coordinate(target.longitude)}"
            val pinLabel = label
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "(${percentEncode(it)})" }
                .orEmpty()
            "geo:$position?q=$position$pinLabel"
        }
    }

// The one coordinate formatter of every geo: URI the app builds. Six decimals
// is about 0.1 m, finer than any fix. A fixed format keeps a small value out
// of scientific notation ("1.0E-4"), and Locale.ROOT keeps the decimal
// separator a dot on devices set to a comma-decimal locale.
private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

private fun percentEncode(text: String): String =
    text.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (code < 0x80 && char in UNRESERVED) char.toString() else "%%%02X".format(Locale.ROOT, code)
    }

/**
 * Return the `geo:` URI that opens the user's maps app centred on a position
 * at [zoom] (the dashboard's "open maps here"). Its coordinates share
 * [geoHandoffUri]'s formatter.
 */
internal fun geoCentreUri(
    latitude: Double,
    longitude: Double,
    zoom: Int,
): String = "geo:${coordinate(latitude)},${coordinate(longitude)}?z=$zoom"
