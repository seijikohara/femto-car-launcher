package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Production registry: one collector per section. The spectrum probe must
 * complete before the log tail is dumped — its Visualizer errors are exactly
 * what the tail exists to carry — so that ordering is an explicit await, not
 * a call-order comment (the v1 fragility this registry replaces).
 *
 * Build a fresh registry per refresh (never reuse a returned list across
 * collections): the spectrum→logs gate is a single-use [CompletableDeferred],
 * already completed on a reused registry, so the log tail would stop waiting
 * on it from the second refresh onward.
 */
internal fun diagnosticsCollectors(context: Context): List<SectionCollector> {
    val app = AppFactsCollector(context)
    val device = DeviceFactsCollector(context)
    val display = DisplayFactsCollector(context)
    val graphics = GraphicsFactsCollector(context)
    val permissions = PermissionFactsCollector(context)
    val music = MusicFactsCollector(context, AudioSpectrumRepository(context))
    val connectivity = ConnectivityFactsCollector(context)
    val environment = EnvironmentFactsCollector(context)
    val performance = PerformanceFactsCollector(context)
    val storage = StorageFactsCollector(context)
    val webView = WebViewFactsCollector(context)
    val map = MapFactsCollector(context)
    val settings = SettingsFactsCollector(context)
    val logs = LogTailCollector()
    val spectrumProbed = CompletableDeferred<Unit>()
    return listOf(
        SectionCollector(SectionId.APP, app::appFacts),
        SectionCollector(SectionId.CRASH_HISTORY, app::crashHistory),
        SectionCollector(SectionId.DEVICE, device::deviceFacts),
        SectionCollector(SectionId.DISPLAY, display::displayFacts),
        SectionCollector(SectionId.GRAPHICS, graphics::graphicsFacts),
        SectionCollector(SectionId.PERMISSIONS, permissions::permissionFacts),
        SectionCollector(SectionId.MUSIC) {
            try {
                music.musicFacts()
            } finally {
                spectrumProbed.complete(Unit)
            }
        },
        SectionCollector(SectionId.NETWORK, connectivity::networkFacts),
        SectionCollector(SectionId.LOCATION, environment::locationFacts),
        SectionCollector(SectionId.LOCALE_TIME, environment::localeTimeFacts),
        SectionCollector(SectionId.PERFORMANCE, performance::performanceFacts),
        SectionCollector(SectionId.STORAGE, storage::storageFacts),
        SectionCollector(SectionId.INPUT, environment::inputFacts),
        SectionCollector(SectionId.WEBVIEW, webView::webViewFacts),
        SectionCollector(SectionId.MAP, map::mapFacts),
        SectionCollector(SectionId.SETTINGS, settings::settingsFacts),
        SectionCollector(SectionId.LOGS) {
            // Bounded: a wedged spectrum probe must not hold the log tail hostage.
            withTimeoutOrNull(SPECTRUM_AWAIT_TIMEOUT_MS) { spectrumProbed.await() }
            logs.logTail()
        },
    )
}

private const val SPECTRUM_AWAIT_TIMEOUT_MS = 5_000L
