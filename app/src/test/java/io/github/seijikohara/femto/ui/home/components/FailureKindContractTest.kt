package io.github.seijikohara.femto.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drift guard for the failure kinds that span the TypeScript page and Kotlin.
 *
 * `webmap/src/load-outcome.ts` names the kind of every load failure the page
 * reports; the host retries a failure, and words its notice, by that kind
 * ([NetworkFailureKinds], [RefusedFailureKinds]). A kind renamed or added on
 * one side only moves a failure into the wrong class without a symptom: a
 * network failure bounded and `online`-gated again (the blank map after a
 * start without data), or a refusal retried without end. Each side's own tests
 * pin only their own literals, so these assertions are the only place that
 * drift becomes visible.
 */
class FailureKindContractTest {
    private val source = File("../webmap/src/load-outcome.ts").readText()

    // Both outcome gates pick `network ? <network kind> : <refused kind>`.
    private val gateKinds = OUTCOME_GATE_KINDS.findAll(source).map { it.groupValues[1] to it.groupValues[2] }.toList()

    // initFailureDetail picks `ScriptLoadError ? <network kind> : <other kind>`.
    private val initKinds = INIT_FAILURE_KINDS.findAll(source).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test fun `the host's network kinds are exactly the page's`() {
        assertEquals((gateKinds + initKinds).map { it.first }.toSet(), NetworkFailureKinds)
    }

    @Test fun `the host's refused-request kinds are exactly the page's`() {
        assertEquals(gateKinds.map { it.second }.toSet(), RefusedFailureKinds)
    }

    @Test fun `the host classifies every kind the page reports as the page means it`() {
        (gateKinds + initKinds).forEach { (network, other) ->
            assertTrue(network, isNetworkFailure("$network: detail"))
            assertFalse(other, isNetworkFailure("$other: detail"))
        }
        initKinds.forEach { (_, other) -> assertFalse(other, isRefusedFailure("$other: detail")) }
    }

    @Test fun `the page puts the kind before a colon, where the host reads it`() {
        assertEquals(
            "each fatal the page builds from a kind",
            gateKinds.size + initKinds.size,
            KIND_PREFIX.findAll(source).count(),
        )
    }

    private companion object {
        val OUTCOME_GATE_KINDS = Regex("""evidence\.network\s*\?\s*"([^"]+)"\s*:\s*"([^"]+)"""")
        val INIT_FAILURE_KINDS = Regex("""instanceof ScriptLoadError\s*\?\s*"([^"]+)"\s*:\s*"([^"]+)"""")
        val KIND_PREFIX = Regex("""`\$\{kind}: \$\{""")
    }
}
