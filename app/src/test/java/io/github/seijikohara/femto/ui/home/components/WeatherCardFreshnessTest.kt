package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.weather.WEATHER_STALE_THRESHOLD
import io.github.seijikohara.femto.testfixtures.FixedDashboardClock
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * The weather card ages its reading against the dashboard's injected clock, not
 * the wall clock, so a capture with a fixed clock reads the fixture as fresh
 * (the condition word) and a fixed clock past the threshold turns the eyebrow
 * into the fetch time — printed in that clock's zone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class WeatherCardFreshnessTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setCard(clock: Clock) =
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onExpand = {},
                    clock = clock,
                )
            }
        }

    @Test
    fun `the captures' fixed clock reads the fixture as fresh`() {
        setCard(FixedDashboardClock)

        rule.onNodeWithText("SUNNY").assertIsDisplayed()
        rule.onNodeWithText("AS OF", substring = true).assertDoesNotExist()
    }

    @Test
    fun `past the threshold the eyebrow is the fetch time in the clock's zone`() {
        val fetchedAt = fakeWeatherSnapshot().fetchedAt
        setCard(Clock.fixed(fetchedAt.plus(WEATHER_STALE_THRESHOLD), ZoneOffset.ofHours(9)))

        // 10:02Z in the +09:00 zone of the clock, not the host's default zone.
        rule.onNodeWithText("AS OF 19:02").assertIsDisplayed()
        rule.onNodeWithText("SUNNY").assertDoesNotExist()
    }

    @Test
    fun `a clock just short of the threshold still reads fresh`() {
        val fetchedAt = fakeWeatherSnapshot().fetchedAt
        setCard(Clock.fixed(fetchedAt.plus(WEATHER_STALE_THRESHOLD).minus(Duration.ofSeconds(1)), ZoneOffset.UTC))

        rule.onNodeWithText("SUNNY").assertIsDisplayed()
    }
}
