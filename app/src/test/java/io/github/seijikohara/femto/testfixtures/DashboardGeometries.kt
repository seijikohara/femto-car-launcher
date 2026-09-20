package io.github.seijikohara.femto.testfixtures

/**
 * A display geometry the dashboard is rendered at, in dp at mdpi (dp ≈ px).
 *
 * @property id URL- and file-name-safe slug; the goldens are named
 *   `dashboard-<id>.png` and the catalog entries start with it.
 * @property label Human-readable name shown on the catalog page.
 * @property qualifiers The Robolectric configuration string. It is one of the
 *   [DashboardGeometries] constants because `@Config(qualifiers = …)` needs a
 *   compile-time constant; [DashboardGeometriesTest] keeps it in step with the
 *   dp size.
 */
internal data class DashboardGeometry(
    val id: String,
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
    val qualifiers: String,
)

/**
 * The display geometries the launcher realistically ships on — the one list
 * behind the dashboard goldens (`DashboardScreenshotTest`) and the screenshot
 * catalog (`CatalogMatrix`).
 *
 * Selection (in dp): in-car displays run at low density (~mdpi, dp ≈ px), so
 * their dp footprints are large — a mainstream 16:9 head unit is ~1280×720 dp,
 * not the phone-sized figures a high-density assumption would give. The set
 * spans the real-world clusters surveyed across worldwide automakers,
 * aftermarket Android head units, CarPlay / Android Auto projection, and
 * dashboard-mounted phones / tablets:
 *  - 5:3 / 17:10 head units: the 800×480 floor (Android Auto minimum, cheapest
 *    units), the 853×512 reference binding, the 1024×600 budget cluster, and
 *    the 2000×1200 premium panel.
 *  - 16:9 head units: 1280×720 mainstream and the 1920×1080 flagship / Android
 *    Auto ceiling.
 *  - 8:3 ultrawide bar: 1920×720 (OEM-fit widescreens).
 *  - Dashboard phones: 915×412 landscape and 412×915 portrait.
 *  - Tablets / native car portrait: 800×1280, and the tall 1024×1365 /
 *    1200×1920 portrait panels.
 * All at mdpi: density only scales a captured PNG, not the dp layout, so one
 * density keeps the goldens small and the geometries faithful.
 */
internal object DashboardGeometries {
    const val FLOOR_800X480 = "w800dp-h480dp-mdpi"
    const val HEAD_UNIT_853X512 = "w853dp-h512dp-mdpi"
    const val BUDGET_1024X600 = "w1024dp-h600dp-mdpi"
    const val MAINSTREAM_1280X720 = "w1280dp-h720dp-mdpi"
    const val ULTRAWIDE_1920X720 = "w1920dp-h720dp-mdpi"
    const val FLAGSHIP_1920X1080 = "w1920dp-h1080dp-mdpi"
    const val PREMIUM_2000X1200 = "w2000dp-h1200dp-mdpi"
    const val PHONE_LANDSCAPE_915X412 = "w915dp-h412dp-mdpi"
    const val PHONE_PORTRAIT_412X915 = "w412dp-h915dp-mdpi"
    const val TABLET_800X1280 = "w800dp-h1280dp-mdpi"
    const val CAR_PORTRAIT_1024X1365 = "w1024dp-h1365dp-mdpi"
    const val CAR_PORTRAIT_TALL_1200X1920 = "w1200dp-h1920dp-mdpi"

    val all: List<DashboardGeometry> =
        listOf(
            DashboardGeometry("floor-800x480", "Floor 800×480", 800, 480, FLOOR_800X480),
            DashboardGeometry("head-unit-853x512", "Head unit 853×512", 853, 512, HEAD_UNIT_853X512),
            DashboardGeometry("budget-1024x600", "Budget 1024×600", 1024, 600, BUDGET_1024X600),
            DashboardGeometry("mainstream-1280x720", "Mainstream 1280×720", 1280, 720, MAINSTREAM_1280X720),
            DashboardGeometry("ultrawide-1920x720", "Ultrawide 1920×720", 1920, 720, ULTRAWIDE_1920X720),
            DashboardGeometry("flagship-1920x1080", "Flagship 1920×1080", 1920, 1080, FLAGSHIP_1920X1080),
            DashboardGeometry("premium-2000x1200", "Premium 2000×1200", 2000, 1200, PREMIUM_2000X1200),
            DashboardGeometry("phone-landscape-915x412", "Phone landscape 915×412", 915, 412, PHONE_LANDSCAPE_915X412),
            DashboardGeometry("phone-portrait-412x915", "Phone portrait 412×915", 412, 915, PHONE_PORTRAIT_412X915),
            DashboardGeometry("tablet-800x1280", "Tablet 800×1280", 800, 1280, TABLET_800X1280),
            DashboardGeometry("car-portrait-1024x1365", "Car portrait 1024×1365", 1024, 1365, CAR_PORTRAIT_1024X1365),
            DashboardGeometry(
                "car-portrait-tall-1200x1920",
                "Car portrait tall 1200×1920",
                1200,
                1920,
                CAR_PORTRAIT_TALL_1200X1920,
            ),
        )
}
