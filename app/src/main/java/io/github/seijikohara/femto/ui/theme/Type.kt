package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * the 18sp automotive minimum. See `CLAUDE.md#design-system`.
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
