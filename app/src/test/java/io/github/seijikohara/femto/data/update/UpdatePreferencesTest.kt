package io.github.seijikohara.femto.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
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

            assertEquals(UpdateSettings(false, ATTEMPT_AT, PENDING), store.settings.first())
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
    fun `resetToDefaults restores automatic checks and keeps the bookkeeping`() =
        runTest {
            val store = clearedStore()
            store.setAutoCheck(false)
            store.setLastCheckAttemptAt(ATTEMPT_AT)
            store.setPendingInstallVersionCode(PENDING)

            store.resetToDefaults()

            assertEquals(UpdateSettings(DEFAULT_AUTO_CHECK, ATTEMPT_AT, PENDING), store.settings.first())
        }

    private suspend fun clearedStore(): UpdatePreferences {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.updateDataStore.edit { it.clear() }
        return UpdatePreferences(context)
    }

    private companion object {
        const val ATTEMPT_AT = 1_790_000_000_000L
        const val PENDING = 26092402
    }
}
