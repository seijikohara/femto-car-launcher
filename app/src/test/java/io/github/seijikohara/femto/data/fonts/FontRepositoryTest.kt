package io.github.seijikohara.femto.data.fonts

import io.github.seijikohara.femto.testfixtures.FakeFontFaceStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FontRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `choosing a family downloads it and evicts the dropped family`() =
        runTest {
            val cache = FakeFontFaceStore().apply { preload("Old Face") }
            val repository =
                repository(cache, FakeFontSelectionStore(FontSelection(latin = FontSource.GoogleFonts("Old Face"))))
            runCurrent()

            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("New Face"))
            runCurrent()

            assertEquals(listOf("New Face"), cache.downloads)
            assertFalse(cache.isCached("Old Face"))
            assertNotNull(repository.resolved.value.latin)
        }

    @Test
    fun `eviction never runs while a download is still in flight`() =
        runTest {
            val cache = FakeFontFaceStore()
            val repository = repository(cache)
            val gate = cache.gateDownload("Slow Face")
            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Slow Face"))
            runCurrent()
            assertEquals(setOf("Slow Face"), repository.downloading.value)

            // A fast re-selection cancels the resolve pass mid-download; the
            // switch pass must not evict until the in-flight write has finished.
            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Other Face"))
            runCurrent()
            assertTrue(cache.evictions.none { (keep, _) -> "Other Face" in keep })

            gate.complete(Unit)
            runCurrent()
            assertTrue(cache.evictions.any { (keep, _) -> "Other Face" in keep })
            assertFalse(cache.isCached("Slow Face"))
            assertEquals(emptySet(), repository.downloading.value)
        }

    @Test
    fun `restart loads the catalog from disk when the network is unavailable`() =
        runTest {
            val families = listOf(GoogleFontFamily("Inter", "Sans Serif", listOf("latin"), popularity = 1))
            catalogFile().writeText(Json.encodeToString(ListSerializer(GoogleFontFamily.serializer()), families))
            val repository = repository(FakeFontFaceStore())

            repository.ensureCatalog()
            val loaded = repository.catalog.first { it is CatalogState.Loaded }

            assertEquals(families, (loaded as CatalogState.Loaded).families)
        }

    @Test
    fun `restart serves a cached selection without re-downloading`() =
        runTest {
            val cache = FakeFontFaceStore().apply { preload("Inter") }
            val repository =
                repository(cache, FakeFontSelectionStore(FontSelection(latin = FontSource.GoogleFonts("Inter"))))

            val resolved = repository.resolved.first { it.latin != null }

            assertEquals(cache.cachedFontOrNull("Inter"), resolved.latin)
            assertTrue(cache.downloads.isEmpty())
        }

    @Test
    fun `a failed download adds the family to downloadFailed`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val repository = repository(cache)

            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            runCurrent()

            assertEquals(setOf("Inter"), repository.downloadFailed.value)
            assertNull(repository.resolved.value.latin)
        }

    @Test
    fun `re-choosing the failed family retries and a success clears downloadFailed`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val repository = repository(cache)
            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            runCurrent()
            cache.failing.clear()

            // The persisted selection does not change, so without the retry
            // trigger this second choose would never reach a new resolve pass.
            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            runCurrent()

            assertEquals(listOf("Inter", "Inter"), cache.downloads)
            assertEquals(emptySet(), repository.downloadFailed.value)
            assertNotNull(repository.resolved.value.latin)
        }

    @Test
    fun `choosing a different family clears the stale downloadFailed entry`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val repository = repository(cache)
            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            runCurrent()

            repository.choose(FontSlot.LATIN, FontSource.GoogleFonts("Roboto"))
            runCurrent()

            assertEquals(emptySet(), repository.downloadFailed.value)
        }

    @Test
    fun `a system font selection resolves straight from disk with no download`() =
        runTest {
            val installed =
                SystemFontFamily(
                    familyName = "Roboto Condensed",
                    files = listOf(File("/system/fonts/RobotoCondensed-Regular.ttf")),
                    supportsLatin = true,
                    supportsCjk = false,
                )
            val cache = FakeFontFaceStore()
            val repository =
                repository(
                    cache,
                    FakeFontSelectionStore(FontSelection(latin = FontSource.SystemFont("Roboto Condensed"))),
                    systemFontSource = SystemFontSource { listOf(installed) },
                )
            runCurrent()

            val resolved = assertIs<CachedFont.Static>(repository.resolved.value.latin)

            assertEquals(mapOf(400 to installed.files.single()), resolved.fileByWeight)
            assertTrue(cache.downloads.isEmpty())
            assertEquals(emptySet(), repository.downloading.value)
        }

    @Test
    fun `a system font selection that disappears from the catalog falls back to the system font`() =
        runTest {
            val repository =
                repository(
                    FakeFontFaceStore(),
                    FakeFontSelectionStore(FontSelection(latin = FontSource.SystemFont("Ghost Face"))),
                    systemFontSource = SystemFontSource { emptyList() },
                )
            runCurrent()

            assertNull(repository.resolved.value.latin)
        }

    @Test
    fun `a system font family with italic files resolves to the upright file per weight regardless of file order`() =
        runTest {
            val regular = File("/system/fonts/Roboto-Regular.ttf")
            val italic = File("/system/fonts/Roboto-Italic.ttf")
            val bold = File("/system/fonts/Roboto-Bold.ttf")
            val boldItalic = File("/system/fonts/Roboto-BoldItalic.ttf")

            // weightFromFileName has no italic token, so Regular/Italic both guess
            // 400 and Bold/BoldItalic both guess 700 — associateBy is
            // last-write-wins, so without the upright filter the resolved file for
            // a weight would depend on enumeration order. Assert both orderings so
            // neither can hide the bug.
            listOf(
                listOf(italic, regular, boldItalic, bold),
                listOf(regular, italic, bold, boldItalic),
            ).forEach { fileOrder ->
                val installed = SystemFontFamily("Roboto", fileOrder, supportsLatin = true, supportsCjk = false)
                val repository =
                    repository(
                        FakeFontFaceStore(),
                        FakeFontSelectionStore(FontSelection(latin = FontSource.SystemFont("Roboto"))),
                        systemFontSource = SystemFontSource { listOf(installed) },
                    )
                runCurrent()

                val resolved = assertIs<CachedFont.Static>(repository.resolved.value.latin)

                assertEquals(regular, resolved.fileByWeight[400])
                assertEquals(bold, resolved.fileByWeight[700])
            }
        }

    @Test
    fun `an italic-only system font family has no upright file and falls back to the system font`() =
        runTest {
            val installed =
                SystemFontFamily(
                    familyName = "Handwriting Italic",
                    files = listOf(File("/system/fonts/HandwritingItalic-Italic.ttf")),
                    supportsLatin = true,
                    supportsCjk = false,
                )
            val repository =
                repository(
                    FakeFontFaceStore(),
                    FakeFontSelectionStore(FontSelection(latin = FontSource.SystemFont("Handwriting Italic"))),
                    systemFontSource = SystemFontSource { listOf(installed) },
                )
            runCurrent()

            assertNull(repository.resolved.value.latin)
        }

    @Test
    fun `a system font selection is excluded from Google cache eviction`() =
        runTest {
            val cache = FakeFontFaceStore().apply { preload("Inter") }
            val repository =
                repository(
                    cache,
                    FakeFontSelectionStore(
                        FontSelection(
                            latin = FontSource.SystemFont("Roboto Condensed"),
                            cjk = FontSource.GoogleFonts("Inter"),
                        ),
                    ),
                    systemFontSource = SystemFontSource {
                        listOf(
                            SystemFontFamily("Roboto Condensed", emptyList(), true, false),
                        )
                    },
                )
            runCurrent()

            // The eviction "keep" set is Google-only: a system font family name
            // must never appear there, since evictExcept only ever operates
            // inside filesDir/google_fonts/ and has no directory for it anyway.
            assertTrue(cache.evictions.all { (keep, _) -> "Roboto Condensed" !in keep })
            assertTrue(cache.evictions.any { (keep, _) -> keep == setOf("Inter") })
        }

    private fun TestScope.repository(
        cache: FakeFontFaceStore,
        preferences: FakeFontSelectionStore = FakeFontSelectionStore(),
        systemFontSource: SystemFontSource = SystemFontSource { emptyList() },
        catalog: List<GoogleFontFamily>? = null,
    ): FontRepository =
        FontRepository(
            api = FontCatalogSource { catalog },
            cache = cache,
            preferences = preferences,
            systemFontSource = systemFontSource,
            catalogFile = catalogFile(),
            scope = backgroundScope,
        )

    private fun catalogFile(): File = File(tempFolder.root, "catalog.json")
}
