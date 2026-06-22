package io.github.seijikohara.femto.data.billing

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillingPreferencesTest {
    // The billingDataStore delegate is a process-wide singleton bound to the first
    // Application's filesDir; Robolectric hands each test method a fresh Application.
    // All DataStore round-trip steps therefore live in one test method so the persisted
    // file and the singleton never disagree across tests — mirrors DisplayPreferencesTest.
    @Test
    fun `defaults to locked then caches and reads back unlocked`() =
        runTest {
            val store = BillingPreferences(ApplicationProvider.getApplicationContext())

            // A fresh store must read back the Locked sentinel exactly.
            assertEquals(Entitlement.Locked, store.cached.first())

            // Cache an unlocked entitlement with a verification timestamp.
            store.cache(Entitlement(mapboxUnlocked = true, lastVerifiedAtMillis = 123L))

            val read = store.cached.first()
            assertTrue(read.mapboxUnlocked)
            assertEquals(123L, read.lastVerifiedAtMillis)

            // Cache null timestamp: the key must be removed so the read returns null
            // rather than the stale 123L value. This is the "revoke / re-lock" path.
            store.cache(Entitlement(mapboxUnlocked = false, lastVerifiedAtMillis = null))
            val revoked = store.cached.first()
            assertEquals(Entitlement.Locked, revoked)
            assertNull(revoked.lastVerifiedAtMillis)
        }
}
