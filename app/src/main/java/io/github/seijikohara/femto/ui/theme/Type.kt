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
import androidx.compose.ui.unit.sp

/**
 * Bold Minimal typography on top of M3 roles. Every role's size comes from the
 * rem-style modular scale rooted at [FemtoDimens.BaseTextSize] (16sp), and every
 * weight collapses to one of three tiers (400 ± 200): [FontWeight.Normal] for
 * body / label / caption, [FontWeight.SemiBold] for title / headline / display,
 * and [FontWeight.ExtraLight] — the airy tier used only by the large hero-numeral
 * extensions ([bigNumber] and the clock [heroNumeral]), never by a role here.
 *
 * The copies set fontFamily / fontWeight / fontSize and zero every role's
 * letterSpacing (Bold Minimal runs untracked — the M3 role tracking is
 * overridden); lineHeight stays at the M3 default each role inherits, so
 * re-basing the scale never disturbs a role's vertical rhythm. See
 * `.claude/rules/design-system.md`.
 *
 * [family] is the resolved font family — the system default, or the user's
 * downloaded Google Fonts pair (Latin + CJK fallback). See [buildFontFamily].
 */
internal fun femtoTypography(family: FontFamily): Typography =
    Typography().run {
        copy(
            displayLarge = displayLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text8Xl,
                letterSpacing = 0.sp,
            ),
            displayMedium = displayMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text7Xl,
                letterSpacing = 0.sp,
            ),
            displaySmall = displaySmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text6Xl,
                letterSpacing = 0.sp,
            ),
            headlineLarge = headlineLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text4Xl,
                letterSpacing = 0.sp,
            ),
            headlineMedium = headlineMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text3Xl,
                letterSpacing = 0.sp,
            ),
            headlineSmall = headlineSmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.Text2Xl,
                letterSpacing = 0.sp,
            ),
            titleLarge = titleLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.TextXl,
                letterSpacing = 0.sp,
            ),
            titleMedium = titleMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.TextLg,
                letterSpacing = 0.sp,
            ),
            titleSmall = titleSmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.SemiBold,
                fontSize = FemtoDimens.TextMd,
                letterSpacing = 0.sp,
            ),
            bodyLarge = bodyLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextLg,
                letterSpacing = 0.sp,
            ),
            bodyMedium = bodyMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextMd,
                letterSpacing = 0.sp,
            ),
            bodySmall = bodySmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextSm,
                letterSpacing = 0.sp,
            ),
            labelLarge = labelLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextMd,
                letterSpacing = 0.sp,
            ),
            labelMedium = labelMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextSm,
                letterSpacing = 0.sp,
            ),
            labelSmall = labelSmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = FemtoDimens.TextSm,
                letterSpacing = 0.sp,
            ),
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
 * plus tabular figures so the large numeral never reflows the row beside it.
 * [weight] defaults to ExtraLight (200) — the airy hero tier premium automotive
 * dashboards run their large numerals at (Rivian / Polestar / CarPlay), the
 * lightest of the three weight tiers; AAOS guidance likewise advises against Bold
 * at this size. The maximize panel keeps this default; the compact dashboard cards
 * (calendar day, weather temperature) pass [FontWeight.Normal] at
 * [FemtoDimens.Text4Xl] for a heavier, more legible glance. [size] defaults to the
 * [FemtoDimens.BigNumberFontSize] anchor (the Text6Xl scale step, 56sp) and drives
 * the same 0.92 leading ratio.
 */
internal fun Typography.bigNumber(
    size: TextUnit = FemtoDimens.BigNumberFontSize,
    weight: FontWeight = FontWeight.ExtraLight,
): TextStyle =
    displayLarge.copy(
        fontSize = size,
        fontWeight = weight,
        lineHeight = (size.value * 0.92f).sp,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the glass-overlay hero numeral style (the clock readout, the speed
 * value). [FemtoDimens.Text4Xl] (40sp) tabular digits on a fixed 40sp line;
 * callers pass the [weight] their role warrants — the ambient clock runs at
 * Normal (sharing the dashboard's unified 40sp / Normal numerals) while the
 * safety-critical speed value stays a more emphatic SemiBold.
 */
internal fun Typography.heroNumeral(weight: FontWeight = FontWeight.SemiBold): TextStyle =
    displayMedium.copy(
        fontSize = FemtoDimens.Text4Xl,
        fontWeight = weight,
        lineHeight = FemtoDimens.Text4Xl,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the glance metric-value style (speed-overlay trip metrics, the calendar
 * day-gutter numeral), sized at [FemtoDimens.GlanceMetricSize] (16sp — the TextMd
 * scale step, which sits on the [FemtoDimens.MinBodyTextSize] body floor) with
 * tabular figures so ticking values keep a fixed digit advance.
 */
internal fun Typography.glanceMetric(): TextStyle =
    titleSmall.copy(
        fontSize = FemtoDimens.GlanceMetricSize,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TabularFigures,
    )

/**
 * Return the shared unit-glyph style for the dashboard's measured values — the
 * worded units (km/h, mph, km, mi, m, m/s) and the hero temperature scale
 * (°C / °F). [Typography.labelSmall] held at SemiBold: 12sp, untracked, no
 * tabular figures. The caller dims it (the shared `UnitSuffix` composable) so
 * the unit reads a step below the value it trails. Supersedes the speed
 * overlay's old per-unit `sectionLabel` unit and the temperature's `unitAffix`.
 */
internal fun Typography.unitLabel(): TextStyle = labelSmall.copy(fontWeight = FontWeight.SemiBold)

/**
 * Return the maximize panel's hero metric-value style — the 24sp titleLarge tier
 * held at SemiBold so a panel's headline figure reads heavier than the body
 * around it (the explicit weight keeps it at the title tier regardless of
 * titleLarge's own weight). Named here rather than copied inline at the call
 * site (design rule: no ad-hoc TextStyle literals in components).
 */
internal fun Typography.panelMetric(): TextStyle = titleLarge.copy(fontWeight = FontWeight.SemiBold)

/**
 * Return body text relaxed to the [FemtoDimens.GlanceTextSize] glance floor for
 * dense card metadata — the calendar "no events" / event-title lines and the
 * music "nothing playing" hint, per CLAUDE.md#automotive-overrides.
 */
internal fun Typography.glanceBody(): TextStyle =
    bodyMedium.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        // Caption leading for the 12sp GlanceTextSize (was 18sp, tied to the old
        // 13sp glance size); a ~1.33 ratio keeps the dense metadata line readable.
        lineHeight = 16.sp,
    )

/**
 * Return the shared [FemtoDimens.GlanceTextSize] glance-caption style for the
 * dashboard's dense numeric/metadata text: the dock status readouts, the
 * calendar event time, the weather metric value and forecast temperature, and
 * the speed overlay's address line (CLAUDE.md#automotive-overrides). These
 * sites split across two M3 lineages with different line-box metrics — the
 * label lineage ([Typography.labelLarge], the default) and the body lineage
 * ([Typography.bodyMedium] / [cardMeta], the latter carrying a fixed line box
 * and zero font padding) — so [base] lets a caller keep the lineage its
 * surrounding text already uses instead of silently overriding it. [lineHeight]
 * defaults to [base]'s own
 * (untouched) since most callers are happy with their base role's natural
 * line box; a card with a tighter row passes its own. [fontFeatureSettings]
 * defaults to [TabularFigures] for a value that ticks; pass null for prose
 * (the address line has no digits to keep steady).
 */
internal fun Typography.glanceCaption(
    base: TextStyle = labelLarge,
    fontWeight: FontWeight = FontWeight.SemiBold,
    lineHeight: TextUnit = base.lineHeight,
    fontFeatureSettings: String? = TabularFigures,
): TextStyle =
    base.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        fontFeatureSettings = fontFeatureSettings,
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
        fontWeight = FontWeight.SemiBold,
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
 * [Typography.labelSmall]. Callers pass the explicit size the design SSOT
 * specifies for each strip, so the small sub-floor label sizes stay
 * parameterised rather than hardcoded per card; callers snap [sizeSp] to a scale
 * step — 12 for [FemtoDimens.TextSm], or 8 for [FemtoDimens.TextXs] where a label
 * must stay tiny. Carries tabular figures so labels that embed a number (e.g. the
 * "09h" forecast hour) keep a fixed digit advance; the feature is a no-op on
 * letter-only labels.
 */
internal fun Typography.sectionLabel(
    sizeSp: Int,
    fontWeight: FontWeight = FontWeight.SemiBold,
): TextStyle =
    labelSmall.copy(
        fontSize = sizeSp.sp,
        fontWeight = fontWeight,
        fontFeatureSettings = TabularFigures,
    )

// Uppercase section eyebrow (e.g. the music source, the calendar month) at one
// shared size, so every eyebrow reads identically. Built on [sectionLabel] so
// it inherits the labelSmall + tabular base.
internal fun Typography.eyebrow(): TextStyle = sectionLabel(12)

// The calendar head's weekday name: titleLarge tightened a notch for the head
// unit. Rendered through [FitText] so a long localized weekday ("Wednesday",
// "Mittwoch") shrinks to fit the narrow head column instead of truncating.
internal fun Typography.calendarWeekday(): TextStyle =
    titleLarge.copy(
        fontSize = FemtoDimens.TextLg,
        fontWeight = FontWeight.SemiBold,
        lineHeight = FemtoDimens.TextLg,
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
 * metrics the cards inherit from the retired dashboard-v2 mockup — the
 * [FemtoDimens.TextLg] scale step, one below titleLarge's own 24sp. SemiBold:
 * the title weight tier of the three-weight system, sitting a clear step over
 * the Normal 12sp [cardMeta] metadata through both weight and size. The fixed
 * line box ([FixedLineBox]) keeps the primary-face metrics stable; it does NOT
 * survive a fallback face on its own (see the [FixedLineBox] note) — a
 * single-line slot that must hold its height across Latin↔CJK track switches
 * additionally clamps with [singleLineBox].
 */
internal fun Typography.cardTitle(): TextStyle =
    titleLarge.copy(
        fontSize = FemtoDimens.TextLg,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 23.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Return the dashboard-card secondary metadata line style (artist / album
 * rows). 12sp glance metadata — the [FemtoDimens.TextSm] scale step, one of the
 * sanctioned card relaxations of the [FemtoDimens.MinBodyTextSize] body floor
 * (CLAUDE.md#automotive-overrides). Normal weight so it sits a clear step below
 * the SemiBold [cardTitle] above it (premium media cards subordinate the
 * artist / album through weight + dimmer colour, not just size). Fixed line box
 * as in [cardTitle], with the same caveat: height stability across font
 * fallbacks comes from the caller's [singleLineBox] clamp, not from the style
 * alone.
 */
internal fun Typography.cardMeta(): TextStyle =
    bodyMedium.copy(
        fontSize = FemtoDimens.TextSm,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Return the card call-to-action / empty-state headline style. Sized at the
 * 16sp automotive body floor ([FemtoDimens.MinBodyTextSize]) because the user
 * must be able to read it at a glance to unlock or interpret the card.
 */
internal fun Typography.cardCta(): TextStyle =
    titleMedium.copy(
        fontSize = FemtoDimens.MinBodyTextSize,
        fontWeight = FontWeight.SemiBold,
    )

/**
 * Return the card call-to-action hint body style. The ~4/3 leading (1.33)
 * keeps the two-line hint readable at the 16sp body floor
 * ([FemtoDimens.MinBodyTextSize]); the leading tracks the floor token so it
 * re-derives if the scale is re-based.
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
        fontSize = FemtoDimens.MinBodyTextSize,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        lineHeightStyle = FixedLineBox,
        platformStyle = NoFontPadding,
    )

/**
 * Return [Typography.bodyLarge] pinned to the [FemtoDimens.MinBodyTextSize]
 * automotive floor. bodyLarge's own resting size (20sp) already clears the
 * floor, but the app drawer's search field, row labels, and empty/error
 * states pin it down to exactly the floor rather than inheriting the larger
 * default.
 */
internal fun Typography.drawerBody(): TextStyle = bodyLarge.copy(fontSize = FemtoDimens.MinBodyTextSize)

/**
 * Return the map attribution credit's style. Legal copyright text, not
 * glance content — OSM ODbL / OpenMapTiles CC-BY require it and it is not
 * read on the move — so it is exempt from the 16sp body floor
 * (CLAUDE.md#automotive-overrides) and sized at the smallest scale step,
 * [FemtoDimens.TextXs] (8sp), for this static, arm's-length legal credit.
 */
internal fun Typography.attributionCredit(): TextStyle =
    labelSmall.copy(fontSize = FemtoDimens.TextXs, lineHeight = FemtoDimens.TextXs)

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
