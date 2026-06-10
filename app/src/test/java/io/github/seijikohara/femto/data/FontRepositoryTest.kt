package io.github.seijikohara.femto.data

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
            val repository = repository(cache, FakeFontSelectionStore(FontSelection(latinFamily = "Old Face")))
            runCurrent()

            repository.choose(FontSlot.LATIN, "New Face")
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
            repository.choose(FontSlot.LATIN, "Slow Face")
            runCurrent()
            assertEquals(setOf("Slow Face"), repository.downloading.value)

            // A fast re-selection cancels the resolve pass mid-download; the
            // switch pass must not evict until the in-flight write has finished.
            repository.choose(FontSlot.LATIN, "Other Face")
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
            val repository = repository(cache, FakeFontSelectionStore(FontSelection(latinFamily = "Inter")))

            val resolved = repository.resolved.first { it.latin != null }

            assertEquals(cache.cached("Inter"), resolved.latin)
            assertTrue(cache.downloads.isEmpty())
        }

    @Test
    fun `a failed download adds the family to downloadFailed`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val repository = repository(cache)

            repository.choose(FontSlot.LATIN, "Inter")
            runCurrent()

            assertEquals(setOf("Inter"), repository.downloadFailed.value)
            assertNull(repository.resolved.value.latin)
        }

    @Test
    fun `re-choosing the failed family retries and a success clears downloadFailed`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val repository = repository(cache)
            repository.choose(FontSlot.LATIN, "Inter")
            runCurrent()
            cache.failing.clear()

            // The persisted selection does not change, so without the retry
            // trigger this second choose would never reach a new resolve pass.
            repository.choose(FontSlot.LATIN, "Inter")
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
            repository.choose(FontSlot.LATIN, "Inter")
            runCurrent()

            repository.choose(FontSlot.LATIN, "Roboto")
            runCurrent()

            assertEquals(emptySet(), repository.downloadFailed.value)
        }

    private fun TestScope.repository(
        cache: FakeFontFaceStore,
        preferences: FakeFontSelectionStore = FakeFontSelectionStore(),
        catalog: List<GoogleFontFamily>? = null,
    ): FontRepository =
        FontRepository(
            api = FontCatalogSource { catalog },
            cache = cache,
            preferences = preferences,
            catalogFile = catalogFile(),
            scope = backgroundScope,
        )

    private fun catalogFile(): File = File(tempFolder.root, "catalog.json")
}
