package io.github.seijikohara.femto.data

import android.app.Application
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mapping table for [VoiceRecognizer.messageFor]: the platform error codes
 * collapse into three user-facing strings (no-match, network, generic), and any
 * unknown future code must land on the generic message rather than crash.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceRecognizerMessageTest(
    private val errorCode: Int,
    private val expectedMessageRes: Int,
) {
    @Test
    fun `maps the recognizer error code to its user-facing message`() {
        val recognizer = VoiceRecognizer(ApplicationProvider.getApplicationContext<Application>())

        assertEquals(expectedMessageRes, recognizer.messageFor(errorCode))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "error={0} -> res={1}")
        fun params(): List<Array<Any>> =
            listOf(
                arrayOf(SpeechRecognizer.ERROR_NO_MATCH, R.string.assistant_voice_error_no_match),
                arrayOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, R.string.assistant_voice_error_no_match),
                arrayOf(SpeechRecognizer.ERROR_NETWORK, R.string.assistant_voice_error_network),
                arrayOf(SpeechRecognizer.ERROR_NETWORK_TIMEOUT, R.string.assistant_voice_error_network),
                arrayOf(SpeechRecognizer.ERROR_AUDIO, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_CLIENT, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_SERVER, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, R.string.assistant_voice_error_generic),
                arrayOf(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT, R.string.assistant_voice_error_generic),
                // An unknown future platform code must degrade to the generic message.
                arrayOf(Int.MAX_VALUE, R.string.assistant_voice_error_generic),
            )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceRecognizerFirstTextTest {
    private val recognizer = VoiceRecognizer(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `firstText returns null for a null bundle`() {
        assertNull(recognizer.firstText(null))
    }

    @Test
    fun `firstText returns null when the bundle lacks a results list`() {
        assertNull(recognizer.firstText(Bundle()))
    }

    @Test
    fun `firstText returns null when the results list is empty`() {
        val bundle =
            Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList())
            }

        assertNull(recognizer.firstText(bundle))
    }

    @Test
    fun `firstText returns the first transcript when several are present`() {
        val bundle =
            Bundle().apply {
                putStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                    arrayListOf("navigate home", "navigate Rome"),
                )
            }

        assertEquals("navigate home", recognizer.firstText(bundle))
    }
}
