package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DashboardFooterTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_home_and_apps_nav_buttons() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Home").assertIsDisplayed()
        rule.onNodeWithContentDescription("Apps").assertIsDisplayed()
    }

    @Test
    fun tapping_apps_dispatches_open_app_drawer() {
        var lastAction: HomeAction? = null
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(),
                    onAction = { lastAction = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Apps").performClick()
        assertEquals(HomeAction.OpenAppDrawer, lastAction)
    }

    @Test
    fun shows_wifi_connected_description_when_wifi_connected() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(wifiConnected = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Wi-Fi connected").assertIsDisplayed()
    }

    @Test
    fun shows_bluetooth_disconnected_description_when_bluetooth_disconnected() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(bluetoothConnected = false),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Bluetooth disconnected").assertIsDisplayed()
    }

    @Test
    fun renders_battery_percent_text() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(batteryPercent = 78),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("78%").assertIsDisplayed()
    }
}
