package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.data.display.MotionTier

/**
 * One home for dashboard motion so every surface animates identically and the
 * user's MotionTier applies everywhere, Material components included (through
 * [scheme]). STANDARD = fade + subtle scale; REDUCED = fade, no scale; OFF =
 * instant. The maximize panels time their transitions from the theme's motion
 * scheme; the content cross-fades keep their own tween durations.
 */
internal object Motion {
    private const val STANDARD_IN_MS = 220
    private const val REDUCED_MS = 120
    private const val ENTER_SCALE = 0.92f

    // Held as vals so [tier] can recognise a scheme by identity: the two Material
    // schemes are not guaranteed to be singletons across releases.
    private val ExpressiveScheme = MotionScheme.expressive()
    private val StandardScheme = MotionScheme.standard()

    /**
     * Return the Material motion scheme [FemtoTheme] installs for [tier], so the
     * Material components follow the same setting as the dashboard. REDUCED takes
     * the standard scheme because the expressive springs overshoot and settle
     * noticeably slower, which is what REDUCED exists to avoid on a 30 Hz
     * head-unit panel; OFF takes a scheme that never animates.
     */
    fun scheme(tier: MotionTier): MotionScheme =
        when (tier) {
            MotionTier.STANDARD -> ExpressiveScheme
            MotionTier.REDUCED -> StandardScheme
            MotionTier.OFF -> NoMotionScheme
        }

    /**
     * Return the tier whose [scheme] [FemtoTheme] installed, the inverse of [scheme].
     * Lets a shared component follow the user's setting from `MaterialTheme.motionScheme`
     * instead of a parameter threaded through every screen. Outside [FemtoTheme] a plain
     * `MaterialTheme` carries Material's own default, the standard scheme, which reads as
     * REDUCED; a scheme this object never handed out reads as STANDARD, the default tier.
     */
    fun tier(scheme: MotionScheme): MotionTier =
        when {
            scheme === StandardScheme -> MotionTier.REDUCED
            scheme === NoMotionScheme -> MotionTier.OFF
            else -> MotionTier.STANDARD
        }

    /**
     * Return the maximize-panel enter transition for [tier], timed by [scheme]
     * (the theme's `MaterialTheme.motionScheme`). The scale rides the spatial
     * spec so the panel grows into place with the scheme's spring. OFF ignores
     * [scheme] and snaps, so the no-motion guarantee never depends on the caller
     * passing the matching scheme.
     */
    fun panelEnter(
        tier: MotionTier,
        scheme: MotionScheme,
    ): EnterTransition =
        when (tier) {
            MotionTier.STANDARD -> fadeIn(scheme.defaultEffectsSpec()) +
                scaleIn(scheme.defaultSpatialSpec(), initialScale = ENTER_SCALE)

            MotionTier.REDUCED -> fadeIn(scheme.defaultEffectsSpec())

            MotionTier.OFF -> fadeIn(snap())
        }

    /**
     * Return the maximize-panel exit transition for [tier], timed by [scheme].
     * Both the fade and the scale ride the effects spec: the expressive spatial
     * spring overshoots, and a closing panel that bounces past its target scale
     * reads as a glitch rather than a dismissal.
     */
    fun panelExit(
        tier: MotionTier,
        scheme: MotionScheme,
    ): ExitTransition =
        when (tier) {
            MotionTier.STANDARD -> fadeOut(scheme.defaultEffectsSpec()) +
                scaleOut(scheme.defaultEffectsSpec(), targetScale = ENTER_SCALE)

            MotionTier.REDUCED -> fadeOut(scheme.defaultEffectsSpec())

            MotionTier.OFF -> fadeOut(snap())
        }

    /**
     * Tier-aware fade spec for a discrete content swap — every card-level
     * dissolve (album art, track metadata, a weather/calendar refresh).
     * STANDARD/REDUCED fade over their duration; OFF snaps instantly, so a
     * MotionTier.OFF user never sees any of these dissolve. [targetState] at
     * the call site must be a discrete identity
     * (a track key, a fetch timestamp, a snapshot) — never a per-frame value
     * (a progress fraction, a clock tick) — or the fade thrashes every frame
     * instead of firing once per real content change.
     */
    private fun contentFadeSpec(tier: MotionTier): FiniteAnimationSpec<Float> =
        when (tier) {
            MotionTier.STANDARD -> tween(STANDARD_IN_MS)
            MotionTier.REDUCED -> tween(REDUCED_MS)
            MotionTier.OFF -> snap()
        }

    /**
     * Thin [CrossDissolve] wrapper that always routes through
     * [contentFadeSpec], so every content-swap fade in the dashboard honors
     * [tier] the same way instead of each call site re-deriving (or
     * forgetting) the tier-aware spec. It dissolves through [CrossDissolve]
     * rather than the stock `Crossfade`, whose two-layer alpha dips combined
     * coverage mid-transition and let the live map bleed through the glass as
     * a visible flick.
     */
    @Composable
    fun <T> ContentCrossfade(
        targetState: T,
        tier: MotionTier,
        label: String,
        modifier: Modifier = Modifier,
        content: @Composable (T) -> Unit,
    ) = CrossDissolve(
        targetState = targetState,
        animationSpec = contentFadeSpec(tier),
        label = label,
        modifier = modifier,
        content = content,
    )
}

/**
 * The MotionTier.OFF scheme: every spec snaps, so Material components that read
 * the theme's motion scheme jump straight to their end state. Neither built-in
 * scheme can express "no motion" — both are springs.
 */
private object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
