package io.github.seijikohara.femto.data.update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AutoCheckEvaluationsTest {
    private val online = MutableStateFlow(false)
    private val ticks = MutableStateFlow(0)

    @Test
    fun `nothing is collected while automatic checks are off`() =
        runTest {
            backgroundScope.launch { autoCheckEvaluations(flowOf(false), online, ticks).collect {} }
            runCurrent()

            // The clock receiver and the network callback stay unregistered.
            assertEquals(0, online.subscriptionCount.value)
            assertEquals(0, ticks.subscriptionCount.value)
        }

    @Test
    fun `turning automatic checks off releases the sources`() =
        runTest {
            val autoCheck = MutableStateFlow(true)
            backgroundScope.launch { autoCheckEvaluations(autoCheck, online, ticks).collect {} }
            runCurrent()
            assertEquals(1, online.subscriptionCount.value)

            autoCheck.value = false
            runCurrent()

            assertEquals(0, online.subscriptionCount.value)
            assertEquals(0, ticks.subscriptionCount.value)
        }

    @Test
    fun `every tick and every online change is evaluated while checks are on`() =
        runTest {
            val evaluations = mutableListOf<Boolean>()
            backgroundScope.launch { autoCheckEvaluations(flowOf(true), online, ticks).collect { evaluations += it } }
            runCurrent()

            ticks.value = 1
            runCurrent()
            online.value = true
            runCurrent()

            assertEquals(listOf(false, false, true), evaluations)
        }

    @Test
    fun `a failing source ends the evaluations instead of throwing`() =
        runTest {
            // A network callback the platform refuses to register (too many
            // callbacks, for one): the updater's scope has no handler, so an
            // escape would take down the HOME process.
            val broken = flow<Boolean> { throw IllegalStateException("callback refused") }

            val evaluations = autoCheckEvaluations(flowOf(true), broken, ticks).toList()

            assertEquals(emptyList(), evaluations)
        }
}
