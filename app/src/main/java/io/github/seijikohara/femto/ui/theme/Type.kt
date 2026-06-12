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
 * Keeps its PascalCase SSOT name across callers rather than ktlint's
 * SCREAMING_SNAKE_CASE for `const val`.
 */
@Suppress("ktlint:standard:property-naming")
internal const val TabularFigures = "tnum"

/**
 * Return the shared big-number display style used for the calendar big-day and
 * the weather big-temperature anchors. Derived from [Typography.displayLarge]
 * with the Bold Minimal tuning the cards apply verbatim, plus tabular figures
 * so the large numeral never reflows the row beside it.
 */
internal fun Typography.bigNumber(): TextStyle =
    displayLarge.copy(
        fontSize = FemtoDimens.BigNumberFontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.045f).em,
        lineHeight = (FemtoDimens.BigNumberFontSize.value * 0.92f).sp,
        fontFeatureSettings = TabularFigures,
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
 * metrics the cards inherit from the retired dashboard-v2 mockup. The line
 * box is fixed ([FixedLineBox]) so a vertically-centred meta block does not
 * shift when consecutive tracks switch between Latin and CJK titles.
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
 * 18sp floor (CLAUDE.md#automotive-overrides). Fixed line box for the same
 * script-independence reason as [cardTitle].
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
internal fun Modifier.singleLineBox(style: TextStyle): Modifier =
    with(LocalDensity.current) {
        height(style.lineHeight.toDp())
            .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
    }
