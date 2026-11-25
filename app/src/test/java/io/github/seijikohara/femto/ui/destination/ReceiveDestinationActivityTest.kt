package io.github.seijikohara.femto.ui.destination

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.MainActivity
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.ReceivedPlaceInbox
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The entrance another app reaches with a share or a `geo:` link: the manifest
 * routes both to it and adds no second launcher icon, and the entrance leaves
 * the place in the shared inbox, brings the dashboard forward and finishes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReceiveDestinationActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entrance = ComponentName(context, ReceiveDestinationActivity::class.java)
    private val inbox = ReceivedPlaceInbox.shared

    // The inbox is the process's; a place left in it would reach the next test.
    @After
    fun emptyInbox() {
        inbox.pending.value?.let(inbox::handled)
    }

    // Default-category matches, as startActivity resolves an implicit intent; the launcher-icon check passes no flags,
    // since a MAIN + LAUNCHER filter needs no DEFAULT category to show an icon.
    private fun resolvers(
        intent: Intent,
        flags: Int = PackageManager.MATCH_DEFAULT_ONLY,
    ): List<ComponentName> =
        context.packageManager
            .queryIntentActivities(intent, flags)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    @Test
    fun `a shared plain text reaches the entrance`() {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Airport")

        assertEquals(listOf(entrance), resolvers(share))
    }

    @Test
    fun `a geo link reaches the entrance`() {
        val link = Intent(Intent.ACTION_VIEW, "geo:0,0?q=Airport".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

        assertEquals(listOf(entrance), resolvers(link))
    }

    @Test
    fun `the app keeps exactly one launcher icon`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)

        assertEquals(listOf(ComponentName(context, MainActivity::class.java)), resolvers(launcher, flags = 0))
    }

    // Started with FLAG_ACTIVITY_NEW_TASK while the launcher is not running, an entrance with the app's own affinity
    // would found the launcher's task: that task would keep the entrance's geo: base intent and drop out of recents.
    @Test
    fun `the entrance never joins the launcher's task`() {
        val info = context.packageManager.getActivityInfo(entrance, 0)

        assertTrue(info.taskAffinity.isNullOrEmpty(), "taskAffinity = ${info.taskAffinity}")
    }

    @Test
    fun `the entrance stays out of recents and history`() {
        val info = context.packageManager.getActivityInfo(entrance, 0)

        assertEquals(
            ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS or ActivityInfo.FLAG_NO_HISTORY,
            info.flags and (ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS or ActivityInfo.FLAG_NO_HISTORY),
        )
    }

    @Test
    fun `a share leaves its place in the inbox, brings the dashboard forward and finishes`() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Matsuyama Castle")
                .putExtra(Intent.EXTRA_TEXT, "https://maps.example.com/p/123 ")

        val activity = Robolectric.buildActivity(ReceiveDestinationActivity::class.java, share).create().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle")), inbox.pending.value)
        assertEquals(ComponentName(context, MainActivity::class.java), started.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a restored entrance hands nothing over a second time`() {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Airport")

        val activity = Robolectric.buildActivity(ReceiveDestinationActivity::class.java, share).create(Bundle()).get()

        assertNull(inbox.pending.value)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `an action the entrance does not take changes nothing`() {
        val other = Intent(Intent.ACTION_EDIT, "geo:35.6,139.7".toUri()).setComponent(entrance)

        val activity = Robolectric.buildActivity(ReceiveDestinationActivity::class.java, other).create().get()

        assertNull(inbox.pending.value)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }
}
