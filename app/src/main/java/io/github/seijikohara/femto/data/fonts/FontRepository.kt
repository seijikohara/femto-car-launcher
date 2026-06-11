package io.github.seijikohara.femto.data.fonts

import android.content.Context
import android.util.Log
import io.github.seijikohara.femto.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

private const val TAG = "FontRepository"

/** The downloaded faces backing the current selection; null slots are system. */
internal data class ResolvedFonts(
    val latin: CachedFont? = null,
    val cjk: CachedFont? = null,
) {
    companion object {
        val System = ResolvedFonts()
    }
}

/** Catalog availability for the font picker. */
internal sealed interface CatalogState {
    data object Idle : CatalogState

    data object Loading : CatalogState

    data class Loaded(
        val families: List<GoogleFontFamily>,
    ) : CatalogState

    data object Error : CatalogState
}

/** The slice of [GoogleFontsApi] the repository consumes; a seam for JVM tests. */
internal fun interface FontCatalogSource {
    suspend fun catalog(): List<GoogleFontFamily>?
}

/** The slice of [FontCache] the repository consumes; a seam for JVM tests. */
internal interface FontFaceStore {
    fun cached(family: String): CachedFont?

    suspend fun ensure(family: String): CachedFont?

    fun evictExcept(
        keep: Collection<String>,
        alsoProtect: Collection<String>,
    )
}

/** The slice of [FontPreferences] the repository consumes; a seam for JVM tests. */
internal interface FontSelectionStore {
    val selection: Flow<FontSelection>

    suspend fun setFamily(
        slot: FontSlot,
        family: String?,
    )

    suspend fun resetToDefaults()
}

/**
 * App-scoped hub for downloadable Google Fonts. Resolves the persisted
 * [FontSelection] into on-disk faces (downloading on demand), evicts the cache
 * of families the user drops, and serves the catalog to the picker.
 *
 * A singleton so [MainActivity]'s theme and the settings picker share one cache
 * and one in-flight download set. Its [scope] lives for the whole process,
 * which suits a launcher that is only ever the foreground home app. The
 * constructor stays injectable for JVM tests; production wiring goes through
 * [get].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FontRepository internal constructor(
    private val api: FontCatalogSource,
    private val cache: FontFaceStore,
    private val preferences: FontSelectionStore,
    private val catalogFile: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogSerializer = ListSerializer(GoogleFontFamily.serializer())

    val selection: StateFlow<FontSelection> =
        preferences.selection.stateIn(scope, SharingStarted.Eagerly, FontSelection.System)

    // Declared before [resolved]: the eagerly started mapLatest below reads these
    // fields, and stateIn(Eagerly) may run the lambda while construction is still
    // in progress.
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())

    /** Families with an in-flight download, so the picker can show progress. */
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _downloadFailed = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Families whose most recent resolve attempt failed (manifest or download
     * unreachable), so the picker can flag the row and offer a retry. An entry
     * clears when a later resolve succeeds or when the slot moves to another
     * family.
     */
    val downloadFailed: StateFlow<Set<String>> = _downloadFailed.asStateFlow()

    // DataStore skips both the disk write and the re-emission when the chosen
    // family equals the persisted one, so re-tapping a failed family would never
    // reach a new resolve pass. [choose] fires this trigger for unchanged
    // selections to force a fresh pass that retries the download.
    private val retryTrigger = MutableSharedFlow<Unit>()

    /**
     * The resolved faces, recomputed whenever the selection changes (or a retry
     * fires). Evicts the now-unused cache first, then ensures each selected
     * family is downloaded — so a switch frees the previous font's bytes before
     * fetching the new one. Families still downloading are protected from
     * eviction: a fast re-selection cancels the previous mapLatest pass while
     * its OkHttp call is still streaming, and deleting that directory would
     * corrupt the write.
     */
    val resolved: StateFlow<ResolvedFonts> =
        combine(preferences.selection, retryTrigger.onStart { emit(Unit) }) { selection, _ -> selection }
            .mapLatest { selection ->
                // A failed family that is no longer selected has no retry surface;
                // drop it so the picker does not flag a stale row.
                _downloadFailed.update { failed -> failed intersect selection.families }
                cache.evictExcept(selection.families, alsoProtect = _downloading.value)
                ResolvedFonts(
                    latin = resolveSlot(selection.latinFamily),
                    cjk = resolveSlot(selection.cjkFamily),
                )
            }.stateIn(scope, SharingStarted.Eagerly, ResolvedFonts.System)

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    /** Persist a slot choice; null restores that slot to the system font. */
    fun choose(
        slot: FontSlot,
        family: String?,
    ) {
        scope.launch {
            val unchanged = selection.value.familyFor(slot) == family
            preferences.setFamily(slot, family)
            if (unchanged) retryTrigger.emit(Unit)
        }
    }

    fun resetToDefaults() {
        scope.launch { preferences.resetToDefaults() }
    }

    /** Load the catalog once (disk first for offline use, then refresh). */
    fun ensureCatalog() {
        // CAS from the two launchable states (Idle on first call, Error on
        // retry) so concurrent callers cannot both pass a check-then-set gap
        // and double-launch the fetch.
        val claimed =
            _catalog.compareAndSet(CatalogState.Idle, CatalogState.Loading) ||
                _catalog.compareAndSet(CatalogState.Error, CatalogState.Loading)
        if (!claimed) return
        scope.launch {
            readCatalogDisk()?.let { _catalog.value = CatalogState.Loaded(it) }
            val fresh = api.catalog()
            when {
                fresh != null -> {
                    _catalog.value = CatalogState.Loaded(fresh)
                    writeCatalogDisk(fresh)
                }

                _catalog.value !is CatalogState.Loaded -> {
                    _catalog.value = CatalogState.Error
                }
            }
        }
    }

    private suspend fun resolveSlot(family: String?): CachedFont? {
        if (family == null) return null
        cache.cached(family)?.let { font ->
            _downloadFailed.update { it - family }
            return font
        }
        _downloading.update { it + family }
        return try {
            cache.ensure(family).also { font ->
                if (font == null) {
                    Log.w(TAG, "resolve failed for $family; falling back to the system font")
                    _downloadFailed.update { it + family }
                } else {
                    _downloadFailed.update { it - family }
                }
            }
        } finally {
            _downloading.update { it - family }
        }
    }

    private suspend fun readCatalogDisk(): List<GoogleFontFamily>? =
        withContext(Dispatchers.IO) {
            runCatching {
                catalogFile.takeIf { it.isFile }?.readText()?.let { json.decodeFromString(catalogSerializer, it) }
            }.onFailure { Log.w(TAG, "catalog disk read failed", it) }
                .getOrNull()
        }

    private suspend fun writeCatalogDisk(families: List<GoogleFontFamily>) =
        withContext(Dispatchers.IO) {
            runCatching { catalogFile.writeText(json.encodeToString(catalogSerializer, families)) }
                .onFailure { Log.w(TAG, "catalog disk write failed", it) }
            Unit
        }

    companion object {
        @Volatile
        private var instance: FontRepository? = null

        fun get(context: Context): FontRepository =
            instance ?: synchronized(this) { instance ?: create(context).also { instance = it } }

        private fun create(context: Context): FontRepository {
            val app = context.applicationContext
            val client = OkHttpClient()
            val api = GoogleFontsApi(client, metadataBaseUrl = BuildConfig.FONTS_METADATA_BASE_URL)
            val cacheRoot = File(app.filesDir, "google_fonts")
            return FontRepository(
                api = FontCatalogSource(api::catalog),
                cache = FontCache(cacheRoot, api).asFaceStore(),
                preferences = FontPreferences(app).asSelectionStore(),
                catalogFile = File(app.filesDir, "google_fonts_catalog.json"),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
    }
}

// Adapters narrowing the concrete collaborators to the seams above. They live
// here (not on the concrete classes) so the data layer's public surface stays
// unchanged while the repository remains constructible from fakes in JVM tests.
private fun FontCache.asFaceStore(): FontFaceStore =
    object : FontFaceStore {
        override fun cached(family: String): CachedFont? = this@asFaceStore.cached(family)

        override suspend fun ensure(family: String): CachedFont? = this@asFaceStore.ensure(family)

        override fun evictExcept(
            keep: Collection<String>,
            alsoProtect: Collection<String>,
        ) = this@asFaceStore.evictExcept(keep, alsoProtect)
    }

private fun FontPreferences.asSelectionStore(): FontSelectionStore =
    object : FontSelectionStore {
        override val selection: Flow<FontSelection> = this@asSelectionStore.selection

        override suspend fun setFamily(
            slot: FontSlot,
            family: String?,
        ) = this@asSelectionStore.setFamily(slot, family)

        override suspend fun resetToDefaults() = this@asSelectionStore.resetToDefaults()
    }
