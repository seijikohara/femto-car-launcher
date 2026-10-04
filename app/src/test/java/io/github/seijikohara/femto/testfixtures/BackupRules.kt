package io.github.seijikohara.femto.testfixtures

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.xmlpull.v1.XmlPullParser

/**
 * The file-domain paths excluded under each section element of the backup
 * rules resource [xmlRes] (`cloud-backup`, `device-transfer`, or the legacy
 * `full-backup-content`), read through the app's resources as the platform
 * reads them.
 */
internal fun excludedBackupPaths(xmlRes: Int): Map<String, Set<String>> {
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
