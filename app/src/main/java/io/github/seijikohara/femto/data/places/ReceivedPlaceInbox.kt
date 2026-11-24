package io.github.seijikohara.femto.data.places

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place another app has handed the launcher and the dashboard has not
 * taken yet. The entry activity fills it before it starts the dashboard, so a
 * cold start through a share finds the place waiting once the dashboard is up;
 * a second place before the first is taken replaces it.
 *
 * The dashboard marks the place [handled] as soon as the destination panel
 * holds it, which empties the inbox for good: a recreated or rotated activity,
 * and a later return to the launcher, find nothing to open again. A state, not
 * an event stream, because the place must wait for a dashboard that is not
 * composed yet, which an event with no collector would lose.
 */
internal class ReceivedPlaceInbox {
    private val held = MutableStateFlow<ReceivedPlace?>(null)

    /** The place waiting for the dashboard, or null. */
    val pending: StateFlow<ReceivedPlace?> = held.asStateFlow()

    /** Hold [place] for the dashboard, replacing one it has not taken. */
    fun offer(place: ReceivedPlace) {
        held.value = place
    }

    /**
     * Empty the inbox of [place]. A newer place that arrived after the
     * dashboard read [place] stays, so it is not lost to the older one's
     * hand-over.
     */
    fun handled(place: ReceivedPlace) {
        held.compareAndSet(place, null)
    }

    companion object {
        /** The process's inbox: the entry activity and the dashboard run in one process. */
        val shared: ReceivedPlaceInbox = ReceivedPlaceInbox()
    }
}
