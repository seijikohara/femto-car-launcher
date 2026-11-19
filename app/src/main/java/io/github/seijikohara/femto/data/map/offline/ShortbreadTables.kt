package io.github.seijikohara.femto.data.map.offline

// Lookup tables that map Shortbread kinds onto OpenMapTiles classes; ShortbreadConverter is the only reader.

private fun classTable(vararg groups: Pair<String, List<String>>): Map<String, String> =
    groups.flatMap { (targetClass, kinds) -> kinds.map { it to targetClass } }.toMap()

internal val RoadClasses: Map<String, String> =
    classTable(
        "motorway" to listOf("motorway"),
        "trunk" to listOf("trunk"),
        "primary" to listOf("primary"),
        "secondary" to listOf("secondary"),
        "tertiary" to listOf("tertiary"),
        "minor" to listOf("unclassified", "residential", "living_street", "road"),
        "service" to listOf("service"),
        "track" to listOf("track"),
        "path" to listOf("pedestrian", "footway", "cycleway", "path", "steps", "bridleway"),
        "busway" to listOf("busway"),
        "bus_guideway" to listOf("bus_guideway"),
        "raceway" to listOf("raceway"),
        "rail" to listOf("rail", "narrow_gauge", "preserved", "funicular"),
        "transit" to listOf("light_rail", "subway", "tram", "monorail"),
    )

/** Classes whose `subclass` carries the original kind on `transportation` features. */
internal val RoadClassesWithSubclass: Set<String> = setOf("path", "rail", "transit")

internal val RoadMinZoom: Map<String, Int> =
    mapOf("tertiary" to 11, "minor" to 12, "service" to 13, "track" to 13, "path" to 13)

internal val RoadNameOrder: List<String> =
    listOf("motorway", "trunk", "primary", "secondary", "tertiary", "minor", "service", "track", "path")

internal val AerowayKinds: Set<String> = setOf("runway", "taxiway", "apron", "helipad")

internal val WaterClasses: Map<String, String> =
    classTable(
        "lake" to listOf("water", "reservoir", "basin"),
        "river" to listOf("river", "canal"),
        "dock" to listOf("dock"),
    )

internal const val DEFAULT_WATER_CLASS = "lake"

internal val LandcoverClasses: Map<String, String> =
    classTable(
        "wood" to listOf("forest", "wood"),
        "grass" to
            listOf(
                "grass",
                "grassland",
                "meadow",
                "heath",
                "scrub",
                "park",
                "garden",
                "village_green",
                "recreation_ground",
                "golf_course",
                "miniature_golf",
            ),
        "farmland" to
            listOf(
                "farmland",
                "orchard",
                "vineyard",
                "greenhouse_horticulture",
                "plant_nursery",
                "farmyard",
                "allotments",
            ),
        "sand" to listOf("beach", "sand", "dune"),
        "rock" to listOf("bare_rock", "scree", "shingle"),
        "wetland" to listOf("marsh", "swamp", "wet_meadow", "bog", "wetland", "reedbed"),
    )

internal val LanduseClasses: Map<String, String> =
    listOf(
        "residential",
        "industrial",
        "commercial",
        "retail",
        "cemetery",
        "quarry",
        "landfill",
        "military",
        "railway",
        "garages",
        "playground",
        "pitch",
        "school",
        "university",
        "college",
        "kindergarten",
        "hospital",
    ).associateWith { it } + ("grave_yard" to "cemetery")

/** An OpenMapTiles place class and the `capital` level it carries, when the Shortbread kind is a capital. */
internal data class PlaceClass(
    val placeClass: String,
    val capital: Long? = null,
)

internal val PlaceClasses: Map<String, PlaceClass> =
    listOf(
        "city",
        "town",
        "village",
        "hamlet",
        "suburb",
        "quarter",
        "neighbourhood",
        "isolated_dwelling",
        "island",
    ).associateWith { PlaceClass(it) } +
        mapOf(
            "capital" to PlaceClass("city", capital = 2),
            "state_capital" to PlaceClass("city", capital = 4),
            "farm" to PlaceClass("isolated_dwelling"),
        )

internal val PlaceMinZoom: Map<String, Int> =
    mapOf("suburb" to 11, "hamlet" to 11, "quarter" to 13, "neighbourhood" to 13, "isolated_dwelling" to 14)

// A primitive array: the rank loop runs per place and must not box the thresholds.
internal val CityRankPopulation: LongArray =
    longArrayOf(5_000_000, 2_000_000, 1_000_000, 500_000, 250_000, 100_000, 50_000, 25_000, 10_000)

internal val PlaceRankOffset: Map<String, Int> =
    mapOf(
        "town" to 10,
        "village" to 12,
        "island" to 12,
        "suburb" to 12,
        "hamlet" to 14,
        "quarter" to 14,
        "neighbourhood" to 15,
        "isolated_dwelling" to 16,
    )

internal val PlaceOrder: List<String> =
    listOf(
        "country",
        "state",
        "province",
        "city",
        "town",
        "village",
        "island",
        "suburb",
        "hamlet",
        "quarter",
        "neighbourhood",
        "isolated_dwelling",
    )

internal const val STATE_MIN_AREA = 5_000_000.0

internal const val BUILDING_HEIGHT = 5L

/** Return the country rank for a boundary polygon of [wayArea] square metres: 1 is the largest. */
internal fun countryRank(wayArea: Double): Long =
    when {
        wayArea >= 110_000_000.0 -> 1
        wayArea >= 10_000_000.0 -> 2
        else -> 3
    }
