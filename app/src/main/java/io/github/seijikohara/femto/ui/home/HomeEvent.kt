package io.github.seijikohara.femto.ui.home

/**
 * One-shot side-effect signals emitted by [HomeViewModel] for the host to act on.
 *
 * Events are distinct from [HomeAction] (input) and [HomeUiState] (output): they
 * carry transient navigation / system-call requests that must not be replayed
 * on configuration change. Subscribers collect them via a `SharedFlow` so the
 * latest value is not retained.
 */
internal sealed interface HomeEvent {
    data object OpenDrawer : HomeEvent
}
