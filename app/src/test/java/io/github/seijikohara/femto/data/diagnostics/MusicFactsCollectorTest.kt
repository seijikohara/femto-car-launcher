package io.github.seijikohara.femto.data.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.CardKind
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.fakeObservedSession
import io.github.seijikohara.femto.testfixtures.fakeSessionTrace
import io.github.seijikohara.femto.testfixtures.fakeShownCard
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives [MusicFactsCollector] over a trace of the test's own, as the collector reads the process-wide one on a device.
 * The spectrum probe answers at once here: the test app holds no audio-recording grant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicFactsCollectorTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val trace = fakeSessionTrace()

    @Test
    fun `a snapshot recorded in the injected trace appears in the collected facts`() =
        runTest {
            trace.record(sessions = listOf(fakeObservedSession()), cardSessionKey = "session-1")

            assertEquals(
                "#1 $FAKE_MUSIC_PACKAGE PLAYING, track 1, on the card",
                collected().valueOf("Sessions"),
            )
        }

    @Test
    fun `the session history lists the injected trace's snapshots`() =
        runTest {
            trace.record(sessions = listOf(fakeObservedSession()), cardSessionKey = "session-1")

            assertEquals(1, collected().facts.count { it.label.startsWith("Session history ") })
        }

    @Test
    fun `an entry recorded in the injected trace's card record appears as a card history fact`() =
        runTest {
            trace.recordCard(fakeShownCard(CardKind.RESUME_OFFERED))

            assertEquals(
                listOf("resume offered for $FAKE_MUSIC_PACKAGE, track 1"),
                collected().facts.filter { it.label.startsWith("Card history ") }.map { it.text() },
            )
        }

    @Test
    fun `an empty injected trace reports that nothing was recorded`() =
        runTest {
            val facts = collected()

            assertTrue(
                listOf("Sessions", "Session history", "Card history").all { label ->
                    facts.valueOf(label)?.startsWith("none recorded") == true
                },
                "an empty trace did not read as none recorded: ${facts.facts}",
            )
        }

    private suspend fun collected(): SectionPayload.Facts =
        MusicFactsCollector(application, AudioSpectrumRepository(application), trace).musicFacts()

    private fun SectionPayload.Facts.valueOf(label: String): String? = facts.firstOrNull { it.label == label }?.text()

    private fun DiagnosticFact.text(): String = (value as FactValue.Text).value
}
