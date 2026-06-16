package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Bold Minimal typography on top of M3 roles, tuned one weight notch lighter
 * than the original scale after on-device review found the heavy display/headline
 * weights too dense on the head unit.
 *
 * Display anchors stay confident (ExtraBold/Bold) for editorial impact but no
 * longer reach Black; headlines drop to SemiBold and titles to Medium. Body and
 * label roles are unchanged (already Normal/Medium) and body sizes still clear
 * the 18sp automotive minimum. See `.claude/rules/design-system.md`.
 *
 * [family] is the resolved font family — the system default, or the user's
 * downloaded Google Fonts pair (Latin + CJK fallback). See [buildFontFamily].
 */
internal fun femtoTypography(family: FontFamily): Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 96.sp),
            displayMedium = displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 72.sp),
            displaySmall = displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 56.sp),
            headlineLarge = headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
            headlineMedium = headlineMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
            ),
            headlineSmall = headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 26.sp),
            titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 24.sp),
            titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 20.sp),
            titleSmall = titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 18.sp),
            bodyLarge = bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 20.sp),
            bodyMedium = bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 18.sp),
            bodySmall = bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp),
            labelLarge = labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 18.sp),
            labelMedium = labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp),
            labelSmall = labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        )
    }

/**
 * OpenType `tnum` feature tag. Tabular figures give every digit the same
 * advance width so changing numbers (clock, speed, temperature, day) do not
 * shift surrounding layout as their glyphs change.
 *
 * A plain `val`, not `const val`: ktlint mandates SCREAMING_SNAKE_CASE for
 * compile-time constants, and this keeps its PascalCase SSOT name across
 * callers.
 */
internal val TabularFigures = "tnum"

/**
 * Return the shared big-number display style used for the calendar big-day and
 * the weather big-temperature anchors. Derived from [Typography.displayLarge]
 * with the Bold Minimal tuning the cards apply verbatim, plus tabular figures
 * so the large numeral never reflows the row beside it. [size] defaults to the
 * [FemtoDimens.BigNumberFontSize] anchor; a card with tighter geometry (the
 * weather hero temperature) passes its own size and inherits the same 0.92
 * leading ratio.
 */
internal fun Typography.bigNumber(size: TextUnit = FemtoDimens.BigNumberFontSize): TextStyle =
    displayLarge.copy(
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045f).em,
        lineHeight = (size.value * 0.92f).sp,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the glass-overlay hero numeral style (the clock readout, the speed
 * value). 40sp Bold tabular digits on a fixed 40sp line; callers pass the
 * tracking their glyph run needs (the clock's colon tolerates tighter
 * tracking than the speed's digit run).
 */
internal fun Typography.heroNumeral(trackingEm: Float): TextStyle =
    displayMedium.copy(
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = trackingEm.em,
        lineHeight = 40.sp,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the 16sp glance metric-value style (speed-overlay trip metrics, the
 * calendar day-gutter numeral) — a sanctioned card relaxation of the 18sp
 * floor (CLAUDE.md#automotive-overrides), with tabular figures so ticking
 * values keep a fixed digit advance.
 */
internal fun Typography.glanceMetric(): TextStyle =
    titleSmall.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return body text relaxed to the [FemtoDimens.GlanceTextSize] glance floor for
 * dense card metadata — the calendar "no events" / event-title lines and the
 * music "nothing playing" hint, per CLAUDE.md#automotive-overrides.
 */
internal fun Typography.glanceBody(): TextStyle =
    bodyMedium.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        lineHeight = 18.sp,
    )

/**
 * Return the music transport's position / duration caption style. Sized at
 * the [FemtoDimens.GlanceTextSize] glance floor — the progress-caption
 * relaxation CLAUDE.md#automotive-overrides routes through that token — with
 * tabular figures so the ticking position never reflows the progress row.
 */
internal fun Typography.progressCaption(): TextStyle =
    labelMedium.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the dense monospace reference-text style for legal / log content shown
 * in sub-sheets (the open-source licenses body and the diagnostics log tail).
 * Relaxed to the [FemtoDimens.GlanceTextSize] glance floor per
 * CLAUDE.md#automotive-overrides.
 */
internal fun Typography.monoReference(): TextStyle =
    bodySmall.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        fontFamily = FontFamily.Monospace,
    )

/**
 * Return an uppercase eyebrow / section-label style derived from
 * [Typography.labelSmall]. Callers pass the explicit size and tracking the
 * design SSOT specifies for each strip, so the relaxed sub-18sp eyebrow sizes
 * stay parameterised rather than hardcoded per card. Carries tabular figures so
 * labels that embed a number (e.g. the "09h" forecast hour) keep a fixed digit
 * advance; the feature is a no-op on letter-only labels.
 */
internal fun Typography.sectionLabel(
    sizeSp: Int,
    trackingEm: Float,
): TextStyle =
    labelSmall.copy(
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = trackingEm.em,
        fontFeatureSettings = TabularFigures,
    )

// Shared line-box policy for the styles whose rows aim at a stable height:
// centred, untrimmed, Fixed mode. NOTE measured on-device: this alone does
// NOT pin a line that renders through a FALLBACK face — Android applies
// fallback line spacing after the line-height machinery, so a CJK line still
// grows to the fallback's taller metrics. Single-line slots that must be
// script-independent additionally clamp their layout height with
// [singleLineBox]; this style keeps the primary-face behaviour consistent.
private val FixedLineBox =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
        mode = LineHeightStyle.Mode.Fixed,
    )
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * Return the dashboard-card primary line style (e.g. the now-playing track
 * title). Derived from [Typography.titleLarge] with the tighter 20sp/23sp
 * metrics the cards inherit from the retired dashboard-v2 mockup. The fixed
 * line box ([FixedLineBox]) keeps the primary-face metrics stable; it does
 * NOT survive a fallback face on its own (see the [FixedLineBox] note) — a
 * single-line slot that must hold its height across Latin↔CJK track
 * switches additionally clamps with [singleLineBox].
 */
internal fun Typography.cardTitle(): TextStyle =
    titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.02f).em,
        lineHeight = 23.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Return the dashboard-card secondary metadata line style (artist / album
 * rows). 14sp glance metadata — one of the sanctioned card relaxations of the
 * 18sp floor (CLAUDE.md#automotive-overrides). Fixed line box as in
 * [cardTitle], with the same caveat: height stability across font fallbacks
 * comes from the caller's [singleLineBox] clamp, not from the style alone.
 */
internal fun Typography.cardMeta(): TextStyle =
    bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Return the card call-to-action / empty-state headline style. Sized at the
 * 18sp automotive glance floor ([FemtoDimens.MinBodyTextSize]) because the
 * user must be able to read it at a glance to unlock or interpret the card.
 */
internal fun Typography.cardCta(): TextStyle =
    titleMedium.copy(
        fontSize = FemtoDimens.MinBodyTextSize,
        fontWeight = FontWeight.SemiBold,
    )

/**
 * Return the card call-to-action hint body style. The ~4/3 leading (1.33)
 * keeps the two-line hint readable at the 18sp glance floor.
 */
internal fun Typography.cardCtaHint(): TextStyle =
    bodyMedium.copy(
        fontSize = FemtoDimens.MinBodyTextSize,
        lineHeight = FemtoDimens.MinBodyTextSize * 1.33f,
    )

/**
 * Return the app-tile label style: a single line with a deterministic line-box
 * height. Different scripts resolve to different faces (the CJK fallback
 * carries taller metrics than the Latin face), so an untrimmed one-line label
 * measures taller for some apps than others and breaks the drawer grid's
 * lattice. The fixed, centred, untrimmed-at-both-edges line height makes every
 * label — and therefore every tile — measure identically regardless of which
 * font renders it.
 */
internal fun Typography.tileLabel(): TextStyle =
    labelLarge.copy(
        lineHeight = 26.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Constrain a single-line [androidx.compose.material3.Text] to exactly its
 * [style]'s `lineHeight`, regardless of which font face renders it.
 *
 * Why a layout clamp and not a text style: Android applies *fallback line
 * spacing* after the line-height machinery, so a line whose glyphs resolve
 * through a fallback face (e.g. CJK over a Latin primary) grows to the
 * fallback's taller metrics even under [LineHeightStyle.Mode.Fixed] —
 * measured on-device. The fixed-height slot pins the row's measured height;
 * `wrapContentHeight(unbounded)` lets the taller text measure freely and
 * centres it in the slot, and since CJK ink stays within the em box the
 * overflow is metric air, not visible clipping.
 */
@Composable
internal fun Modifier.singleLineBox(style: TextStyle): Modifier {
    // toDp() would throw a unitless IllegalStateException for em/unspecified;
    // fail fast with the actual contract instead.
    require(style.lineHeight.isSp) {
        "singleLineBox needs a style with an sp lineHeight, got ${style.lineHeight}"
    }
    return with(LocalDensity.current) {
        height(style.lineHeight.toDp())
            .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
    }
}
