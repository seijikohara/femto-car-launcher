package io.github.seijikohara.femto.ui.offlinemap

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.testfixtures.regionDownload
import io.github.seijikohara.femto.testfixtures.savedRegion
import io.github.seijikohara.femto.ui.offlinemap.components.REGION_PICKER_CHEVRON_TAG
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.components.MapSection
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What the Saved map regions sheet draws from its state, beyond the ViewModel's mapping: the storage line, the
 * download row in each phase with the buttons that phase takes, the saved regions with their delete button, the
 * Add region row that a download disables, and the picker's rows. The Settings row that opens the sheet is here
 * too, since it shows only when the build carries the feature. Settings has no screenshot goldens, so nothing else
 * pins these.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h1600dp")
class OfflineRegionsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = mutableListOf<OfflineRegionsAction>()

    @Test
    fun `the storage line names the limit when there is one`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                usedBytes = USED_BYTES,
                freeBytes = FREE_BYTES,
                limit = OfflineStorageLimit.GB_5,
            ),
        )

        val limit = context.getString(R.string.offline_regions_limit_gb, 5L)
        rule
            .onNodeWithText(
                context.getString(R.string.offline_regions_storage_limited, size(USED_BYTES), limit, size(FREE_BYTES)),
            ).assertExists()
    }

    @Test
    fun `the storage line without a limit and the limit row open the choice`() {
        setScreen(OfflineRegionsUiState(loaded = true, usedBytes = USED_BYTES, freeBytes = FREE_BYTES))

        rule
            .onNodeWithText(context.getString(R.string.offline_regions_storage, size(USED_BYTES), size(FREE_BYTES)))
            .assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_limit_summary)).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_limit)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.OpenStorageLimit), actions)
    }

    @Test
    fun `the running download shows both sizes and its cancel asks first`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download = regionDownload("Bayern", RegionDownloadPhase.RUNNING, RECEIVED_BYTES, TOTAL_BYTES),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_download_title, "Bayern")).assertExists()
        rule
            .onNodeWithText(
                context.getString(R.string.offline_regions_download_progress, size(RECEIVED_BYTES), size(TOTAL_BYTES)),
            ).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_cancel)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.CancelDownload), actions)
    }

    @Test
    fun `the paused download says why and resumes`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download =
                    regionDownload(
                        "Bayern",
                        RegionDownloadPhase.PAUSED,
                        RECEIVED_BYTES,
                        TOTAL_BYTES,
                        RegionDownloadFailure.NETWORK,
                    ),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_failure_network)).assertExists()
        rule
            .onNodeWithText(
                context.getString(R.string.offline_regions_download_progress, size(RECEIVED_BYTES), size(TOTAL_BYTES)),
            ).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_resume)).performClick()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_cancel)).performClick()

        assertEquals(
            listOf(OfflineRegionsAction.ResumeDownload, OfflineRegionsAction.CancelDownload),
            actions,
        )
    }

    @Test
    fun `a download restored after a restart is named and says it is paused`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download = regionDownload("Bayern", RegionDownloadPhase.PAUSED, RECEIVED_BYTES, TOTAL_BYTES),
            ),
        )

        // Nothing runs until Resume, so the row does not say Downloading.
        rule.onNodeWithText("Bayern").assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_title, "Bayern")).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_paused)).assertExists()
        rule
            .onNodeWithText(
                context.getString(R.string.offline_regions_download_progress, size(RECEIVED_BYTES), size(TOTAL_BYTES)),
            ).assertExists()
    }

    @Test
    fun `a failed download is named without saying downloading`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download =
                    regionDownload("Bayern", RegionDownloadPhase.FAILED, failure = RegionDownloadFailure.NETWORK),
            ),
        )

        rule.onNodeWithText("Bayern").assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_title, "Bayern")).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.offline_regions_failure_network)).assertExists()
    }

    @Test
    fun `the verifying download takes no button`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download = regionDownload("Bayern", RegionDownloadPhase.VERIFYING, TOTAL_BYTES, TOTAL_BYTES),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_download_verifying)).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_cancel)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_resume)).assertDoesNotExist()
    }

    @Test
    fun `the failed download says why and dismisses`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                download =
                    regionDownload("Bayern", RegionDownloadPhase.FAILED, failure = RegionDownloadFailure.OVER_LIMIT),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_failure_over_limit)).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_download_dismiss)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.DismissDownload), actions)
    }

    @Test
    fun `add region is disabled while a download exists`() {
        setScreen(
            OfflineRegionsUiState(loaded = true, download = regionDownload("Bayern", RegionDownloadPhase.RUNNING)),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_add)).assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.offline_regions_add_busy)).assertExists()
    }

    @Test
    fun `add region waits for the saved regions to load`() {
        setScreen(OfflineRegionsUiState(loaded = false))

        // A download restored by the load would answer the offer as busy, and the sheet would close it silently.
        rule.onNodeWithText(context.getString(R.string.offline_regions_add)).assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.offline_regions_add_busy)).assertDoesNotExist()
    }

    @Test
    fun `add region opens the picker without a download`() {
        setScreen(OfflineRegionsUiState(loaded = true))

        rule.onNodeWithText(context.getString(R.string.offline_regions_add)).assertIsEnabled().performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.AddRegion), actions)
    }

    @Test
    fun `the list says when nothing is saved`() {
        setScreen(OfflineRegionsUiState(loaded = true))

        rule.onNodeWithText(context.getString(R.string.offline_regions_empty)).assertExists()
    }

    @Test
    fun `a saved region shows its size and data date and its delete button asks`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                saved = listOf(savedRegion("berlin", "Berlin", BERLIN_BYTES, dataDateEpochMs = DATA_DATE_EPOCH_MS)),
            ),
        )

        val date = DateFormat.getMediumDateFormat(context).format(Date(DATA_DATE_EPOCH_MS))
        rule.onNodeWithText("Berlin").assertExists()
        rule
            .onNodeWithText(context.getString(R.string.offline_regions_saved_summary, size(BERLIN_BYTES), date))
            .assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_empty)).assertDoesNotExist()
        rule
            .onNodeWithContentDescription(context.getString(R.string.offline_regions_delete_region, "Berlin"))
            .performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.DeleteRegion("berlin")), actions)
    }

    @Test
    fun `the picker shows the suggestion under its subheader and sends taps by row kind`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                picker =
                    RegionPickerUiState.Level(
                        parentName = null,
                        suggestion = RegionRow("bayern", "Bayern", BAYERN_BYTES, hasChildren = false, saved = false),
                        rows =
                            listOf(
                                RegionRow("asia", "Asia", null, hasChildren = true, saved = false),
                                RegionRow("europe", "Europe", null, hasChildren = true, saved = false),
                            ),
                        pendingId = null,
                    ),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_suggestion)).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_size, size(BAYERN_BYTES))).assertExists()
        rule.onNodeWithText("Bayern").performClick()
        rule.onNodeWithText("Europe").performClick()

        assertEquals(
            listOf(OfflineRegionsAction.PickRegion("bayern"), OfflineRegionsAction.OpenRegion("europe")),
            actions,
        )
    }

    @Test
    fun `a level starts with the whole region, and a saved region says so and takes no tap`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                picker =
                    RegionPickerUiState.Level(
                        parentName = "Germany",
                        suggestion = null,
                        rows =
                            listOf(
                                RegionRow(
                                    "germany",
                                    "Germany",
                                    GERMANY_BYTES,
                                    hasChildren = false,
                                    saved = false,
                                    whole = true,
                                ),
                                RegionRow("berlin", "Berlin", BERLIN_BYTES, hasChildren = false, saved = true),
                            ),
                        pendingId = null,
                    ),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_saved)).assertExists()
        rule.onNodeWithText("Berlin").assertHasNoClickAction()
        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_whole, "Germany")).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()

        assertEquals(
            listOf(OfflineRegionsAction.PickRegion("germany"), OfflineRegionsAction.Back),
            actions,
        )
    }

    @Test
    fun `a saved region with sub-regions says saved, shows no chevron and takes no tap`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                picker =
                    RegionPickerUiState.Level(
                        parentName = "Europe",
                        suggestion = null,
                        rows =
                            listOf(
                                RegionRow("germany", "Germany", GERMANY_BYTES, hasChildren = true, saved = true),
                                RegionRow("poland", "Poland", null, hasChildren = true, saved = false),
                            ),
                        pendingId = null,
                    ),
            ),
        )

        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_saved)).assertExists()
        // Poland's chevron alone: the saved Germany shows none.
        rule.onAllNodesWithTag(REGION_PICKER_CHEVRON_TAG, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("Germany").assertHasNoClickAction().performClick()

        assertEquals(emptyList(), actions)
    }

    @Test
    fun `rows take no tap while an offer is pending`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                picker =
                    RegionPickerUiState.Level(
                        parentName = null,
                        suggestion = null,
                        rows =
                            listOf(
                                RegionRow("cyprus", "Cyprus", CYPRUS_BYTES, hasChildren = false, saved = false),
                                RegionRow("monaco", "Monaco", MONACO_BYTES, hasChildren = false, saved = false),
                            ),
                        pendingId = "cyprus",
                    ),
            ),
        )

        rule.onNodeWithText("Cyprus").assertHasNoClickAction()
        rule.onNodeWithText("Monaco").assertHasNoClickAction()
    }

    @Test
    fun `an unavailable region list offers a retry`() {
        setScreen(OfflineRegionsUiState(loaded = true, picker = RegionPickerUiState.Unavailable))

        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_unavailable)).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_picker_retry)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.RetryCatalog), actions)
    }

    @Test
    fun `the storage line waits for the saved regions to load`() {
        setScreen(OfflineRegionsUiState(loaded = false))

        rule
            .onNodeWithText(context.getString(R.string.offline_regions_storage, size(0), size(0)))
            .assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.offline_regions_empty)).assertDoesNotExist()
    }

    @Test
    fun `the save confirmation names the sizes against the limit and saves`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                dialog =
                    OfflineRegionsDialog.ConfirmSave(
                        name = "Bayern",
                        sizeBytes = TOTAL_BYTES,
                        usedAfterBytes = USED_AFTER_BYTES,
                        limitBytes = LIMIT_BYTES,
                        freeBytes = FREE_BYTES,
                    ),
            ),
        )

        val limit = context.getString(R.string.offline_regions_limit_gb, 2L)
        rule.onNodeWithText(context.getString(R.string.offline_regions_save_title, "Bayern")).assertExists()
        rule
            .onNodeWithText(
                context.getString(
                    R.string.offline_regions_save_message_limited,
                    size(TOTAL_BYTES),
                    size(USED_AFTER_BYTES),
                    limit,
                    size(FREE_BYTES),
                ),
            ).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_save)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.ConfirmSave), actions)
    }

    @Test
    fun `the over-limit dialog names the excess and opens the limit`() {
        setScreen(
            OfflineRegionsUiState(
                loaded = true,
                dialog = OfflineRegionsDialog.OverLimit("Bayern", TOTAL_BYTES, OVER_BY_BYTES, LIMIT_BYTES),
            ),
        )

        val limit = context.getString(R.string.offline_regions_limit_gb, 2L)
        rule
            .onNodeWithText(
                context.getString(
                    R.string.offline_regions_over_limit_message,
                    "Bayern",
                    size(TOTAL_BYTES),
                    size(OVER_BY_BYTES),
                    limit,
                ),
            ).assertExists()
        rule.onNodeWithText(context.getString(R.string.offline_regions_change_limit)).performClick()

        assertEquals(listOf<OfflineRegionsAction>(OfflineRegionsAction.ChangeLimit), actions)
    }

    @Test
    fun `the limit choice sets the picked limit`() {
        setScreen(OfflineRegionsUiState(loaded = true, dialog = OfflineRegionsDialog.StorageLimit))

        rule.onNodeWithText(context.getString(R.string.offline_regions_limit_gb, 10L)).performClick()

        assertEquals(
            listOf<OfflineRegionsAction>(OfflineRegionsAction.SetStorageLimit(OfflineStorageLimit.GB_10)),
            actions,
        )
    }

    @Test
    fun `the settings row is absent when saved regions are unavailable`() {
        setMapSection(offlineRegionsAvailable = false)

        rule.onNodeWithText(context.getString(R.string.settings_map_offline_regions)).assertDoesNotExist()
    }

    @Test
    fun `the settings row opens the sheet when saved regions are available`() {
        var opened = 0
        setMapSection(offlineRegionsAvailable = true, onOpen = { opened++ })

        rule.onNodeWithText(context.getString(R.string.settings_map_offline_regions_summary)).assertExists()
        rule
            .onNodeWithText(context.getString(R.string.settings_map_offline_regions))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `the stable build the unit tests run on does not offer saved regions`() {
        assertFalse(context.resources.getBoolean(R.bool.offline_regions_enabled))
    }

    private fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    private fun setScreen(uiState: OfflineRegionsUiState) {
        rule.setContent {
            FemtoTheme {
                OfflineRegionsScreen(uiState = uiState, onAction = { actions += it })
            }
        }
        rule.waitForIdle()
    }

    private fun setMapSection(
        offlineRegionsAvailable: Boolean,
        onOpen: () -> Unit = {},
    ) {
        rule.setContent {
            FemtoTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MapSection(
                        uiState =
                            SettingsUiState.Initial.copy(
                                mapBackend = MapBackend.OSM,
                                offlineRegionsAvailable = offlineRegionsAvailable,
                            ),
                        onAction = {},
                        onOpenDocument = {},
                        onOpenOfflineRegions = onOpen,
                        onOpenRoutingData = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        const val USED_BYTES = 50_946_048L
        const val FREE_BYTES = 9_000_000_000L
        const val RECEIVED_BYTES = 300_000_000L
        const val TOTAL_BYTES = 1_043_341_312L
        const val GERMANY_BYTES = 4_400_000_000L
        const val BAYERN_BYTES = 1_043_341_312L
        const val BERLIN_BYTES = 49_217_536L
        const val CYPRUS_BYTES = 32_321_536L
        const val USED_AFTER_BYTES = 1_094_287_360L
        const val LIMIT_BYTES = 2_000_000_000L
        const val OVER_BY_BYTES = 200_000_000L
        const val MONACO_BYTES = 1_728_512L
        const val DATA_DATE_EPOCH_MS = 1_763_400_000_000L
    }
}
