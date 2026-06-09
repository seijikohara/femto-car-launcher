package io.github.seijikohara.femto.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
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

/**
 * App-scoped hub for downloadable Google Fonts. Resolves the persisted
 * [FontSelection] into on-disk faces (downloading on demand), evicts the cache
 * of families the user drops, and serves the catalog to the picker.
 *
 * A singleton so [MainActivity]'s theme and the settings picker share one cache
 * and one in-flight download set. Its [scope] lives for the whole process,
 * which suits a launcher that is only ever the foreground home app.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class FontRepository private constructor(
    private val api: GoogleFontsApi,
    private val cache: FontCache,
    private val preferences: FontPreferences,
    private val catalogFile: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogSerializer = ListSerializer(GoogleFontFamily.serializer())

    val selection: StateFlow<FontSelection> =
        preferences.selection.stateIn(scope, SharingStarted.Eagerly, FontSelection.System)

    /**
     * The resolved faces, recomputed whenever the selection changes. Evicts the
     * now-unused cache first, then ensures each selected family is downloaded —
     * so a switch frees the previous font's bytes before fetching the new one.
     */
    val resolved: StateFlow<ResolvedFonts> =
        preferences.selection
            .mapLatest { selection ->
                cache.evictExcept(selection.families)
                ResolvedFonts(
                    latin = resolveSlot(selection.latinFamily),
                    cjk = resolveSlot(selection.cjkFamily),
                )
            }.stateIn(scope, SharingStarted.Eagerly, ResolvedFonts.System)

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())

    /** Families with an in-flight download, so the picker can show progress. */
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    /** Persist a slot choice; null restores that slot to the system font. */
    fun choose(
        slot: FontSlot,
        family: String?,
    ) {
        scope.launch { preferences.setFamily(slot, family) }
    }

    fun resetToDefaults() {
        scope.launch { preferences.resetToDefaults() }
    }

    /** Load the catalog once (disk first for offline use, then refresh). */
    fun ensureCatalog() {
        if (_catalog.value is CatalogState.Loaded || _catalog.value is CatalogState.Loading) return
        _catalog.value = CatalogState.Loading
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
        cache.cached(family)?.let { return it }
        _downloading.update { it + family }
        return try {
            cache.ensure(family)
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
            val api = GoogleFontsApi(client)
            val cacheRoot = File(app.filesDir, "google_fonts")
            return FontRepository(
                api = api,
                cache = FontCache(cacheRoot, api),
                preferences = FontPreferences(app),
                catalogFile = File(app.filesDir, "google_fonts_catalog.json"),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        }
    }
}
