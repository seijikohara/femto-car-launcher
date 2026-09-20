package io.github.seijikohara.femto.catalog

import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
internal data class CatalogAxisValue(
    val id: String,
    val label: String,
)

@Serializable
internal data class CatalogAxis(
    val id: String,
    val label: String,
    val values: List<CatalogAxisValue>,
)

@Serializable
internal data class CatalogManifestEntry(
    val id: String,
    val file: String,
    val widthDp: Int,
    val heightDp: Int,
    val values: Map<String, String>,
)

/**
 * The contract between the generator and the docs site (`.claude/rules/docs.md`):
 * the site reads nothing but this file plus the `img/` it points at.
 */
@Serializable
internal data class CatalogManifest(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val gitSha: String,
    val axes: List<CatalogAxis>,
    val entries: List<CatalogManifestEntry>,
)

internal val CatalogJson: Json =
    Json {
        prettyPrint = true
        // schemaVersion has a default value, which kotlinx.serialization drops
        // from the output unless told otherwise — and the site keys on it.
        encodeDefaults = true
    }

/** One cell of the catalog: a geometry rendered under one value of every other axis. */
internal data class CatalogEntry(
    val geometry: DashboardGeometry,
    val scale: UiScale,
    val darkTheme: Boolean,
    val driverSide: DriverSide,
    val dockPosition: DockPosition,
) {
    /** `<geometry>__<scale>__<theme>__<driver>__<dock>` — the file stem and the URL key on the site. */
    val id: String get() = values().values.joinToString(ID_SEPARATOR)

    val file: String get() = "img/$id.png"

    /** Axis id → value id, in axis order. */
    fun values(): Map<String, String> =
        linkedMapOf(
            CatalogMatrix.GEOMETRY to geometry.id,
            CatalogMatrix.SCALE to scale.slug,
            CatalogMatrix.THEME to if (darkTheme) DARK else LIGHT,
            CatalogMatrix.DRIVER to driverSide.slug,
            CatalogMatrix.DOCK to dockPosition.slug,
        )

    fun toManifestEntry(): CatalogManifestEntry =
        CatalogManifestEntry(
            id = id,
            file = file,
            widthDp = geometry.widthDp,
            heightDp = geometry.heightDp,
            values = values(),
        )

    // The parameterized runner names each case after this.
    override fun toString(): String = id

    private companion object {
        const val ID_SEPARATOR = "__"
        const val LIGHT = "light"
        const val DARK = "dark"
    }
}

/**
 * The axes of the screenshot catalog and their Cartesian product. Every other
 * dashboard setting (dock width, accent, panel visibility, glass, fonts) stays
 * at its default; add an axis here — never a second list — when the catalog
 * should vary it.
 */
internal object CatalogMatrix {
    const val GEOMETRY = "geometry"
    const val SCALE = "scale"
    const val THEME = "theme"
    const val DRIVER = "driver"
    const val DOCK = "dock"

    val axes: List<CatalogAxis> =
        listOf(
            CatalogAxis(GEOMETRY, "Geometry", DashboardGeometries.all.map { CatalogAxisValue(it.id, it.label) }),
            CatalogAxis(SCALE, "Display size", UiScale.entries.map { CatalogAxisValue(it.slug, it.label) }),
            CatalogAxis(THEME, "Theme", listOf(CatalogAxisValue("light", "Light"), CatalogAxisValue("dark", "Dark"))),
            CatalogAxis(DRIVER, "Driver side", DriverSide.entries.map { CatalogAxisValue(it.slug, it.label) }),
            CatalogAxis(DOCK, "Dock position", DockPosition.entries.map { CatalogAxisValue(it.slug, it.label) }),
        )

    /** Geometry outermost, dock innermost — the order the site's matrix view assumes. */
    val entries: List<CatalogEntry> =
        DashboardGeometries.all.flatMap { geometry ->
            UiScale.entries.flatMap { scale ->
                listOf(false, true).flatMap { darkTheme ->
                    DriverSide.entries.flatMap { driverSide ->
                        DockPosition.entries.map { dockPosition ->
                            CatalogEntry(geometry, scale, darkTheme, driverSide, dockPosition)
                        }
                    }
                }
            }
        }

    fun entries(filter: Regex?): List<CatalogEntry> =
        filter?.let { regex -> entries.filter { regex.containsMatchIn(it.id) } } ?: entries

    fun manifest(
        entries: List<CatalogEntry>,
        generatedAt: Instant,
        gitSha: String,
    ): CatalogManifest =
        CatalogManifest(
            generatedAt = generatedAt.toString(),
            gitSha = gitSha,
            axes = axes,
            entries = entries.map(CatalogEntry::toManifestEntry),
        )
}

// Enum names double as the slugs (`LARGE` → `large`) and, capitalised, as the labels.
private val Enum<*>.slug: String get() = name.lowercase()
private val Enum<*>.label: String get() = slug.replaceFirstChar(Char::uppercase)
