package io.github.seijikohara.femto.catalog

/**
 * The knobs `generateCatalog` passes to the generator JVM as system properties.
 * Paths are relative to the module directory, which is the JVM's working
 * directory under Gradle — the same convention the goldens use.
 */
internal object CatalogRun {
    const val OUTPUT_DIR = "build/outputs/catalog"
    const val MANIFEST_FILE = "manifest.json"

    /** `-Pfemto.catalog.filter=<regex>`: render only the entries whose id contains a match. */
    val filter: Regex?
        get() = System.getProperty("femto.catalog.filter")?.takeIf { it.isNotBlank() }?.let(::Regex)

    /** The commit the catalog was rendered from, for the manifest; "unknown" outside Gradle. */
    val gitSha: String
        get() = System.getProperty("femto.catalog.gitSha")?.takeIf { it.isNotBlank() } ?: "unknown"
}
