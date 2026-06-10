package io.github.seijikohara.femto.data

import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// cached() and evictExcept() are pure filesystem operations, so the API is never
// invoked here — a real instance is supplied only to satisfy the constructor.
class FontCacheTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val root: File get() = tempFolder.root
    private val cache: FontCache get() = FontCache(root, GoogleFontsApi(OkHttpClient()))

    @Test
    fun `cached returns null for an absent family`() {
        assertNull(cache.cached("Roboto"))
    }

    @Test
    fun `cached reads a variable font directory`() {
        File(root, "roboto").mkdirs()
        File(root, "roboto/variable.ttf").writeText("ttf")
        assertTrue(cache.cached("Roboto") is CachedFont.Variable)
    }

    @Test
    fun `cached reads static weights keyed by weight`() {
        File(root, "roboto").mkdirs()
        File(root, "roboto/w400.ttf").writeText("ttf")
        File(root, "roboto/w700.ttf").writeText("ttf")
        val cached = cache.cached("Roboto")
        assertTrue(cached is CachedFont.Static)
        assertEquals(setOf(400, 700), cached.fileByWeight.keys)
    }

    @Test
    fun `evictExcept keeps the retained family and drops the rest`() {
        File(root, "roboto").mkdirs()
        File(root, "roboto/variable.ttf").writeText("ttf")
        File(root, "lobster").mkdirs()
        File(root, "lobster/variable.ttf").writeText("ttf")

        cache.evictExcept(listOf("Roboto"))

        assertTrue(File(root, "roboto").isDirectory)
        assertFalse(File(root, "lobster").exists())
    }

    @Test
    fun `evictExcept spares a protected family outside the keep set`() {
        File(root, "roboto").mkdirs()
        File(root, "roboto/variable.ttf").writeText("ttf")
        File(root, "lobster").mkdirs()
        File(root, "lobster/variable.ttf").writeText("ttf")

        cache.evictExcept(listOf("Roboto"), alsoProtect = listOf("Lobster"))

        assertTrue(File(root, "lobster").isDirectory)
    }
}
