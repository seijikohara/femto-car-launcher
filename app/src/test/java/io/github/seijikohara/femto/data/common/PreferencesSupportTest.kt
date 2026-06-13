@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class Sample { ALPHA, BETA }

class PreferencesSupportTest {
    // --- toEnumOr --------------------------------------------------------------

    @Test
    fun `toEnumOr decodes a known name`() {
        assertEquals(Sample.BETA, "BETA".toEnumOr(Sample.ALPHA))
    }

    @Test
    fun `toEnumOr falls back for an unknown name`() {
        // A renamed / removed entry after a downgrade must not throw.
        assertEquals(Sample.ALPHA, "GAMMA".toEnumOr(Sample.ALPHA))
    }

    @Test
    fun `toEnumOr falls back for a null (absent key)`() {
        assertEquals(Sample.ALPHA, null.toEnumOr(Sample.ALPHA))
    }

    // --- catchIoAsDefaults -----------------------------------------------------

    @Test
    fun `catchIoAsDefaults replaces an IOException with empty preferences`() =
        runTest {
            // A corrupted prefs file (e.g. power loss mid-write) must degrade to
            // defaults rather than crash-loop the HOME launcher's cold start.
            val recovered =
                flow<Preferences> { throw IOException("corrupt") }
                    .catchIoAsDefaults("test")
                    .first()

            assertEquals(emptyPreferences(), recovered)
        }

    @Test
    fun `catchIoAsDefaults rethrows a non-IO exception`() =
        runTest {
            // Only IO (an unreadable file) is recoverable; a programming error must
            // surface, not be masked as "empty preferences".
            assertFailsWith<IllegalStateException> {
                flow<Preferences> { throw IllegalStateException("bug") }
                    .catchIoAsDefaults("test")
                    .first()
            }
        }

    @Test
    fun `catchIoAsDefaults passes a successful read through untouched`() =
        runTest {
            val key = booleanPreferencesKey("flag")
            val prefs = mutablePreferencesOf(key to true)

            val read = flow { emit(prefs as Preferences) }.catchIoAsDefaults("test").first()

            assertEquals(true, read[key])
        }

    // --- editOrLog -------------------------------------------------------------

    @Test
    fun `editOrLog applies the transform on success`() =
        runTest {
            val key = booleanPreferencesKey("flag")
            val store = FakePreferencesDataStore()

            store.editOrLog("test") { it[key] = true }

            assertEquals(true, store.data.first()[key])
        }

    @Test
    fun `editOrLog swallows an IOException instead of crashing the launcher`() =
        runTest {
            // Fire-and-forget writes: a failed edit must not escape and kill the
            // HOME process. Losing one write is acceptable.
            val store = FakePreferencesDataStore(failWith = IOException("disk full"))

            // No exception escapes.
            store.editOrLog("test") { it[booleanPreferencesKey("flag")] = true }
        }

    @Test
    fun `editOrLog rethrows cancellation to keep structured concurrency intact`() =
        runTest {
            val store = FakePreferencesDataStore(failWith = CancellationException("cancelled"))

            assertFailsWith<CancellationException> {
                store.editOrLog("test") { it[booleanPreferencesKey("flag")] = true }
            }
        }
}

// Minimal DataStore that applies edits to an in-memory Preferences, or throws a
// configured exception from updateData to drive editOrLog's failure paths.
private class FakePreferencesDataStore(
    private val failWith: Throwable? = null,
) : DataStore<Preferences> {
    private val state = MutableSharedFlow<Preferences>(replay = 1)

    init {
        state.tryEmit(emptyPreferences())
    }

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        failWith?.let { throw it }
        val updated = transform(state.replayCache.first())
        state.emit(updated)
        return updated
    }
}
