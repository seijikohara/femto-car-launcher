package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.ui.home.HomeAction
import org.junit.Test
import kotlin.test.assertEquals

// DockConfig.visibleNav / visibleStatus is the ordered, hidden-filtered view
// HorizontalDock/VerticalDock and StatusCluster render from; these tests pin
// its default (today's fixed dock, byte-identical) and the filter behavior a
// future reorder/hide UI will drive.
class DockConfigTest {
    @Test
    fun `defaults to every id in its enum's declared order with nothing hidden`() {
        val config = DockConfig()
        assertEquals(DockNavId.entries, config.visibleNav)
        assertEquals(DockStatusId.entries, config.visibleStatus)
    }

    @Test
    fun `visibleNav is navOrder with navHidden filtered out, order preserved`() {
        val config =
            DockConfig(
                navOrder = listOf(DockNavId.SETTINGS, DockNavId.MUSIC, DockNavId.PHONE),
                navHidden = setOf(DockNavId.MUSIC),
            )
        assertEquals(listOf(DockNavId.SETTINGS, DockNavId.PHONE), config.visibleNav)
    }

    @Test
    fun `visibleStatus is statusOrder with statusHidden filtered out, order preserved`() {
        val config =
            DockConfig(
                statusOrder = listOf(DockStatusId.BATTERY, DockStatusId.GPS, DockStatusId.WIFI),
                statusHidden = setOf(DockStatusId.GPS),
            )
        assertEquals(listOf(DockStatusId.BATTERY, DockStatusId.WIFI), config.visibleStatus)
    }
}

// navSpecFor is the exhaustive DockNavId -> icon/label/action mapping the dock
// renders each visible button from; pinning it here catches a mapping typo or
// an accidental duplicate action independently of the Roborazzi goldens (which
// only catch a mapping change that also shifts pixels).
class NavSpecForTest {
    @Test
    fun `every DockNavId maps to a distinct label resource`() {
        val labels = DockNavId.entries.map { navSpecFor(it).labelRes }
        assertEquals(labels.distinct(), labels)
    }

    @Test
    fun `every DockNavId maps to a distinct action`() {
        val actions = DockNavId.entries.map { navSpecFor(it).action }
        assertEquals(actions.distinct(), actions)
    }

    @Test
    fun `PHONE and MUSIC map to their AppsBarShortcut, not a generic action`() {
        assertEquals(HomeAction.Shortcut(AppsBarShortcut.Phone), navSpecFor(DockNavId.PHONE).action)
        assertEquals(HomeAction.Shortcut(AppsBarShortcut.Music), navSpecFor(DockNavId.MUSIC).action)
    }

    @Test
    fun `APPS opens the drawer and SETTINGS opens settings`() {
        assertEquals(HomeAction.OpenAppDrawer, navSpecFor(DockNavId.APPS).action)
        assertEquals(HomeAction.OpenSettings, navSpecFor(DockNavId.SETTINGS).action)
    }

    @Test
    fun `SETTINGS keeps the settings label`() {
        assertEquals(R.string.nav_settings, navSpecFor(DockNavId.SETTINGS).labelRes)
    }
}
