package io.github.seijikohara.femto.testfixtures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

// Generous next to RECEIVER_WORK_BUDGET_MS: the work runs on a real
// background thread, and a slow CI runner must not fail a receiver that
// finishes in time.
private const val FINISH_WAIT_SECONDS = 30L

/**
 * Deliver [intent] to this receiver the way the platform does, with a pending
 * result that goAsync() can take, and return once the receiver has called
 * finish() on it, or at once when it did not go async. Calling onReceive
 * alone gives goAsync() nothing to return.
 * A receiver that never finishes fails the test with a TimeoutException.
 */
internal fun BroadcastReceiver.receiveAndAwaitFinish(
    context: Context,
    intent: Intent,
) {
    val pending =
        ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
            ShadowBroadcastPendingResult::class.java,
            "create",
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, null),
            ClassParameter.from(Bundle::class.java, null),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false),
        )
    ReflectionHelpers.callInstanceMethod<Unit>(
        this,
        "setPendingResult",
        ClassParameter.from(BroadcastReceiver.PendingResult::class.java, pending),
    )
    onReceive(context, intent)
    // A receiver that did not go async is finished by the platform as soon
    // as onReceive returns.
    if (shadowOf(this).wentAsync()) {
        Shadow.extract<ShadowBroadcastPendingResult>(pending).future.get(FINISH_WAIT_SECONDS, TimeUnit.SECONDS)
    }
}
