package io.github.seijikohara.femto.data.places

/**
 * A destination another app handed the launcher, through the share sheet or a
 * `geo:` link: a [Found] place, or [Unreadable] input the launcher could not
 * read a place from. The panel shows either; nothing routes by itself.
 */
internal sealed interface ReceivedPlace {
    /**
     * A [target] to show in the destination panel. [label] names a [PlaceTarget.Point] (the pin's name, or the
     * place's name the sharing app sent); a [PlaceTarget.Query] carries its text in the target and leaves the label
     * empty.
     */
    data class Found(
        val target: PlaceTarget,
        val label: String = "",
    ) : ReceivedPlace

    /** Input with no position and no readable name, such as a bare short link. */
    data object Unreadable : ReceivedPlace
}

/**
 * The most characters of one received field the launcher reads; the rest is
 * dropped. A place name or a share message fits well inside it, and the cap
 * keeps an oversized share from costing the home screen time.
 */
internal const val MAX_RECEIVED_TEXT_LENGTH = 2_000

/**
 * Read the `geo:` URI [uri] another app opened with the launcher.
 *
 * The URI is opaque (`android.net.Uri.getQueryParameter` throws on it), so it
 * is read by hand, after RFC 5870 and Android's common-intents forms:
 * `geo:lat,lon`, `geo:lat,lon?z=…`, `geo:lat,lon;u=…`, `geo:0,0?q=lat,lon`,
 * `geo:0,0?q=lat,lon(label)`, `geo:lat,lon?q=text` and `geo:0,0?q=text`.
 * `0,0` is the convention for "no position", so a `geo:0,0` without a usable
 * `q` is [ReceivedPlace.Unreadable].
 */
internal fun readGeoUri(uri: String): ReceivedPlace =
    geoPlaceOrNull(cleanReceived(uri).trim()) ?: ReceivedPlace.Unreadable

/**
 * Read a place from a share: [text] (`EXTRA_TEXT`), with [subject]
 * (`EXTRA_SUBJECT`), else [title] (`EXTRA_TITLE`), as the place's name.
 *
 * The text is searched, in order, for a `geo:` URI, a decimal "lat, lon" pair
 * in the prose, a pair in degrees, minutes and seconds (`33°50'44.2"N
 * 132°45'56.2"E`, in the prose or as the subject or title, the way a dropped
 * pin is shared), and a pair inside a link (`?q=lat,lon`, `?ll=lat,lon`,
 * `@lat,lon`). Without a position the name becomes a search text: the subject
 * if there is one, else the text's lines with the links taken out. A text of
 * links only, with no subject, is [ReceivedPlace.Unreadable]: links are never
 * opened, so a short link with no name says nothing the launcher can read.
 *
 * Free-text pairs need decimals on both sides ("10, 20 people" is no place),
 * and every position must lie in range. Every field is untrusted: each is cut
 * to [MAX_RECEIVED_TEXT_LENGTH] and stripped of control characters.
 */
internal fun readSharedText(
    text: String?,
    subject: String?,
    title: String?,
): ReceivedPlace {
    val body = text?.let(::cleanReceived).orEmpty()
    // The subject and the title without their links. A position in degrees, minutes and seconds there is coordinates,
    // not a name, so a field names the place only with what is left beside it.
    val fields = listOfNotNull(subject, title).map { oneLine(cleanReceived(it).replace(LinkToken, " ")) }
    val name = fields.firstNotNullOfOrNull { field -> oneLine(withoutDms(field)).takeIf { it.isNotEmpty() } }
    val links = LinkToken.findAll(body).map { it.value }.toList()
    val prose = body.replace(LinkToken, " ").replace(RepeatedSpaces, " ")
    val nameOrProse = name ?: linesAsName(prose)
    return geoLinkPlaceOrNull(links, nameOrProse)
        ?: prosePairPlaceOrNull(prose, name)
        ?: dmsPlaceOrNull(fields + prose, label = name ?: linesAsName(withoutDms(prose)))
        ?: linkPairPlaceOrNull(links, nameOrProse)
        ?: nameOrProse.takeIf { it.isNotEmpty() }?.let { ReceivedPlace.Found(PlaceTarget.Query(it)) }
        ?: ReceivedPlace.Unreadable
}

// The first geo: link in a share that reads as a place; a point it leaves unnamed takes [fallbackLabel].
private fun geoLinkPlaceOrNull(
    links: List<String>,
    fallbackLabel: String,
): ReceivedPlace.Found? =
    links
        .filter { it.startsWith(GEO_SCHEME, ignoreCase = true) }
        .firstNotNullOfOrNull(::geoPlaceOrNull)
        ?.let { found ->
            if (found.target is PlaceTarget.Point && found.label.isEmpty()) found.copy(label = fallbackLabel) else found
        }

// A decimal pair in the share's prose. The pair's own line is no name ("We are at 35.6, 139.7."), so the label is the
// subject, else the prose's other lines.
private fun prosePairPlaceOrNull(
    prose: String,
    name: String?,
): ReceivedPlace.Found? =
    DecimalPair.findAll(prose).firstNotNullOfOrNull { match ->
        pointOrNull(match.groupValues[1], match.groupValues[2])?.let { point ->
            val pairLine = prose.substring(0, match.range.first).count { it == '\n' }
            val otherLines = prose.lines().filterIndexed { index, _ -> index != pairLine }.joinToString("\n")
            ReceivedPlace.Found(point, label = name ?: linesAsName(otherLines))
        }
    }

// The first pair in degrees, minutes and seconds among [texts] that reads as a position.
private fun dmsPlaceOrNull(
    texts: List<String>,
    label: String,
): ReceivedPlace.Found? =
    texts
        .firstNotNullOfOrNull { text -> DmsPair.findAll(text).firstNotNullOfOrNull(::dmsPointOrNull) }
        ?.let { point -> ReceivedPlace.Found(point, label) }

// [text] with every pair in degrees, minutes and seconds that reads as a position taken out; a pair that does not
// (minutes past 59, say) stays, as text.
private fun withoutDms(text: String): String =
    DmsPair.replace(text) {
        if (dmsPointOrNull(it) !=
            null
        ) {
            " "
        } else {
            it.value
        }
    }

// A pair in degrees, minutes and seconds: one hemisphere letter on each side, before or after the number, S and W
// south and west of zero.
private fun dmsPointOrNull(match: MatchResult): PlaceTarget.Point? {
    val groups = match.groupValues
    val lat = signedDegreesOrNull(groups[1], groups[5], groups[2], groups[3], groups[4], negative = "S") ?: return null
    val lon = signedDegreesOrNull(groups[6], groups[10], groups[7], groups[8], groups[9], negative = "W") ?: return null
    return PlaceTarget.Point(lat, lon).takeIf { lat in -90.0..90.0 && lon in -180.0..180.0 }
}

private fun signedDegreesOrNull(
    letterBefore: String,
    letterAfter: String,
    degrees: String,
    minutes: String,
    seconds: String,
    negative: String,
): Double? {
    val hemisphere = listOf(letterBefore.trim(), letterAfter).filter { it.isNotEmpty() }.singleOrNull() ?: return null
    val whole = degrees.toIntOrNull() ?: return null
    val minute = minutes.toIntOrNull()?.takeIf { it < 60 } ?: return null
    val second = seconds.toDoubleOrNull()?.takeIf { it < 60.0 } ?: return null
    val value = whole + minute / 60.0 + second / 3_600.0
    return if (hemisphere == negative) -value else value
}

// A pair inside a link: a search or centre parameter, or the `@lat,lon` path step many map links carry.
private fun linkPairPlaceOrNull(
    links: List<String>,
    label: String,
): ReceivedPlace.Found? =
    links
        .filterNot { it.startsWith(GEO_SCHEME, ignoreCase = true) }
        .firstNotNullOfOrNull { link ->
            // Decoding can bring runs of spaces back (%20%20…); they go as in cleanReceived.
            val decoded = percentDecode(link, plusAsSpace = false).replace(RepeatedSpaces, " ")
            LinkPair.findAll(decoded).firstNotNullOfOrNull { match ->
                pointOrNull(match.groupValues[1], match.groupValues[2])
            }
        }?.let { point -> ReceivedPlace.Found(point, label) }

// The text's lines with the links already taken out, as one comma-separated line ("" when nothing is left).
private fun linesAsName(prose: String): String =
    prose
        .lines()
        .map(::oneLine)
        .filter { it.isNotEmpty() }
        .joinToString(", ")

private fun geoPlaceOrNull(uri: String): ReceivedPlace.Found? {
    if (!uri.startsWith(GEO_SCHEME, ignoreCase = true)) return null
    val rest = uri.substring(GEO_SCHEME.length).substringBefore('#')
    val position = pathPointOrNull(rest.substringBefore('?'))
    val q =
        rest
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.substringBefore('=').equals("q", ignoreCase = true) && '=' in it }
            ?.substringAfter('=')
    val fromQ = q?.let(::qPlaceOrNull)
    return when {
        fromQ is QPlace.At -> ReceivedPlace.Found(fromQ.point, fromQ.label)
        fromQ is QPlace.Text && position != null -> ReceivedPlace.Found(position, label = fromQ.text)
        fromQ is QPlace.Text -> ReceivedPlace.Found(PlaceTarget.Query(fromQ.text))
        position != null -> ReceivedPlace.Found(position)
        else -> null
    }
}

// The coordinates before any `;` parameter (`u=`, `crs=`) and the `?` query: latitude, longitude, an optional
// altitude. `0,0` means "no position" by the geo: convention, so it reads as none.
private fun pathPointOrNull(path: String): PlaceTarget.Point? =
    percentDecode(path, plusAsSpace = false)
        .substringBefore(';')
        .split(',')
        .takeIf { it.size in 2..3 }
        ?.let { parts -> pointOrNull(parts[0].trim(), parts[1].trim(), decimalsRequired = false) }
        ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }

private sealed interface QPlace {
    data class At(
        val point: PlaceTarget.Point,
        val label: String,
    ) : QPlace

    data class Text(
        val text: String,
    ) : QPlace

    // A `q` shaped like coordinates but out of range: neither a point nor a search text.
    data object Invalid : QPlace
}

// The `q` parameter: "lat,lon" with an optional "(label)", else search text. The label runs to the last closing
// parenthesis, so parentheses inside it (which geoHandoffUri escapes) stay part of it once decoded.
private fun qPlaceOrNull(raw: String): QPlace? {
    val decoded = oneLine(percentDecode(raw, plusAsSpace = true))
    return CoordinateQuery.matchEntire(decoded)?.let { match ->
        pointOrNull(match.groupValues[1], match.groupValues[2], decimalsRequired = false)
            ?.let { point -> QPlace.At(point, oneLine(match.groupValues[3])) }
            ?: QPlace.Invalid
    } ?: decoded.takeIf { it.isNotEmpty() }?.let(QPlace::Text)
}

// A position from two numbers in the plain decimal syntax (no exponent, NaN or Infinity), in range. Free text asks
// for decimals on both, so a count such as "10, 20" is no place. The parse is the null-returning one even after the
// syntax check: the text is untrusted, and a throw here would take down the home screen.
private fun pointOrNull(
    latitude: String,
    longitude: String,
    decimalsRequired: Boolean = true,
): PlaceTarget.Point? {
    val syntax = if (decimalsRequired) DecimalNumber else PlainNumber
    if (!syntax.matches(latitude) || !syntax.matches(longitude)) return null
    val lat = latitude.toDoubleOrNull() ?: return null
    val lon = longitude.toDoubleOrNull() ?: return null
    return PlaceTarget.Point(lat, lon).takeIf { lat in -90.0..90.0 && lon in -180.0..180.0 }
}

/**
 * Cut [text] to [MAX_RECEIVED_TEXT_LENGTH] (never leaving half a surrogate
 * pair), drop its control characters, keeping line breaks, which separate a
 * share's name from its link, and turn every other run of whitespace into one
 * space. A real share never needs more than one space in a row, and the
 * patterns below are written for exactly that.
 */
private fun cleanReceived(text: String): String =
    text
        .take(MAX_RECEIVED_TEXT_LENGTH)
        .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        .replace("\r\n", "\n")
        .mapNotNull { char ->
            when {
                char == '\n' || char == '\r' -> '\n'
                char.isWhitespace() -> ' '
                char.isISOControl() -> null
                else -> char
            }
        }.joinToString("")
        .replace(RepeatedSpaces, " ")

// One trimmed line, with runs of whitespace (line breaks included) as one space and no control characters: what a
// field or a label can show. Decoding can bring control characters back (%00), so decoded text passes through here.
private fun oneLine(text: String): String =
    text
        .map { char -> if (char.isISOControl() || char.isWhitespace()) ' ' else char }
        .joinToString("")
        .replace(RepeatedSpaces, " ")
        .trim()

/**
 * Decode `%XX` escapes as UTF-8, and `+` as a space when [plusAsSpace]. A `%`
 * not followed by two hexadecimal digits stays as it is; a byte sequence that is
 * no valid UTF-8 decodes to the replacement character.
 */
private fun percentDecode(
    text: String,
    plusAsSpace: Boolean,
): String =
    buildString {
        val bytes = java.io.ByteArrayOutputStream()
        val flush = {
            if (bytes.size() > 0) {
                append(String(bytes.toByteArray(), Charsets.UTF_8))
                bytes.reset()
            }
        }
        var index = 0
        while (index < text.length) {
            val high = text.getOrNull(index + 1)?.hexValueOrNull()
            val low = text.getOrNull(index + 2)?.hexValueOrNull()
            if (text[index] == '%' && high != null && low != null) {
                bytes.write(high * 16 + low)
                index += 3
            } else {
                flush()
                append(if (plusAsSpace && text[index] == '+') ' ' else text[index])
                index++
            }
        }
        flush()
    }

// ASCII hexadecimal only: Char.digitToIntOrNull also accepts other scripts' digits.
private fun Char.hexValueOrNull(): Int? =
    when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> null
    }

private const val GEO_SCHEME = "geo:"

// Every pattern below spells its character classes in ASCII ([0-9], [A-Za-z0-9_], a plain space), never the
// backslash shorthands for digits, word characters, word boundaries or whitespace. Android's regex engine (ICU)
// gives those shorthands their Unicode meaning: a digit of any script, which toDouble does not read, and every
// letter of every script (kana, kanji) as a word character, so a name glued to a link or a pair would read
// differently on the device. The JVM's shorthands are ASCII-only, so unit tests on the JVM would never see the
// difference; ReceivedDigitClassTest checks this file for the shorthands.
//
// The text these patterns read has been through cleanReceived or oneLine (or, for a decoded link, RepeatedSpaces), so
// its only whitespace is a single space or a line break. Every optional space in a pattern is therefore one space
// (` ?`), never a run (` *`), and no two optional spaces stand side by side with only optional tokens between them.
// Two runs side by side can split a long run of spaces between them in every way, and a backtracking engine tries
// each split: polynomial time on 2,000 characters of the home screen's main thread. Bounding each run to one space,
// rather than making it possessive, keeps the patterns readable and leaves nothing a run could hand back.
private val RepeatedSpaces = Regex(" {2,}")

// A link in shared text: any `scheme://…` or a `geo:` URI, up to the next space or line break. It may follow a
// letter of another script directly ("ここで会おうhttps://…"), which is not part of its scheme.
private val LinkToken = Regex("""(?<![A-Za-z0-9+.\-])(?:[A-Za-z][A-Za-z0-9+.\-]*://|[Gg][Ee][Oo]:)[^ \n]+""")

private val PlainNumber = Regex("""[-+]?[0-9]+(?:\.[0-9]+)?""")

private val DecimalNumber = Regex("""[-+]?[0-9]+\.[0-9]+""")

// "lat, lon" in prose: decimals on both, not glued to a longer ASCII number or word; a sentence's full stop, or a word
// of another script ("35.6586,139.7454付近"), may follow.
private val DecimalPair =
    Regex("""(?<![A-Za-z0-9_.])([-+]?[0-9]{1,2}\.[0-9]+) ?, ?([-+]?[0-9]{1,3}\.[0-9]+)(?![A-Za-z0-9_]|\.[0-9])""")

// Degrees, minutes and seconds: `°`, `'` or `′`, `"` or `″`, seconds with an optional decimal part, a hemisphere letter
// before or after each number, the two halves apart by a space, a comma, or neither. Groups: the latitude's letter
// before, its degrees, minutes, seconds, its letter after; then the same for the longitude. A letter after a number
// takes the space before it inside its own group, which needs the letter, so the space before the separator belongs
// to one token only: the letter's group when a letter follows, else the separator.
//
// A hemisphere letter stands alone: the pair does not start inside an ASCII word, and a letter after a number has no
// ASCII letter behind it. Otherwise the S of "GPS 35°…N" would be the latitude's second letter, the real pair would
// be passed over, and the E of "… Exit 5" would be an east the share never said.
private val DmsPair =
    Regex(
        """(?<![0-9.A-Za-z])([NS] ?)?([0-9]{1,2})° ?([0-9]{1,2})['′] ?([0-9]{1,2}(?:\.[0-9]+)?)["″]""" +
            """(?: ?([NS])(?![A-Za-z]))?(?: ?, ?| )?""" +
            """([EW] ?)?([0-9]{1,3})° ?([0-9]{1,2})['′] ?([0-9]{1,2}(?:\.[0-9]+)?)["″](?: ?([EW])(?![A-Za-z]))?""",
    )

// A pair inside a decoded link: `?q=` / `&q=`, `?ll=` / `&ll=`, or `@`.
private val LinkPair =
    Regex("""(?:[?&](?:q|ll)=|@) ?([-+]?[0-9]{1,2}\.[0-9]+) ?, ?([-+]?[0-9]{1,3}\.[0-9]+)(?![0-9])""")

// The whole of a geo: `q`, as one trimmed line (oneLine): "lat,lon" and an optional "(label)".
private val CoordinateQuery =
    Regex(
        """([-+]?[0-9]+(?:\.[0-9]+)?) ?, ?([-+]?[0-9]+(?:\.[0-9]+)?)(?: ?\((.*)\))?""",
        RegexOption.DOT_MATCHES_ALL,
    )
