package io.github.seijikohara.femto.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// The updateDataStore delegate is a process-wide singleton bound to the first
// Application's filesDir, while Robolectric hands each test method a fresh
// Application (mirrors DisplayPreferencesTest). resetToDefaults() keeps the
// bookkeeping keys on purpose, so every test clears the raw store first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdatePreferencesTest {
    @Test
    fun `an empty store reads the defaults`() =
        runTest {
            val store = clearedStore()

            assertEquals(UpdateSettings.Default, store.settings.first())
        }

    @Test
    fun `every field round-trips`() =
        runTest {
            val store = clearedStore()

            store.setAutoCheck(false)
            store.setLastCheckAttemptAt(ATTEMPT_AT)
            store.setPendingInstallVersionCode(PENDING)
            store.setOffer(OFFER)
            store.recordPrompted(PROMPTED)

            assertEquals(UpdateSettings(false, ATTEMPT_AT, PENDING, OFFER, PROMPTED), store.settings.first())
        }

    @Test
    fun `a prompted record keeps the newest version asked about`() =
        runTest {
            val store = clearedStore()
            store.recordPrompted(PROMPTED)

            // An older offer shown later (a republished older build) must not
            // reopen the prompt for the newer one already answered.
            store.recordPrompted(PROMPTED - 1)

            assertEquals(PROMPTED, store.settings.first().promptedVersionCode)
        }

    @Test
    fun `a newer prompted version replaces the record`() =
        runTest {
            val store = clearedStore()
            store.recordPrompted(PROMPTED)

            store.recordPrompted(PROMPTED + 1)

            assertEquals(PROMPTED + 1, store.settings.first().promptedVersionCode)
        }

    @Test
    fun `a null pending install removes the record`() =
        runTest {
            val store = clearedStore()
            store.setPendingInstallVersionCode(PENDING)

            store.setPendingInstallVersionCode(null)

            assertNull(store.settings.first().pendingInstallVersionCode)
        }

    @Test
    fun `a null offer removes the record`() =
        runTest {
            val store = clearedStore()
            store.setOffer(OFFER)

            store.setOffer(null)

            assertNull(store.settings.first().offer)
        }

    @Test
    fun `an offer record that is not a manifest reads as no offer`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = clearedStore()
            // Written under the store's own key, as a damaged or foreign record would be.
            context.updateDataStore.edit { it[stringPreferencesKey("update_offer")] = "{\"schemaVersion\":1}" }

            assertNull(store.settings.first().offer)
        }

    @Test
    fun `resetToDefaults restores automatic checks and keeps the bookkeeping`() =
        runTest {
            val store = clearedStore()
            store.setAutoCheck(false)
            store.setLastCheckAttemptAt(ATTEMPT_AT)
            store.setPendingInstallVersionCode(PENDING)
            store.setOffer(OFFER)
            store.recordPrompted(PROMPTED)

            store.resetToDefaults()

            assertEquals(
                UpdateSettings(DEFAULT_AUTO_CHECK, ATTEMPT_AT, PENDING, OFFER, PROMPTED),
                store.settings.first(),
            )
        }

    @Test
    fun `a version is prompted up to the recorded one, and not beyond it`() {
        val settings = UpdateSettings.Default.copy(promptedVersionCode = PROMPTED)

        assertEquals(
            listOf(true, true, false),
            listOf(PROMPTED - 1, PROMPTED, PROMPTED + 1).map { settings.promptedFor(it) },
        )
    }

    @Test
    fun `nothing is prompted without a record`() {
        assertFalse(UpdateSettings.Default.promptedFor(PROMPTED))
    }

    private suspend fun clearedStore(): UpdatePreferences {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.updateDataStore.edit { it.clear() }
        return UpdatePreferences(context)
    }

    private companion object {
        const val ATTEMPT_AT = 1_790_000_000_000L
        const val PENDING = 26092402
        const val PROMPTED = 26092501
        val OFFER = fakeUpdateManifest(PENDING)
    }
}
