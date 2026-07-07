package io.github.seijikohara.femto.ui.fontpicker

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.seijikohara.femto.data.fonts.FontCatalogSource
import io.github.seijikohara.femto.data.fonts.FontRepository
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
import io.github.seijikohara.femto.data.fonts.GoogleFontFamily
import io.github.seijikohara.femto.data.fonts.SystemFontFamily
import io.github.seijikohara.femto.data.fonts.SystemFontSource
import io.github.seijikohara.femto.testfixtures.FakeFontFaceStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

private val Catalog =
    listOf(
        GoogleFontFamily("Inter", "Sans Serif", listOf("latin"), popularity = 1),
        GoogleFontFamily("Noto Sans JP", "Sans Serif", listOf("latin", "japanese"), popularity = 2),
        GoogleFontFamily("Roboto", "Sans Serif", listOf("latin"), popularity = 3),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class FontPickerViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `latin slot lists the whole catalog`() =
        runTest {
            val viewModel = viewModel(FontSlot.LATIN)
            viewModel.uiState.test {
                val ready = awaitUntil { it.status == PickerStatus.READY }
                assertEquals(listOf("Inter", "Noto Sans JP", "Roboto"), ready.families.map { it.family })
            }
        }

    @Test
    fun `cjk slot lists only cjk-capable families`() =
        runTest {
            val viewModel = viewModel(FontSlot.CJK)
            viewModel.uiState.test {
                val ready = awaitUntil { it.status == PickerStatus.READY }
                assertEquals(listOf("Noto Sans JP"), ready.families.map { it.family })
            }
        }

    @Test
    fun `search filters the catalog case-insensitively`() =
        runTest {
            val viewModel = viewModel(FontSlot.LATIN)
            viewModel.uiState.test {
                awaitUntil { it.status == PickerStatus.READY }

                viewModel.onAction(FontPickerAction.Search("noTO"))

                val filtered = awaitUntil { it.query == "noTO" && it.status == PickerStatus.READY }
                assertEquals(listOf("Noto Sans JP"), filtered.families.map { it.family })
            }
        }

    @Test
    fun `a failed download is exposed for the affected family`() =
        runTest {
            val cache = FakeFontFaceStore().apply { failing += "Inter" }
            val viewModel = viewModel(FontSlot.LATIN, cache = cache)
            viewModel.uiState.test {
                awaitUntil { it.status == PickerStatus.READY }

                viewModel.onAction(FontPickerAction.Choose(FontSource.GoogleFonts("Inter")))

                val failed = awaitUntil { "Inter" in it.downloadFailed }
                assertEquals(FontSource.GoogleFonts("Inter"), failed.selectedSource)
                // The selection and failure flows recombine once more after the
                // matched event; the trailing emission is not under test.
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `installed fonts are filtered to the slot and the live search query`() =
        runTest {
            val systemFonts =
                listOf(
                    SystemFontFamily("Roboto Condensed", emptyList(), supportsLatin = true, supportsCjk = false),
                    SystemFontFamily("Noto Sans CJK", emptyList(), supportsLatin = true, supportsCjk = true),
                )
            val viewModel = viewModel(FontSlot.CJK, systemFontSource = SystemFontSource { systemFonts })
            viewModel.uiState.test {
                val ready = awaitUntil { it.status == PickerStatus.READY }
                assertEquals(listOf("Noto Sans CJK"), ready.systemFonts.map { it.familyName })
            }
        }

    private fun TestScope.viewModel(
        slot: FontSlot,
        cache: FakeFontFaceStore = FakeFontFaceStore(),
        systemFontSource: SystemFontSource = SystemFontSource { emptyList() },
    ): FontPickerViewModel =
        FontPickerViewModel(
            repository =
                FontRepository(
                    api = FontCatalogSource { Catalog },
                    cache = cache,
                    preferences = FakeFontSelectionStore(),
                    systemFontSource = systemFontSource,
                    catalogFile = File(tempFolder.root, "catalog.json"),
                    scope = backgroundScope,
                ),
            slot = slot,
        )
}

// Conflated state flows may fold intermediate states into one emission, so the
// assertions anchor on a predicate rather than a fixed emission index.
private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
