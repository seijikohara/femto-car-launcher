package io.github.seijikohara.femto.testfixtures

import org.junit.rules.TemporaryFolder
import java.io.File

/** A new file in this folder holding [FakeApkBody], standing in for a downloaded APK. */
internal fun TemporaryFolder.newFakeApk(): File = newFile("update.apk").apply { writeBytes(FakeApkBody) }
