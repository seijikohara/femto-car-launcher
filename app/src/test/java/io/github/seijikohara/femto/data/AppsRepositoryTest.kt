package io.github.seijikohara.femto.data

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppsRepositoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `launch returns false and does not throw on ActivityNotFoundException`() {
        val repo = AppsRepository(application, launcher = { throw ActivityNotFoundException() })

        assertFalse(repo.launch(STALE_COMPONENT))
    }

    @Test
    fun `launch rethrows non-ActivityNotFoundException errors`() {
        val repo = AppsRepository(application, launcher = { throw SecurityException() })

        assertFailsWith<SecurityException> { repo.launch(STALE_COMPONENT) }
    }

    @Test
    fun `launch returns true on success`() {
        val repo = AppsRepository(application, launcher = {})

        assertTrue(repo.launch(STALE_COMPONENT))
    }

    private companion object {
        val STALE_COMPONENT = ComponentName("com.example", "X")
    }
}
