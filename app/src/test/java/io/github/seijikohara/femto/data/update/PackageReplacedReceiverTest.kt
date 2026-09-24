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
    fun `the home app comes back as the home screen`() {
        holdHomeRole()
        // Allowed to notify as well: the home app still does not.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), started.categories)
        assertEquals(app.packageName, started.`package`)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, shadowOf(notifications).size())
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

    private fun receive(action: String) = PackageReplacedReceiver().onReceive(app, Intent(action))
}
