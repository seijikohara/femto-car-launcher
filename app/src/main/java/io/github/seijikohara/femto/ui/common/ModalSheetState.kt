@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.seijikohara.femto.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Remember the state of one of the launcher's modal bottom sheets, which starts
 * hidden and animates open.
 *
 * [skipPartiallyExpanded] leaves the sheet two rests, hidden and expanded, so it
 * opens straight to its full height; without it the sheet opens partly expanded
 * first. The shared helper is the SSOT for how that choice maps onto the sheet
 * values Material 3 lets a sheet settle in.
 */
@Composable
internal fun rememberModalSheetState(skipPartiallyExpanded: Boolean): SheetState =
    rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = if (skipPartiallyExpanded) HiddenOrExpanded else EveryRest,
    )

private val HiddenOrExpanded = setOf(SheetValue.Hidden, SheetValue.Expanded)

private val EveryRest = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
