package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
    fun renders_apps_and_settings_nav_buttons() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(),
                    onAction = {},
                )
            }
        }
        // The launcher dashboard IS home, so there is no Home button.
        rule.onNodeWithContentDescription("Apps").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
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
    fun tapping_assistant_dispatches_open_assistant() {
        var lastAction: HomeAction? = null
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(),
                    onAction = { lastAction = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Assistant").performClick()
        assertEquals(HomeAction.OpenAssistant, lastAction)
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
    fun shows_cellular_connected_description_when_connected() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(cellularConnected = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Mobile data connected").assertIsDisplayed()
    }

    @Test
    fun hides_cellular_icon_on_telephony_less_unit() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(cellularConnected = null),
                    onAction = {},
                )
            }
        }
        rule.onAllNodesWithContentDescription("Mobile data connected").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Mobile data disconnected").assertCountEquals(0)
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

    @Test
    fun shows_charging_caption_when_charging() {
        rule.setContent {
            FemtoTheme {
                DashboardFooter(
                    systemStatus = fakeSystemStatus(charging = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("Charging").assertIsDisplayed()
    }
}
