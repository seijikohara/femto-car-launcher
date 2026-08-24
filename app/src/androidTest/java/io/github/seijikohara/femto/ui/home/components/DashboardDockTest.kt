package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardDockTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_apps_and_settings_nav_buttons() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
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
                DashboardDock(
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
                DashboardDock(
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
                DashboardDock(
                    systemStatus = fakeSystemStatus(wifiConnected = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Wi-Fi connected").assertIsDisplayed()
    }

    @Test
    fun shows_bluetooth_on_description_when_enabled_but_not_connected() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(bluetoothEnabled = true, bluetoothConnected = false),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Bluetooth on").assertIsDisplayed()
    }

    @Test
    fun shows_bluetooth_off_description_when_adapter_disabled() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(bluetoothEnabled = false, bluetoothConnected = false),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Bluetooth off").assertIsDisplayed()
    }

    @Test
    fun shows_cellular_connected_description_when_connected() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(cellularConnected = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Mobile data connected").assertIsDisplayed()
    }

    @Test
    fun shows_graduated_cellular_icon_when_level_is_known() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(cellularConnected = true, cellularSignalLevel = 3),
                    onAction = {},
                )
            }
        }
        // The graduated path still labels the indicator "Mobile data connected"; the
        // glyph differs by level but the semantics stay stable for the screen reader.
        rule.onNodeWithContentDescription("Mobile data connected").assertIsDisplayed()
    }

    @Test
    fun degrades_cellular_to_binary_icon_when_level_is_null() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    // Connected but level unknown (READ_PHONE_STATE withheld): the
                    // indicator still renders, falling back to the binary glyph.
                    systemStatus = fakeSystemStatus(cellularConnected = true, cellularSignalLevel = null),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Mobile data connected").assertIsDisplayed()
    }

    @Test
    fun shows_cellular_disconnected_when_signal_level_is_zero() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    // A known level drives the lit state off signal presence, not the
                    // validated-route flag: zero bars reads as disconnected even while
                    // the cellular network momentarily holds NET_CAPABILITY_VALIDATED.
                    systemStatus = fakeSystemStatus(cellularConnected = true, cellularSignalLevel = 0),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Mobile data disconnected").assertIsDisplayed()
    }

    @Test
    fun shows_graduated_wifi_icon_when_connected_with_a_level() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(wifiConnected = true, wifiSignalLevel = 1),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Wi-Fi connected").assertIsDisplayed()
    }

    @Test
    fun hides_cellular_icon_on_telephony_less_unit() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(cellularConnected = null),
                    onAction = {},
                )
            }
        }
        rule.onAllNodesWithContentDescription("Mobile data connected").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Mobile data disconnected").assertCountEquals(0)
    }

    @Test
    fun shows_gps_fixed_description_when_gps_is_fixed() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(gpsFixed = true),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("GPS fixed").assertIsDisplayed()
    }

    @Test
    fun shows_gps_searching_description_when_gps_is_not_fixed() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(gpsFixed = false),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("GPS searching").assertIsDisplayed()
    }

    @Test
    fun renders_gps_satellite_count_text() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(gpsFixed = true, gpsSatelliteCount = 9),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("9").assertIsDisplayed()
    }

    @Test
    fun renders_battery_percent_text() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(batteryPercent = 78),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("78%").assertIsDisplayed()
    }

    @Test
    fun does_not_render_a_charging_caption() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(charging = true),
                    onAction = {},
                )
            }
        }
        // Charging reads from the bolt glyph and tint alone; no text caption.
        rule.onNodeWithText("Charging").assertDoesNotExist()
    }

    @Test
    fun vertical_rail_renders_all_nav_buttons() {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(),
                    onAction = {},
                    position = DockPosition.LEFT,
                )
            }
        }
        rule.onNodeWithContentDescription("Apps").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        rule.onNodeWithContentDescription("Phone").assertIsDisplayed()
    }

    // --- Long-press dock edit menu (nav buttons + status indicators) ---

    private fun setDock(
        dockConfig: DockConfig = DockConfig(),
        onAction: (HomeAction) -> Unit = {},
    ) {
        rule.setContent {
            FemtoTheme {
                DashboardDock(
                    systemStatus = fakeSystemStatus(),
                    onAction = onAction,
                    dockConfig = dockConfig,
                )
            }
        }
    }

    @Test
    fun long_pressing_a_nav_button_dispatches_move_left() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        // A long press opens the button's edit menu (combinedClickable's long-press);
        // Apps is not the first button, so Move left swaps it one step earlier.
        rule.onNodeWithContentDescription("Apps").performTouchInput { longClick() }
        rule.onNodeWithText("Move left").performClick()
        assertEquals(HomeAction.MoveDockNav(DockNavId.APPS, -1), lastAction)
    }

    @Test
    fun long_pressing_a_nav_button_dispatches_move_right() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        rule.onNodeWithContentDescription("Apps").performTouchInput { longClick() }
        rule.onNodeWithText("Move right").performClick()
        assertEquals(HomeAction.MoveDockNav(DockNavId.APPS, 1), lastAction)
    }

    @Test
    fun long_pressing_a_nav_button_dispatches_hide() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        rule.onNodeWithContentDescription("Apps").performTouchInput { longClick() }
        rule.onNodeWithText("Hide").performClick()
        assertEquals(HomeAction.HideDockNav(DockNavId.APPS), lastAction)
    }

    @Test
    fun nav_button_menu_reset_dock_dispatches_reset() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        rule.onNodeWithContentDescription("Apps").performTouchInput { longClick() }
        rule.onNodeWithText("Reset dock").performClick()
        assertEquals(HomeAction.ResetDock, lastAction)
    }

    @Test
    fun the_first_nav_button_menu_omits_move_left() {
        setDock()
        // Phone is the first visible button (canMoveLeft = index > 0 is false), so
        // its menu offers Move right but not Move left.
        rule.onNodeWithContentDescription("Phone").performTouchInput { longClick() }
        rule.onNodeWithText("Move right").assertExists()
        rule.onNodeWithText("Move left").assertDoesNotExist()
    }

    @Test
    fun the_last_nav_button_menu_omits_move_right() {
        setDock()
        // Settings is the last visible button (canMoveRight = index < lastIndex is
        // false), so its menu offers Move left but not Move right.
        rule.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        rule.onNodeWithText("Move left").assertExists()
        rule.onNodeWithText("Move right").assertDoesNotExist()
    }

    @Test
    fun the_sole_visible_nav_button_menu_omits_hide() {
        // The nav floor holds: once everything but one button is hidden the
        // survivor's menu omits Hide, so the dock can never lose its last
        // actionable button. Status indicators carry no such floor — they are
        // read-only, so an empty cluster is a layout the design already produces.
        setDock(dockConfig = DockConfig(navHidden = DockNavId.entries.toSet() - DockNavId.APPS))
        rule.onNodeWithContentDescription("Apps").performTouchInput { longClick() }
        rule.onNodeWithText("Hide").assertDoesNotExist()
        // Reset dock stays available even for the sole survivor.
        rule.onNodeWithText("Reset dock").assertExists()
    }

    @Test
    fun a_reordered_and_hidden_dock_config_renders_the_order_and_drops_the_hidden() {
        // Settings moved to the front, Music hidden: the visible buttons follow the
        // configured order and the hidden one is absent.
        setDock(
            dockConfig =
                DockConfig(
                    navOrder = listOf(DockNavId.SETTINGS) + (DockNavId.entries - DockNavId.SETTINGS),
                    navHidden = setOf(DockNavId.MUSIC),
                ),
        )
        rule.onNodeWithContentDescription("Music").assertDoesNotExist()
        // Settings now precedes Phone in the row (it is the last button by default).
        val settingsLeft = rule.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot().left
        val phoneLeft = rule.onNodeWithContentDescription("Phone").getUnclippedBoundsInRoot().left
        // kotlin.test.assertTrue, not a bare assert(): ART runs with JVM assertions
        // disabled, so assert() would silently pass on-device even if the order broke.
        assertTrue(settingsLeft < phoneLeft)
    }

    @Test
    fun long_pressing_a_status_indicator_dispatches_move_status() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        // Status icons are read-only at rest, so their long-press menu is wired via a
        // raw pointerInput rather than combinedClickable. Wi-Fi is not the first
        // indicator, so Move left is offered and swaps it one step earlier.
        rule.onNodeWithContentDescription("Wi-Fi connected").performTouchInput { longClick() }
        rule.onNodeWithText("Move left").performClick()
        assertEquals(HomeAction.MoveDockStatus(DockStatusId.WIFI, -1), lastAction)
    }

    @Test
    fun long_pressing_a_status_indicator_dispatches_hide_status() {
        var lastAction: HomeAction? = null
        setDock(onAction = { lastAction = it })
        rule.onNodeWithContentDescription("Wi-Fi connected").performTouchInput { longClick() }
        rule.onNodeWithText("Hide").performClick()
        assertEquals(HomeAction.HideDockStatus(DockStatusId.WIFI), lastAction)
    }
}
