package io.github.seijikohara.femto.testfixtures

import java.io.File

/** The asset the app ships at [path], read from `src/main/assets/`: Gradle runs unit tests in the module directory. */
internal fun shippedAssetFile(path: String): File = File("src/main/assets/$path")
