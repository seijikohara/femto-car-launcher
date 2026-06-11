package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.fonts.CachedFont
import io.github.seijikohara.femto.data.fonts.FontFaceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-memory [FontFaceStore] for repository tests. Downloads succeed instantly by
 * default; a family in [failing] resolves to null (the offline path), and
 * [gateDownload] holds a download in flight until the test releases it. The gate
 * waits inside [NonCancellable] to model the real cache's blocking OkHttp write,
 * which a cancelled resolve pass cannot interrupt mid-stream.
 */
internal class FakeFontFaceStore : FontFaceStore {
    private val store = mutableMapOf<String, CachedFont>()
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Families whose download resolves to null, as when the network is down. */
    val failing = mutableSetOf<String>()

    /** Every family [ensure] actually tried to download (cache misses only). */
    val downloads = mutableListOf<String>()

    /** Every [evictExcept] call in order, as keep set to alsoProtect set. */
    val evictions = mutableListOf<Pair<Set<String>, Set<String>>>()

    fun preload(family: String) {
        store[family] = fontFor(family)
    }

    fun isCached(family: String): Boolean = family in store

    /** Hold the next download of [family] open until the returned gate completes. */
    fun gateDownload(family: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { gates[family] = it }

    override fun cached(family: String): CachedFont? = store[family]

    override suspend fun ensure(family: String): CachedFont? {
        store[family]?.let { return it }
        downloads += family
        gates.remove(family)?.let { gate -> withContext(NonCancellable) { gate.await() } }
        return if (family in failing) null else fontFor(family).also { store[family] = it }
    }

    override fun evictExcept(
        keep: Collection<String>,
        alsoProtect: Collection<String>,
    ) {
        evictions += keep.toSet() to alsoProtect.toSet()
        store.keys.retainAll((keep + alsoProtect).toSet())
    }

    private fun fontFor(family: String): CachedFont = CachedFont.Variable(File("/fonts/$family/variable.ttf"))
}
