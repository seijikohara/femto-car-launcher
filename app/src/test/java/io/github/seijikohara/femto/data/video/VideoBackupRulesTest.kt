package io.github.seijikohara.femto.data.video

import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.excludedBackupPaths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The video store holds the picked file's reference, whose read grant belongs
 * to this install, and the picture gate a new install starts on: its file
 * must be excluded from cloud backup and device transfer (API 31+ rules) and
 * from the legacy Auto Backup rules, as PRIVACY.md promises.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoBackupRulesTest {
    private val storePath = "datastore/$VIDEO_STORE_NAME.preferences_pb"

    @Test
    fun video_settings_are_excluded_from_cloud_backup() =
        assertTrue(storePath in excludedBackupPaths(R.xml.data_extraction_rules)["cloud-backup"].orEmpty())

    @Test
    fun video_settings_are_excluded_from_device_transfer() =
        assertTrue(storePath in excludedBackupPaths(R.xml.data_extraction_rules)["device-transfer"].orEmpty())

    @Test
    fun video_settings_are_excluded_from_legacy_auto_backup() =
        assertTrue(storePath in excludedBackupPaths(R.xml.backup_rules)["full-backup-content"].orEmpty())
}
