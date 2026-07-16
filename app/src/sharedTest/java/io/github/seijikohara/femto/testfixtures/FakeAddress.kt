package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.geocoding.ShortAddress

internal fun fakeAddress(
    locality: String = "Shibuya",
    region: String? = "Tokyo",
    line: String = "",
): ShortAddress = ShortAddress(locality, region, line)
