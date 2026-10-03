@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest swaps the motion reading in and out with the switch.

package io.github.seijikohara.femto.data.video

import io.github.seijikohara.femto.data.common.catchAsDefault
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.withParkedDwell
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val TAG = "VideoPictureGate"

/**
 * How long the vehicle must read [VehicleMotion.PARKED] without a break before
 * a hidden video picture returns. The EU's Recommendation 2008/653/EC (§4.3.5.3,
 * non-binding) asks that a picture hidden while driving "does not reappear
 * immediately when the vehicle stops", so a brief stop in traffic does not
 * flash the picture back for a moment.
 */
internal const val VIDEO_PICTURE_STOP_DWELL_MS = 3_000L

/**
 * Whether the video window may show its picture (AGENTS.md#driving-lockout:
 * the feature gates itself). With [hidePictureWhileDriving] on, the picture
 * shows only once [motion] has read [VehicleMotion.PARKED] for
 * [VIDEO_PICTURE_STOP_DWELL_MS] without a break; any other verdict hides it at
 * once. PARKED needs a live GPS fix ([io.github.seijikohara.femto.data.location.vehicleMotionFlow]),
 * so the gate fails closed: no fix, a cached fix, or a receiver gone quiet all
 * hide the picture. With the switch off the picture always shows, and motion
 * is not read at all. A failing motion source hides the picture.
 *
 * The gate decides only the picture. Playback and its audio are the player's,
 * and keep going either way.
 */
internal fun videoPictureVisibleFlow(
    motion: Flow<VehicleMotion>,
    hidePictureWhileDriving: Flow<Boolean>,
): Flow<Boolean> =
    hidePictureWhileDriving
        .distinctUntilChanged()
        .flatMapLatest { hide ->
            if (hide) {
                motion
                    .withParkedDwell(VIDEO_PICTURE_STOP_DWELL_MS)
                    .map { it.parkedThroughDwell }
                    .catchAsDefault(TAG, "vehicle motion", false)
            } else {
                flowOf(true)
            }
        }.distinctUntilChanged()
