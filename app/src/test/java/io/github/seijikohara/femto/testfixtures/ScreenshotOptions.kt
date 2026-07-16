package io.github.seijikohara.femto.testfixtures

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * One home for the screenshot suite's Roborazzi comparison options: the small
 * change threshold absorbs sub-pixel antialiasing differences between the
 * golden-record host and the CI runner while still failing on real layout or
 * color regressions. Every `captureRoboImage` call site passes this instead of
 * restating the threshold.
 */
val ScreenshotCompareOptions =
    RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )
