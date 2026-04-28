@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.seijikohara.femto.R

private fun variableFont(resId: Int, weight: FontWeight): Font =
    Font(
        resId = resId,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private val WeightAxis = listOf(
    FontWeight.Thin,
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

private fun mixedFamily(latinResId: Int, jpResId: Int): FontFamily =
    FontFamily(
        WeightAxis.flatMap { weight ->
            listOf(
                variableFont(latinResId, weight),
                variableFont(jpResId, weight),
            )
        }
    )

internal object FemtoFonts {
    val Geist: FontFamily = mixedFamily(R.font.geist_variable, R.font.mplus2_variable)
    val MPlus2: FontFamily = FontFamily(
        WeightAxis.map { variableFont(R.font.mplus2_variable, it) }
    )
}

/**
 * Bold Minimal typography on top of M3 roles.
 *
 * Display sizes lean heavier (Black/ExtraBold) for editorial impact;
 * body sizes are bumped up to clear the 18sp automotive minimum.
 */
internal fun femtoTypography(latin: FontFamily): Typography {
    val baseline = Typography()
    return Typography(
        displayLarge = baseline.displayLarge.copy(
            fontFamily = latin, fontWeight = FontWeight.Black, fontSize = 96.sp,
        ),
        displayMedium = baseline.displayMedium.copy(
            fontFamily = latin, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp,
        ),
        displaySmall = baseline.displaySmall.copy(
            fontFamily = latin, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp,
        ),
        headlineLarge = baseline.headlineLarge.copy(
            fontFamily = latin, fontWeight = FontWeight.Bold, fontSize = 40.sp,
        ),
        headlineMedium = baseline.headlineMedium.copy(
            fontFamily = latin, fontWeight = FontWeight.Bold, fontSize = 32.sp,
        ),
        headlineSmall = baseline.headlineSmall.copy(
            fontFamily = latin, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
        ),
        titleLarge = baseline.titleLarge.copy(
            fontFamily = latin, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
        ),
        titleMedium = baseline.titleMedium.copy(
            fontFamily = latin, fontWeight = FontWeight.Medium, fontSize = 20.sp,
        ),
        titleSmall = baseline.titleSmall.copy(
            fontFamily = latin, fontWeight = FontWeight.Medium, fontSize = 18.sp,
        ),
        bodyLarge = baseline.bodyLarge.copy(
            fontFamily = latin, fontWeight = FontWeight.Normal, fontSize = 20.sp,
        ),
        bodyMedium = baseline.bodyMedium.copy(
            fontFamily = latin, fontWeight = FontWeight.Normal, fontSize = 18.sp,
        ),
        bodySmall = baseline.bodySmall.copy(
            fontFamily = latin, fontWeight = FontWeight.Normal, fontSize = 16.sp,
        ),
        labelLarge = baseline.labelLarge.copy(
            fontFamily = latin, fontWeight = FontWeight.Medium, fontSize = 18.sp,
        ),
        labelMedium = baseline.labelMedium.copy(
            fontFamily = latin, fontWeight = FontWeight.Medium, fontSize = 16.sp,
        ),
        labelSmall = baseline.labelSmall.copy(
            fontFamily = latin, fontWeight = FontWeight.Medium, fontSize = 14.sp,
        ),
    )
}
