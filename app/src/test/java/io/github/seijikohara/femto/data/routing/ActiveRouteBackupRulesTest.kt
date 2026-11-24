package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.excludedBackupPaths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The route kept across a restart is location data the privacy policy promises stays on the device: its directory,
 * which holds both its files, must be excluded from cloud backup and from device transfer (API 31+ rules) and from the
 * legacy Auto Backup rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActiveRouteBackupRulesTest {
    @Test
    fun `the kept route is excluded from cloud backup`() =
        assertTrue(ACTIVE_ROUTE_DIR in excludedBackupPaths(R.xml.data_extraction_rules)["cloud-backup"].orEmpty())

    @Test
    fun `the kept route is excluded from device transfer`() =
        assertTrue(ACTIVE_ROUTE_DIR in excludedBackupPaths(R.xml.data_extraction_rules)["device-transfer"].orEmpty())

    @Test
    fun `the kept route is excluded from legacy auto backup`() =
        assertTrue(ACTIVE_ROUTE_DIR in excludedBackupPaths(R.xml.backup_rules)["full-backup-content"].orEmpty())
}
