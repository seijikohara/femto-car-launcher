package io.github.seijikohara.femto.data.common

import kotlinx.coroutines.flow.SharingStarted

/**
 * Shared `stateIn` / `shareIn` start policy for flows the UI subscribes to.
 *
 * The 5 s grace keeps the upstream (location callbacks, broadcast receivers,
 * DataStore reads) alive across a configuration change or Activity recreation
 * — the new subscriber reattaches before the timeout — while still parking the
 * upstream when the launcher genuinely leaves the foreground.
 */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)
