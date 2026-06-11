package io.github.seijikohara.femto.data.fonts

import java.io.File

/**
 * A downloaded typeface on disk. [Variable] is one file driving every weight via
 * the `wght` axis; [Static] is a weight-keyed set of single-weight files.
 */
internal sealed interface CachedFont {
    data class Variable(
        val file: File,
    ) : CachedFont

    data class Static(
        val fileByWeight: Map<Int, File>,
    ) : CachedFont
}

/**
 * Disk cache for downloaded Google Fonts under `<filesDir>/google_fonts/`.
 *
 * Each family owns one slug directory holding either `variable.ttf` or a set of
 * `w<weight>.ttf` files. The layout lets [cached] rebuild a family offline on
 * the next launch with no network round-trip, and lets [evictExcept] drop the
 * directories of families the user no longer selects.
 */
internal class FontCache(
    private val root: File,
    private val api: GoogleFontsApi,
) {
    /** Return the on-disk font for [family] without touching the network. */
    fun cached(family: String): CachedFont? {
        val dir = dirFor(family)
        if (!dir.isDirectory) return null
        val variable = File(dir, VARIABLE_FILE)
        if (variable.isFile) return CachedFont.Variable(variable)
        val statics =
            dir
                .listFiles { file -> file.name.startsWith(STATIC_PREFIX) && file.name.endsWith(".ttf") }
                .orEmpty()
                .mapNotNull { file ->
                    file.name
                        .removePrefix(STATIC_PREFIX)
                        .removeSuffix(".ttf")
                        .toIntOrNull()
                        ?.let { it to file }
                }.toMap()
        return statics.takeIf { it.isNotEmpty() }?.let(CachedFont::Static)
    }

    /**
     * Return [family] from cache, downloading it first when absent. A failed
     * download yields null so the caller falls back to the system font.
     */
    suspend fun ensure(family: String): CachedFont? =
        cached(family) ?: run {
            when (val plan = api.plan(family)) {
                is FontDownloadPlan.Variable -> downloadVariable(family, plan)
                is FontDownloadPlan.Static -> downloadStatic(family, plan)
                null -> null
            }
        }

    /**
     * Delete the cached directories of every family outside [keep].
     * [alsoProtect] shields families whose download is still in flight — a fast
     * re-selection cancels the previous resolve pass mid-download, and evicting
     * its directory here would delete the file the cancelled pass is writing.
     */
    fun evictExcept(
        keep: Collection<String>,
        alsoProtect: Collection<String> = emptySet(),
    ) {
        val keepDirs = (keep + alsoProtect).map { dirFor(it).name }.toSet()
        root.listFiles().orEmpty().forEach { dir ->
            if (dir.isDirectory && dir.name !in keepDirs) dir.deleteRecursively()
        }
    }

    private suspend fun downloadVariable(
        family: String,
        plan: FontDownloadPlan.Variable,
    ): CachedFont? {
        val target = File(dirFor(family), VARIABLE_FILE)
        return CachedFont.Variable(target).takeIf { api.downloadTo(plan.url, target) }
    }

    private suspend fun downloadStatic(
        family: String,
        plan: FontDownloadPlan.Static,
    ): CachedFont? {
        val files =
            plan.urlByWeight
                .mapNotNull { (weight, url) ->
                    val target = File(dirFor(family), "$STATIC_PREFIX$weight.ttf")
                    (weight to target).takeIf { api.downloadTo(url, target) }
                }.toMap()
        return files.takeIf { it.isNotEmpty() }?.let(CachedFont::Static)
    }

    private fun dirFor(family: String): File = File(root, slug(family))

    private fun slug(family: String): String = family.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private companion object {
        const val VARIABLE_FILE = "variable.ttf"
        const val STATIC_PREFIX = "w"
    }
}
