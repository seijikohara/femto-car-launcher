package io.github.seijikohara.femto.data

import io.github.seijikohara.femto.data.NominatimApi.NominatimAddress

/**
 * Compose a readable [ShortAddress] from a Nominatim structured address.
 *
 * This object is the single source of truth for address composition. It never
 * trims `display_name`; it reads the structured slots, picks the first
 * non-blank candidate per slot, de-duplicates overlapping values (suburb /
 * neighbourhood / quarter commonly repeat), and orders the slots by
 * [NominatimAddress.countryCode]:
 *
 * - East-Asian markets emit slots LARGE-to-SMALL with no separator
 *   (e.g. "東京都新宿区新宿三丁目").
 * - Other markets emit slots SMALL-to-LARGE joined with ", "
 *   (e.g. "1600 Amphitheatre Parkway, Mountain View, CA").
 */
internal object AddressComposer {
    fun composeAddress(address: NominatimAddress): ShortAddress? =
        if (address.countryCode?.lowercase() in EAST_ASIAN_COUNTRIES) {
            composeEastAsian(address)
        } else {
            composeWestern(address)
        }

    private fun composeEastAsian(address: NominatimAddress): ShortAddress? {
        val used = mutableSetOf<String>()
        val prefecture =
            firstUnused(
                used,
                address.state,
                address.province,
                address.region,
                address.stateDistrict,
            ) ?: jpPrefecture(address.isoLvl4)?.also { used += it }
        val municipality =
            firstUnused(
                used,
                address.city,
                address.town,
                address.municipality,
                address.village,
                address.county,
            )
        val subWard = firstUnused(used, address.cityDistrict, address.suburb, address.district, address.borough)
        val district = firstUnused(used, address.quarter, address.neighbourhood, address.suburb, address.town)
        val chome = firstUnused(used, address.neighbourhood, address.quarter, address.cityBlock, address.residential)
        // `road` is intentionally NOT used for East-Asian addresses: Japanese
        // addresses are administrative (prefecture→ward→district→chome→banchi)
        // and do not use street names, so OSM's `road` here is usually a POI /
        // landmark / station (e.g. "JR新宿駅;1階;15番線") that reads as noise. The
        // banchi (`house_number`) is kept when present.
        val houseNumber = firstUnused(used, address.houseNumber)

        // Nothing meaningful to show when neither a municipality nor a
        // prefecture resolves.
        if (municipality == null && prefecture == null) return null

        // The fine-grained place slots overlap in Japan: Nominatim reports a
        // coarse name ("新宿", quarter) and a finer one that contains it
        // ("新宿三丁目", neighbourhood) in adjacent slots. Drop any place name
        // that is a substring of a more specific sibling so the line keeps only
        // the most specific value (yields "新宿三丁目", not "新宿新宿三丁目").
        val fine = dropContained(listOfNotNull(subWard, district, chome))

        val line = (listOfNotNull(prefecture, municipality) + fine + listOfNotNull(houseNumber)).joinToString("")
        return ShortAddress(
            locality = municipality.orEmpty(),
            region = prefecture,
            line = line,
        )
    }

    // Remove any value that is a substring of another value in the list,
    // preserving the original order of the survivors.
    private fun dropContained(values: List<String>): List<String> =
        values.filter { candidate ->
            values.none { other -> other != candidate && other.contains(candidate) }
        }

    private fun composeWestern(address: NominatimAddress): ShortAddress? {
        val used = mutableSetOf<String>()
        val street =
            listOfNotNull(address.houseNumber, address.road)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null }
                ?.also { used += it }
        val city =
            firstUnused(
                used,
                address.city,
                address.town,
                address.village,
                address.municipality,
            )
        val state =
            isoSuffix(address.isoLvl4)?.also { used += it }
                ?: firstUnused(used, address.state, address.province, address.region)

        // Nothing meaningful to show when neither a city nor a state resolves.
        if (city == null && state == null) return null

        val line = listOfNotNull(street, city, state).joinToString(", ")
        return ShortAddress(
            locality = city.orEmpty(),
            region = state,
            line = line,
        )
    }

    // Return the first candidate that is non-blank and not already emitted, and
    // record it as used so a later slot does not repeat the same value.
    private fun firstUnused(
        used: MutableSet<String>,
        vararg candidates: String?,
    ): String? =
        candidates
            .firstOrNull { !it.isNullOrBlank() && it !in used }
            ?.also { used += it }

    private fun jpPrefecture(isoLvl4: String?): String? = JP_PREFECTURES[isoLvl4]

    // "US-CA" -> "CA"; null when the code carries no "-" suffix.
    private fun isoSuffix(isoLvl4: String?): String? =
        isoLvl4
            ?.substringAfter('-', "")
            ?.takeIf { it.isNotBlank() }
}

private val EAST_ASIAN_COUNTRIES = setOf("jp", "cn", "kr", "tw", "hk", "mo")

// ISO 3166-2:JP -> prefecture name in kanji. Nominatim omits the prefecture
// from the structured keys for Japanese addresses, so recover it from the
// "ISO3166-2-lvl4" code (e.g. "JP-13" -> "東京都").
private val JP_PREFECTURES =
    mapOf(
        "JP-01" to "北海道",
        "JP-02" to "青森県",
        "JP-03" to "岩手県",
        "JP-04" to "宮城県",
        "JP-05" to "秋田県",
        "JP-06" to "山形県",
        "JP-07" to "福島県",
        "JP-08" to "茨城県",
        "JP-09" to "栃木県",
        "JP-10" to "群馬県",
        "JP-11" to "埼玉県",
        "JP-12" to "千葉県",
        "JP-13" to "東京都",
        "JP-14" to "神奈川県",
        "JP-15" to "新潟県",
        "JP-16" to "富山県",
        "JP-17" to "石川県",
        "JP-18" to "福井県",
        "JP-19" to "山梨県",
        "JP-20" to "長野県",
        "JP-21" to "岐阜県",
        "JP-22" to "静岡県",
        "JP-23" to "愛知県",
        "JP-24" to "三重県",
        "JP-25" to "滋賀県",
        "JP-26" to "京都府",
        "JP-27" to "大阪府",
        "JP-28" to "兵庫県",
        "JP-29" to "奈良県",
        "JP-30" to "和歌山県",
        "JP-31" to "鳥取県",
        "JP-32" to "島根県",
        "JP-33" to "岡山県",
        "JP-34" to "広島県",
        "JP-35" to "山口県",
        "JP-36" to "徳島県",
        "JP-37" to "香川県",
        "JP-38" to "愛媛県",
        "JP-39" to "高知県",
        "JP-40" to "福岡県",
        "JP-41" to "佐賀県",
        "JP-42" to "長崎県",
        "JP-43" to "熊本県",
        "JP-44" to "大分県",
        "JP-45" to "宮崎県",
        "JP-46" to "鹿児島県",
        "JP-47" to "沖縄県",
    )
