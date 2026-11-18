package io.github.seijikohara.femto.data.map.offline

/**
 * Return true when every letter in [text] belongs to the Latin script.
 *
 * Non-letters are ignored, so digits-only and empty texts count as Latin. This mirrors the rule of the generator behind
 * the online tiles; a Unicode-name check would classify fullwidth Latin letters differently.
 */
internal fun isLatinText(text: String): Boolean {
    // An index loop instead of a stream: this runs per labelled feature, so it must not allocate. Most labels are
    // ASCII, where a letter is always Latin, so the script lookup is skipped below 0x80.
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (codePoint >= ASCII_LIMIT &&
            Character.isLetter(codePoint) &&
            Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN
        ) {
            return false
        }
        index += Character.charCount(codePoint)
    }
    return true
}

private const val ASCII_LIMIT = 0x80

/**
 * Derive the OpenMapTiles name properties that Shortbread omits.
 *
 * The map styles read `name:latin` and `name:nonlatin`, so the split is decided by the script of [name]. This runs for
 * every labelled feature in a tile, hence no regex and no intermediate maps.
 */
internal fun nameProperties(
    name: String?,
    nameEn: String?,
    nameDe: String?,
): List<TileProperty> =
    if (name.isNullOrEmpty()) {
        emptyList()
    } else {
        val en = nameEn.takeUnless { it.isNullOrEmpty() } ?: name
        val de = nameDe.takeUnless { it.isNullOrEmpty() } ?: name
        val nameIsLatin = isLatinText(name)
        val latin =
            when {
                nameIsLatin -> name
                en != name && isLatinText(en) -> en
                else -> null
            }
        buildList {
            add(textProperty("name", name))
            add(textProperty("name_en", en))
            add(textProperty("name_de", de))
            if (!nameIsLatin) add(textProperty("name:nonlatin", name))
            latin?.let { add(textProperty("name:latin", it)) }
            add(textProperty("name_int", latin ?: name))
            if (en != name) add(textProperty("name:en", en))
        }
    }

private fun textProperty(
    key: String,
    value: String,
): TileProperty = TileProperty(key, TileValue.Text(value))
