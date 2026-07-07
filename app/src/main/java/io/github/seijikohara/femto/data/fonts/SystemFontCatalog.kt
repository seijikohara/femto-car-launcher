package io.github.seijikohara.femto.data.fonts

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.SystemFonts
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private const val TAG = "SystemFontCatalog"

private const val NORMAL_WEIGHT = 400

// Representative characters used to probe glyph coverage on the family's
// representative file. Latin checks both an upper- and lowercase letter —
// some display faces ship an uppercase-only glyph set that would otherwise
// misreport body-text fitness. CJK only needs ONE of the three scripts,
// mirroring GoogleFontFamily.supportsCjk: a Japanese-only or Korean-only
// family is still a valid CJK fallback, not just a Han-unified one.
private val LatinProbe = listOf("A", "g")
private val CjkProbe = listOf("中", "あ", "한")

/**
 * Filename-based weight guess, shared with [StaticWeightSuffix] (Google's
 * static-download naming). Tokens are tried longest-first so "ExtraBold" /
 * "SemiBold" / "ExtraLight" are not shadowed by the shorter "Bold" / "Light"
 * substrings they contain; a file with no recognised token defaults to the
 * normal weight rather than being dropped.
 */
internal fun weightFromFileName(fileName: String): Int =
    StaticWeightSuffix.entries
        .sortedByDescending { it.token.length }
        .firstOrNull { fileName.contains(it.token, ignoreCase = true) }
        ?.weight ?: NORMAL_WEIGHT

/**
 * Detects which of [characters] a font file covers; a seam over
 * `Typeface` / `Paint` so the family-grouping and slot-fitness logic in this
 * file stays unit-testable without Android graphics.
 */
internal fun interface GlyphCoverageChecker {
    fun coverage(
        file: File,
        characters: List<String>,
    ): Set<String>
}

/** Real [GlyphCoverageChecker]: one [Typeface] build per call, then [Paint.hasGlyph] per candidate. */
internal object TypefaceGlyphCoverageChecker : GlyphCoverageChecker {
    override fun coverage(
        file: File,
        characters: List<String>,
    ): Set<String> =
        runCatching {
            val paint = Paint().apply { typeface = Typeface.createFromFile(file) }
            characters.filterTo(mutableSetOf(), paint::hasGlyph)
        }.onFailure { Log.w(TAG, "glyph probe failed for ${file.name}", it) }
            .getOrDefault(emptySet())
}

/**
 * The raw font files [installedFontFamilies] groups into families; a seam
 * over `android.graphics.fonts.SystemFonts` so the grouping logic is
 * testable without it.
 */
internal fun interface SystemFontFileSource {
    fun files(): List<File>
}

/**
 * Real [SystemFontFileSource], backed by [SystemFonts.getAvailableFonts]
 * (API 29+; unconditional at this app's minSdk 33). Not every enumerated
 * [android.graphics.fonts.Font] is file-backed (some platform faces are
 * buffer-only), so entries with no readable file are dropped rather than
 * failing the whole scan.
 */
internal object PlatformSystemFontFileSource : SystemFontFileSource {
    override fun files(): List<File> =
        runCatching {
            SystemFonts
                .getAvailableFonts()
                .mapNotNull { font -> font.file }
                .filter { it.isFile }
                .distinct()
        }.onFailure { Log.w(TAG, "system font enumeration failed", it) }
            .getOrDefault(emptyList())
}

/**
 * Group [files] into families by display name. Pure and unit-testable: takes
 * the file list and a name reader as parameters instead of touching Android
 * APIs directly. A file whose name-table read fails (or yields nothing)
 * falls back to a cleaned filename, so a family always gets a stable label.
 */
internal fun groupIntoFamilies(
    files: List<File>,
    nameReader: (File) -> String? = OpenTypeFontName::familyNameOrNull,
): Map<String, List<File>> =
    files.groupBy { file ->
        nameReader(file)?.takeIf { it.isNotBlank() }
            ?: fallbackFamilyName(file)
    }

// Style/weight tokens stripped from the filename so "NotoSans-Bold.ttf" and
// "NotoSans-Regular.ttf" fall into one "NotoSans" family instead of two,
// approximating the grouping a real name-table read would give. Best-effort
// only: an unrecognised naming scheme still yields a stable (if less
// polished) family name rather than crashing or splitting arbitrarily.
private val StyleTokens =
    setOf(
        "thin",
        "extralight",
        "light",
        "regular",
        "medium",
        "semibold",
        "bold",
        "extrabold",
        "black",
        "italic",
        "oblique",
        "normal",
    )

private fun fallbackFamilyName(file: File): String {
    val parts = file.nameWithoutExtension.split(Regex("[-_ ]+")).filter { it.isNotBlank() }
    val trimmed = parts.filterNot { it.lowercase() in StyleTokens }
    return trimmed.ifEmpty { parts }.joinToString(" ").ifBlank { file.nameWithoutExtension }
}

/** The family whose glyph coverage best represents the whole family: the file closest to the normal weight. */
private fun representativeFile(files: List<File>): File =
    files.minBy { file -> abs(weightFromFileName(file.name) - NORMAL_WEIGHT) }

/**
 * Loads the installed-font catalog: enumerate the device's font files, group
 * them into families, then probe each family's Latin / CJK coverage from its
 * representative file. Runs on [Dispatchers.Default] — building a [Typeface]
 * per family is CPU work, not I/O. [nameReader] is exposed (beyond its
 * [groupIntoFamilies] default) so tests can drive grouping without real font
 * files.
 */
internal suspend fun installedFontFamilies(
    fileSource: SystemFontFileSource = PlatformSystemFontFileSource,
    checker: GlyphCoverageChecker = TypefaceGlyphCoverageChecker,
    nameReader: (File) -> String? = OpenTypeFontName::familyNameOrNull,
): List<SystemFontFamily> =
    withContext(Dispatchers.Default) {
        groupIntoFamilies(fileSource.files(), nameReader)
            .map { (name, files) -> toSystemFontFamily(name, files, checker) }
            .sortedBy { it.familyName.lowercase() }
    }

private fun toSystemFontFamily(
    name: String,
    files: List<File>,
    checker: GlyphCoverageChecker,
): SystemFontFamily {
    val covered = checker.coverage(representativeFile(files), LatinProbe + CjkProbe)
    return SystemFontFamily(
        familyName = name,
        files = files,
        supportsLatin = covered.containsAll(LatinProbe),
        supportsCjk = CjkProbe.any { it in covered },
    )
}

/** Loads [installedFontFamilies]; a seam so [FontRepository] can be constructed with a fake in JVM tests. */
internal fun interface SystemFontSource {
    suspend fun families(): List<SystemFontFamily>
}
