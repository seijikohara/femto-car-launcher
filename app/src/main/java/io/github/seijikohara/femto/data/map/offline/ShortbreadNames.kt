package io.github.seijikohara.femto.data.map.offline

/**
 * Return true when every letter in [text] belongs to the Latin script.
 *
 * Non-letters are ignored, so digits-only and empty texts count as Latin. This mirrors the rule of the generator behind
 * the online tiles; a Unicode-name check would classify fullwidth Latin letters differently.
 */
internal fun isLatinText(text: String): Boolean =
    text.codePoints().allMatch { codePoint ->
        !Character.isLetter(codePoint) || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN
    }

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
