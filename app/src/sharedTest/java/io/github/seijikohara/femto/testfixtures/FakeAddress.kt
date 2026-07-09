package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.geocoding.ShortAddress

internal fun fakeAddress(
    locality: String = "Shibuya",
    region: String? = "Tokyo",
): ShortAddress = ShortAddress(locality, region)
