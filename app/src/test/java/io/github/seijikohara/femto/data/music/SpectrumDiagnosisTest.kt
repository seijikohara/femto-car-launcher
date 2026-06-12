package io.github.seijikohara.femto.data.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SpectrumDiagnosisTest {
    @Test
    fun `a null emission classifies as engine unavailable`() {
        assertEquals(
            SpectrumDiagnosis.ENGINE_UNAVAILABLE,
            classifySpectrumProbe(listOf(null)),
        )
    }

    @Test
    fun `a null emission wins over later signal`() {
        assertEquals(
            SpectrumDiagnosis.ENGINE_UNAVAILABLE,
            classifySpectrumProbe(listOf(null, floatArrayOf(0.5f))),
        )
    }

    @Test
    fun `any band above the signal level classifies as active`() {
        assertEquals(
            SpectrumDiagnosis.ACTIVE,
            classifySpectrumProbe(listOf(FloatArray(20), floatArrayOf(0f, DIAGNOSIS_SIGNAL_LEVEL + 0.01f))),
        )
    }

    @Test
    fun `all-zero frames classify as silent`() {
        assertEquals(
            SpectrumDiagnosis.SILENT,
            classifySpectrumProbe(listOf(FloatArray(20), FloatArray(20))),
        )
    }

    @Test
    fun `levels at or below the threshold stay silent`() {
        assertEquals(
            SpectrumDiagnosis.SILENT,
            classifySpectrumProbe(listOf(floatArrayOf(DIAGNOSIS_SIGNAL_LEVEL))),
        )
    }

    @Test
    fun `an empty window classifies as silent`() {
        assertEquals(SpectrumDiagnosis.SILENT, classifySpectrumProbe(emptyList()))
    }
}
