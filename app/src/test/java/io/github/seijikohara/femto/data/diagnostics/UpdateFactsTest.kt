package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import org.junit.Test
import kotlin.test.assertEquals

// The updater's diagnostics facts: the APP channel and check outcome, the
// PERMISSIONS install grant, and the SETTINGS dump's values.
class UpdateFactsTest {
    private val manifest = fakeUpdateManifest(versionCode = 26092501)

    @Test
    fun `the channel fact names the build's channel`() {
        assertEquals(DiagnosticFact("Channel", FactValue.Text("nightly")), channelFact("nightly"))
    }

    @Test
    fun `a flavor without a feed of its own says so`() {
        assertEquals(FactValue.Text("stableFoss (no update feed)"), channelFact("stableFoss").value)
    }

    @Test
    fun `the check fact pairs the outcome with the last attempt`() {
        assertEquals(
            FactValue.Text("up to date (last attempt ${formatEpochMillis(ATTEMPT_MS)})"),
            updateCheckFact(UpdateState.UpToDate, ATTEMPT_MS).value,
        )
    }

    @Test
    fun `the check fact says never when no attempt is recorded`() {
        assertEquals(
            FactValue.Text("no result yet (last attempt never)"),
            updateCheckFact(UpdateState.Idle(lastAttemptAt = null), lastAttemptAtMs = null).value,
        )
    }

    @Test
    fun `an available update is information, not a finding`() {
        assertEquals(
            FactValue.Text("${manifest.versionName} available (last attempt never)"),
            updateCheckFact(UpdateState.Available(manifest), lastAttemptAtMs = null).value,
        )
    }

    @Test
    fun `a failed update action is a warning naming its reason`() {
        assertEquals(
            FactValue.Status("failed: NETWORK (last attempt never)", FactHealth.WARNING),
            updateCheckFact(UpdateState.Failed(UpdateFailure.NETWORK, manifest), lastAttemptAtMs = null).value,
        )
    }

    @Test
    fun `a build that never checks reports no attempt`() {
        assertEquals(
            FactValue.Text("disabled for this build"),
            updateCheckFact(UpdateState.Disabled, ATTEMPT_MS).value,
        )
    }

    @Test
    fun `the install grant is information either way`() {
        assertEquals(FactValue.Status("allowed", FactHealth.INFO), installUnknownAppsFact(allowed = true).value)
        assertEquals(FactValue.Status("not allowed", FactHealth.INFO), installUnknownAppsFact(allowed = false).value)
    }

    @Test
    fun `the settings dump spells out empty bookkeeping`() {
        assertEquals(
            listOf("true", "never", "none", "none", "none", "none", "none"),
            updateSettingsFacts(UpdateSettings.Default).map { (it.value as FactValue.Text).value },
        )
    }

    @Test
    fun `the settings dump shows every bookkeeping record`() {
        val settings =
            UpdateSettings(
                autoCheck = false,
                lastCheckAttemptAt = ATTEMPT_MS,
                pendingInstallVersionCode = 26092501,
                offer = manifest,
                promptedVersionCode = 26092501,
                skippedVersionCode = 26092502,
                refusedVersionCode = 26092503,
            )
        assertEquals(
            listOf(
                "false",
                formatEpochMillis(ATTEMPT_MS),
                "26092501",
                "${manifest.versionName} (26092501)",
                "26092501",
                "26092502",
                "26092503",
            ),
            updateSettingsFacts(settings).map { (it.value as FactValue.Text).value },
        )
    }

    private companion object {
        const val ATTEMPT_MS = 1_790_000_000_000L
    }
}
