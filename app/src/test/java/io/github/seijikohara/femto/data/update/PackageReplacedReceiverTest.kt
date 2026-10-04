package io.github.seijikohara.femto.data.update

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.MainActivity
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.receiveAndAwaitFinish
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowRoleManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackageReplacedReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notifications: NotificationManager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun `the home app tries to come back as the home screen`() {
        holdHomeRole()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), started.categories)
        assertEquals(app.packageName, started.`package`)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the home app also announces the update when it may notify`() {
        // Android refuses the home app's start without an error when another
        // launcher's process took the home screen during the install, so the
        // notification is the way back that always works.
        holdHomeRole()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        val notification = shadowOf(notifications).allNotifications.single()
        assertEquals(
            app.getString(R.string.notification_update_title, BuildConfig.VERSION_NAME),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
    }

    @Test
    fun `the home app that may not notify only tries to come back`() {
        holdHomeRole()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(Intent.ACTION_MAIN, shadowOf(app).nextStartedActivity.action)
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test
    fun `the launcher's return removes the update notification`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        app.dismissUpdateNotification()

        assertEquals(0, shadowOf(notifications).size())
    }

    @Test
    fun `the launcher's return leaves the app's other notifications alone`() {
        // Same id, no tag: only the update notification's own tag may match.
        notifications.notify(1, Notification.Builder(app, "other").setSmallIcon(R.drawable.ic_update_installed).build())

        app.dismissUpdateNotification()

        assertEquals(1, shadowOf(notifications).size())
    }

    @Test
    fun `an app that is not home announces the running version when it may notify`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        val notification = shadowOf(notifications).allNotifications.single()
        assertEquals(
            app.getString(R.string.notification_update_title, BuildConfig.VERSION_NAME),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            notifications.getNotificationChannel(notification.channelId).importance,
        )
        val open = shadowOf(notification.contentIntent)
        assertEquals(MainActivity::class.java.name, open.savedIntent.component?.className)
        // Nothing may rewrite where the tap leads.
        assertTrue(open.isImmutable)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `an app that is not home and may not notify does nothing`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, shadowOf(notifications).size())
    }

    @Test
    fun `a broadcast other than the replacement is ignored`() {
        holdHomeRole()

        receive(Intent.ACTION_PACKAGE_ADDED)

        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `the receiver hears its own replacement and is not exported`() {
        val receivers =
            app.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(app.packageName),
                PackageManager.ResolveInfoFlags.of(0),
            )

        val receiver = receivers.single { it.activityInfo.name == PackageReplacedReceiver::class.java.name }
        assertFalse(receiver.activityInfo.exported)
    }

    private fun holdHomeRole() =
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_HOME, app.packageName, Process.myUserHandle())

    // Delivered with a pending result, so the receiver's goAsync() work runs;
    // returns once it has finished the broadcast.
    private fun receive(action: String) = PackageReplacedReceiver().receiveAndAwaitFinish(app, Intent(action))
}
