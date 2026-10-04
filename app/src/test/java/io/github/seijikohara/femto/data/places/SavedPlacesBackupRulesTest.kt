package io.github.seijikohara.femto.data.places

import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.excludedBackupPaths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Saved places are location data the privacy policy promises stay on the
 * device: the store's file must be excluded from cloud backup and from
 * device transfer (API 31+ rules) and from the legacy Auto Backup rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedPlacesBackupRulesTest {
    private val storePath = "datastore/$SAVED_PLACES_STORE_NAME.preferences_pb"

    @Test
    fun saved_places_are_excluded_from_cloud_backup() =
        assertTrue(storePath in excludedBackupPaths(R.xml.data_extraction_rules)["cloud-backup"].orEmpty())

    @Test
    fun saved_places_are_excluded_from_device_transfer() =
        assertTrue(storePath in excludedBackupPaths(R.xml.data_extraction_rules)["device-transfer"].orEmpty())

    @Test
    fun saved_places_are_excluded_from_legacy_auto_backup() =
        assertTrue(storePath in excludedBackupPaths(R.xml.backup_rules)["full-backup-content"].orEmpty())
}
