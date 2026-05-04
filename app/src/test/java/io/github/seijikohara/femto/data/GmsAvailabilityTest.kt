package io.github.seijikohara.femto.data

import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GmsAvailabilityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `returns true when api availability returns SUCCESS`() {
        val availability =
            mock<GoogleApiAvailability> {
                on { isGooglePlayServicesAvailable(any()) } doReturn ConnectionResult.SUCCESS
            }
        assertTrue(GmsAvailability(context, availability).isPresent())
    }

    @Test
    fun `returns false when api availability returns SERVICE_MISSING`() {
        val availability =
            mock<GoogleApiAvailability> {
                on { isGooglePlayServicesAvailable(any()) } doReturn ConnectionResult.SERVICE_MISSING
            }
        assertFalse(GmsAvailability(context, availability).isPresent())
    }
}
