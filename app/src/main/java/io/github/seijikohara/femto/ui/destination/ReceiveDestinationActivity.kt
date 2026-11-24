package io.github.seijikohara.femto.ui.destination

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.seijikohara.femto.MainActivity
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.ReceivedPlaceInbox
import io.github.seijikohara.femto.data.places.readGeoUri
import io.github.seijikohara.femto.data.places.readSharedText

/**
 * The launcher's entrance for a destination from another app: a shared text
 * (`ACTION_SEND`, `text/plain`) or a `geo:` link (`ACTION_VIEW`). It shows no
 * window (`Theme.NoDisplay`), so it must finish before `onResume`: it reads the
 * place, leaves it in [ReceivedPlaceInbox.shared], brings the dashboard
 * forward, and finishes. The dashboard opens the destination panel with the
 * place; nothing routes by itself.
 *
 * The dashboard's own `MainActivity#onCreate` stays as it was: the place
 * waits in the inbox, which a cold start reads once the dashboard is up.
 */
class ReceiveDestinationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored instance already handed its place over; reading it again would open the panel a second time.
        if (savedInstanceState == null) {
            intent.receivedPlaceOrNull()?.let { place ->
                ReceivedPlaceInbox.shared.offer(place)
                // MainActivity is singleTask, so this brings the launcher's existing task forward.
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }
}

/**
 * Return the place this intent carries, or null for an action the entrance does
 * not take (the activity is exported, so any app can address it explicitly).
 *
 * Everything here is another app's input: the extras may fail to unparcel, and
 * the text or link is untrusted. Any failure of the read reads as
 * [ReceivedPlace.Unreadable], the same as input with no place in it, because an
 * exception escaping here would crash the home screen's process on demand.
 */
private fun Intent.receivedPlaceOrNull(): ReceivedPlace? =
    when (action) {
        Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
            runCatching {
                if (action == Intent.ACTION_VIEW) {
                    readGeoUri(dataString.orEmpty())
                } else {
                    readSharedText(
                        text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                        subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                        title = getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString(),
                    )
                }
            }.getOrDefault(ReceivedPlace.Unreadable)
        }

        else -> {
            null
        }
    }
