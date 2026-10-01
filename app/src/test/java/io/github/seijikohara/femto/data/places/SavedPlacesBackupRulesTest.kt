package io.github.seijikohara.femto.data.places

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
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

    // The file-domain paths excluded under each section element of [xmlRes].
    private fun excludedPaths(xmlRes: Int): Map<String, Set<String>> {
        val parser = ApplicationProvider.getApplicationContext<Context>().resources.getXml(xmlRes)
        val excluded = mutableMapOf<String, MutableSet<String>>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "exclude") {
                if (parser.getAttributeValue(null, "domain") == "file") {
                    excluded.getOrPut(section) { mutableSetOf() } += parser.getAttributeValue(null, "path")
                }
            } else {
                section = parser.name
            }
        }
        return excluded
    }

    @Test
    fun saved_places_are_excluded_from_cloud_backup() =
        assertTrue(storePath in excludedPaths(R.xml.data_extraction_rules)["cloud-backup"].orEmpty())

    @Test
    fun saved_places_are_excluded_from_device_transfer() =
        assertTrue(storePath in excludedPaths(R.xml.data_extraction_rules)["device-transfer"].orEmpty())

    @Test
    fun saved_places_are_excluded_from_legacy_auto_backup() =
        assertTrue(storePath in excludedPaths(R.xml.backup_rules)["full-backup-content"].orEmpty())
}
