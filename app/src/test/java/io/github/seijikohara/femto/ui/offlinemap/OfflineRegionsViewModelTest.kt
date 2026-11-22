package io.github.seijikohara.femto.ui.offlinemap

import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.map.offline.CatalogRegion
import io.github.seijikohara.femto.data.map.offline.OfflineRegionsState
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.data.map.offline.RegionOffer
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeOfflineRegionsPort
import io.github.seijikohara.femto.testfixtures.catalogRegion
import io.github.seijikohara.femto.testfixtures.regionDownload
import io.github.seijikohara.femto.testfixtures.savedRegion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Pure JVM: the port, the display store and the position are in-memory fakes driven by the StandardTestDispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineRegionsViewModelTest {
    private val port = FakeOfflineRegionsPort()
    private val store = FakeDisplaySettingsStore()
    private val dispatcher = StandardTestDispatcher()

    // Europe has no file of its own; Germany has one and sub-regions with files; Asia and Japan stand beside them.
    private val catalog =
        RegionCatalog(
            listOf(
                catalogRegion("europe", "Europe"),
                catalogRegion("germany", "Germany", parentId = "europe", approxBytes = GERMANY_BYTES),
                catalogRegion("bayern", "Bayern", parentId = "germany", approxBytes = BAYERN_BYTES),
                catalogRegion("berlin", "Berlin", parentId = "germany", approxBytes = BERLIN_BYTES),
                catalogRegion("monaco", "Monaco", parentId = "europe", approxBytes = MONACO_BYTES),
                catalogRegion("asia", "Asia"),
                catalogRegion("japan", "Japan", parentId = "asia", approxBytes = JAPAN_BYTES),
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        port.catalog = catalog
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the sheet loads the saved regions when created and fetches nothing until a tap`() =
        runTest(dispatcher) {
            subscribed()
            advanceUntilIdle()

            assertEquals(1, port.loads)
            assertEquals(emptyList(), port.catalogCalls)
            assertEquals(emptyList(), port.offers)
        }

    @Test
    fun `the list shows saved regions the limit and the storage figures`() =
        runTest(dispatcher) {
            val saved =
                listOf(savedRegion("berlin", "Berlin", BERLIN_BYTES), savedRegion("monaco", "Monaco", MONACO_BYTES))
            port.state.value =
                OfflineRegionsState(loaded = true, saved = saved, usedBytes = USED_BYTES, freeBytes = FREE_BYTES)
            store.setOfflineStorageLimit(OfflineStorageLimit.GB_5)
            val vm = subscribed()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(saved, state.saved)
            assertEquals(OfflineStorageLimit.GB_5, state.limit)
            assertEquals(USED_BYTES, state.usedBytes)
            assertEquals(FREE_BYTES, state.freeBytes)
            assertNull(state.picker)
        }

    @Test
    fun `opening the picker loads the catalogue and offers the region for the position first`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            port.catalogGate = gate
            port.suggestedId = "bayern"
            val vm = subscribed()

            vm.onAction(OfflineRegionsAction.AddRegion)
            advanceUntilIdle()
            assertEquals(RegionPickerUiState.Loading, vm.uiState.value.picker)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf<Pair<Double?, Double?>>(HERE_LONGITUDE to HERE_LATITUDE), port.catalogCalls)
            val level = vm.level()
            assertNull(level.parentName)
            assertEquals(row("bayern", "Bayern", BAYERN_BYTES), level.suggestion)
            assertEquals(listOf("asia", "europe"), level.rows.map { it.id })
        }

    @Test
    fun `the picker without a position shows no suggestion`() =
        runTest(dispatcher) {
            port.suggestedId = "bayern"
            val vm = subscribed(position = null)

            vm.onAction(OfflineRegionsAction.AddRegion)
            advanceUntilIdle()

            assertEquals(listOf<Pair<Double?, Double?>>(null to null), port.catalogCalls)
            assertNull(vm.level().suggestion)
        }

    @Test
    fun `opening a region with children shows them with the whole region first when it has a file`() =
        runTest(dispatcher) {
            val vm = pickerOpen()

            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            advanceUntilIdle()
            val europe = vm.level()
            assertEquals("Europe", europe.parentName)
            assertEquals(
                listOf(
                    row("germany", "Germany", GERMANY_BYTES, hasChildren = true),
                    row("monaco", "Monaco", MONACO_BYTES),
                ),
                europe.rows,
            )

            vm.onAction(OfflineRegionsAction.OpenRegion("germany"))
            advanceUntilIdle()
            val germany = vm.level()
            assertEquals("Germany", germany.parentName)
            assertNull(germany.suggestion)
            assertEquals(
                listOf(
                    row("germany", "Germany", GERMANY_BYTES, whole = true),
                    row("bayern", "Bayern", BAYERN_BYTES),
                    row("berlin", "Berlin", BERLIN_BYTES),
                ),
                germany.rows,
            )
        }

    @Test
    fun `back goes up one level and then to the list`() =
        runTest(dispatcher) {
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            vm.onAction(OfflineRegionsAction.OpenRegion("germany"))
            advanceUntilIdle()

            vm.onAction(OfflineRegionsAction.Back)
            advanceUntilIdle()
            assertEquals("Europe", vm.level().parentName)

            vm.onAction(OfflineRegionsAction.Back)
            advanceUntilIdle()
            assertNull(vm.level().parentName)

            vm.onAction(OfflineRegionsAction.Back)
            advanceUntilIdle()
            assertNull(vm.uiState.value.picker)
        }

    @Test
    fun `picking a region shows the confirmation with the sizes from the offer`() =
        runTest(dispatcher) {
            port.offerAnswer = ::readyOffer
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            advanceUntilIdle()

            vm.onAction(OfflineRegionsAction.PickRegion("monaco"))
            advanceUntilIdle()

            assertEquals(listOf("monaco"), port.offers)
            assertEquals(
                OfflineRegionsDialog.ConfirmSave(
                    name = "Monaco",
                    sizeBytes = PROBED_BYTES,
                    usedAfterBytes = USED_AFTER_BYTES,
                    limitBytes = LIMIT_BYTES,
                    freeBytes = FREE_BYTES,
                ),
                vm.uiState.value.dialog,
            )
        }

    @Test
    fun `confirming starts the download and returns to the list`() =
        runTest(dispatcher) {
            port.offerAnswer = ::readyOffer
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            vm.onAction(OfflineRegionsAction.PickRegion("monaco"))
            advanceUntilIdle()

            vm.onAction(OfflineRegionsAction.ConfirmSave)
            advanceUntilIdle()

            assertEquals(listOf(readyOffer(catalog.regionOrNull("monaco")!!)), port.started)
            assertNull(vm.uiState.value.picker)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `an offer over the limit shows the excess and change limit opens the choice`() =
        runTest(dispatcher) {
            port.offerAnswer = { RegionOffer.OverLimit(it, PROBED_BYTES, OVER_BY_BYTES, LIMIT_BYTES) }
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("asia"))
            vm.onAction(OfflineRegionsAction.PickRegion("japan"))
            advanceUntilIdle()

            assertEquals(
                OfflineRegionsDialog.OverLimit("Japan", PROBED_BYTES, OVER_BY_BYTES, LIMIT_BYTES),
                vm.uiState.value.dialog,
            )

            vm.onAction(OfflineRegionsAction.ChangeLimit)
            advanceUntilIdle()
            assertNull(vm.uiState.value.picker)
            assertEquals(OfflineRegionsDialog.StorageLimit, vm.uiState.value.dialog)
            assertEquals(emptyList(), port.started)
        }

    @Test
    fun `an offer the region cannot take says why`() =
        runTest(dispatcher) {
            port.offerAnswer = { RegionOffer.Unavailable(it, RegionOffer.Unavailable.Reason.GONE) }
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("asia"))
            vm.onAction(OfflineRegionsAction.PickRegion("japan"))
            advanceUntilIdle()

            assertEquals(
                OfflineRegionsDialog.OfferUnavailable("Japan", RegionOffer.Unavailable.Reason.GONE),
                vm.uiState.value.dialog,
            )
        }

    @Test
    fun `an offer for a busy or saved region closes silently`() =
        runTest(dispatcher) {
            port.offerAnswer = { RegionOffer.Unavailable(it, RegionOffer.Unavailable.Reason.BUSY) }
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("asia"))
            vm.onAction(OfflineRegionsAction.PickRegion("japan"))
            advanceUntilIdle()

            assertEquals(listOf("japan"), port.offers)
            assertNull(vm.uiState.value.dialog)
            assertNull(vm.level().pendingId)
        }

    @Test
    fun `a second tap while an offer is pending is ignored`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            port.offerGate = gate
            port.offerAnswer = ::readyOffer
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            vm.onAction(OfflineRegionsAction.OpenRegion("germany"))
            vm.onAction(OfflineRegionsAction.PickRegion("bayern"))
            advanceUntilIdle()
            assertEquals("bayern", vm.level().pendingId)

            vm.onAction(OfflineRegionsAction.PickRegion("berlin"))
            vm.onAction(OfflineRegionsAction.PickRegion("bayern"))
            advanceUntilIdle()
            assertEquals(listOf("bayern"), port.offers)

            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(vm.level().pendingId)
            assertEquals("Bayern", assertIs<OfflineRegionsDialog.ConfirmSave>(vm.uiState.value.dialog).name)
        }

    @Test
    fun `a saved region in the picker cannot be picked`() =
        runTest(dispatcher) {
            port.state.value =
                OfflineRegionsState(loaded = true, saved = listOf(savedRegion("berlin", "Berlin", BERLIN_BYTES)))
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            vm.onAction(OfflineRegionsAction.OpenRegion("germany"))
            advanceUntilIdle()
            assertTrue(
                vm
                    .level()
                    .rows
                    .single { it.id == "berlin" }
                    .saved,
            )

            vm.onAction(OfflineRegionsAction.PickRegion("berlin"))
            advanceUntilIdle()

            assertEquals(emptyList(), port.offers)
            assertNull(vm.level().pendingId)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `a saved region with sub-regions does not open`() =
        runTest(dispatcher) {
            port.state.value =
                OfflineRegionsState(loaded = true, saved = listOf(savedRegion("germany", "Germany", GERMANY_BYTES)))
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("europe"))
            advanceUntilIdle()
            assertTrue(
                vm
                    .level()
                    .rows
                    .single { it.id == "germany" }
                    .saved,
            )

            vm.onAction(OfflineRegionsAction.OpenRegion("germany"))
            advanceUntilIdle()

            assertEquals("Europe", vm.level().parentName)
            assertEquals(emptyList(), port.offers)
        }

    @Test
    fun `setting the limit writes the display store`() =
        runTest(dispatcher) {
            val vm = subscribed()
            vm.onAction(OfflineRegionsAction.OpenStorageLimit)
            advanceUntilIdle()
            assertEquals(OfflineRegionsDialog.StorageLimit, vm.uiState.value.dialog)

            vm.onAction(OfflineRegionsAction.SetStorageLimit(OfflineStorageLimit.GB_10))
            advanceUntilIdle()

            assertEquals(OfflineStorageLimit.GB_10, store.settings.first().offlineStorageLimit)
            assertEquals(OfflineStorageLimit.GB_10, vm.uiState.value.limit)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `delete asks first and deletes on confirmation`() =
        runTest(dispatcher) {
            val berlin = savedRegion("berlin", "Berlin", BERLIN_BYTES)
            port.state.value = OfflineRegionsState(loaded = true, saved = listOf(berlin))
            val vm = subscribed()

            vm.onAction(OfflineRegionsAction.DeleteRegion("berlin"))
            advanceUntilIdle()
            assertEquals(OfflineRegionsDialog.ConfirmDelete(berlin), vm.uiState.value.dialog)
            assertEquals(emptyList(), port.deleted)

            vm.onAction(OfflineRegionsAction.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(listOf("berlin"), port.deleted)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `cancel asks first and cancels on confirmation`() =
        runTest(dispatcher) {
            port.state.value =
                OfflineRegionsState(loaded = true, download = regionDownload("Bayern", RegionDownloadPhase.RUNNING))
            val vm = subscribed()

            vm.onAction(OfflineRegionsAction.CancelDownload)
            advanceUntilIdle()
            assertEquals(OfflineRegionsDialog.ConfirmCancel("Bayern"), vm.uiState.value.dialog)
            assertEquals(0, port.cancels)

            vm.onAction(OfflineRegionsAction.ConfirmCancel)
            advanceUntilIdle()
            assertEquals(1, port.cancels)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `resume and dismiss reach the port`() =
        runTest(dispatcher) {
            port.state.value =
                OfflineRegionsState(loaded = true, download = regionDownload("Bayern", RegionDownloadPhase.PAUSED))
            val vm = subscribed()

            vm.onAction(OfflineRegionsAction.ResumeDownload)
            vm.onAction(OfflineRegionsAction.DismissDownload)
            advanceUntilIdle()

            assertEquals(1, port.resumes)
            assertEquals(1, port.cancels)
            assertNull(vm.uiState.value.dialog)
        }

    @Test
    fun `an unavailable catalogue can be retried`() =
        runTest(dispatcher) {
            port.catalog = null
            val vm = subscribed()
            vm.onAction(OfflineRegionsAction.AddRegion)
            advanceUntilIdle()
            assertEquals(RegionPickerUiState.Unavailable, vm.uiState.value.picker)

            port.catalog = catalog
            vm.onAction(OfflineRegionsAction.RetryCatalog)
            advanceUntilIdle()

            assertEquals(2, port.catalogCalls.size)
            assertEquals(listOf("asia", "europe"), vm.level().rows.map { it.id })
        }

    @Test
    fun `closing the sheet returns it to the list and drops the pending offer`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            port.offerGate = gate
            port.offerAnswer = ::readyOffer
            val vm = pickerOpen()
            vm.onAction(OfflineRegionsAction.OpenRegion("asia"))
            vm.onAction(OfflineRegionsAction.PickRegion("japan"))
            advanceUntilIdle()

            vm.onAction(OfflineRegionsAction.Closed)
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(vm.uiState.value.picker)
            assertNull(vm.uiState.value.dialog)
        }

    // Subscribes, since the state is shared only while collected.
    private fun TestScope.subscribed(
        position: Pair<Double, Double>? = HERE_LONGITUDE to HERE_LATITUDE,
    ): OfflineRegionsViewModel =
        OfflineRegionsViewModel(port = port, displayPreferences = store, position = { position }).also { vm ->
            backgroundScope.launch { vm.uiState.collect { } }
        }

    private fun TestScope.pickerOpen(): OfflineRegionsViewModel =
        subscribed().also { vm ->
            vm.onAction(OfflineRegionsAction.AddRegion)
            advanceUntilIdle()
        }

    private fun OfflineRegionsViewModel.level(): RegionPickerUiState.Level =
        assertIs<RegionPickerUiState.Level>(uiState.value.picker)

    private fun row(
        id: String,
        name: String,
        approxBytes: Long?,
        hasChildren: Boolean = false,
        saved: Boolean = false,
        whole: Boolean = false,
    ) = RegionRow(id, name, approxBytes, hasChildren, saved, whole)

    private fun readyOffer(region: CatalogRegion): RegionOffer.Ready =
        RegionOffer.Ready(
            region = region,
            file = RemoteFile(sizeBytes = PROBED_BYTES, etag = "\"probe\"", lastModifiedEpochMs = DATA_DATE_EPOCH_MS),
            usedAfterBytes = USED_AFTER_BYTES,
            limitBytes = LIMIT_BYTES,
            freeBytes = FREE_BYTES,
        )

    private companion object {
        // Asymmetric, so a swapped pair shows.
        const val HERE_LONGITUDE = 11.58
        const val HERE_LATITUDE = 48.14
        const val GERMANY_BYTES = 4_400_000_000L
        const val BAYERN_BYTES = 1_043_341_312L
        const val BERLIN_BYTES = 49_217_536L
        const val MONACO_BYTES = 1_728_512L
        const val JAPAN_BYTES = 2_300_000_000L
        const val PROBED_BYTES = 1_100_000_000L
        const val USED_BYTES = 50_946_048L
        const val USED_AFTER_BYTES = 1_150_946_048L
        const val LIMIT_BYTES = 2_000_000_000L
        const val OVER_BY_BYTES = 200_000_000L
        const val FREE_BYTES = 9_000_000_000L
        const val DATA_DATE_EPOCH_MS = 1_763_400_000_000L
    }
}
