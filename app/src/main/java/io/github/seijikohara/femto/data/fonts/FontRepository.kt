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
    fun cachedFontOrNull(family: String): CachedFont?

    suspend fun ensure(family: String): CachedFont?

    fun evictExcept(
        keep: Collection<String>,
        alsoProtect: Collection<String>,
    )
}

/** The slice of [FontPreferences] the repository consumes; a seam for JVM tests. */
internal interface FontSelectionStore {
    val selection: Flow<FontSelection>

    suspend fun setSource(
        slot: FontSlot,
        source: FontSource,
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
    // Defaulted (unlike the other collaborators) so the many existing tests
    // that do not care about installed fonts need no change; tests that do
    // pass a fake explicitly.
    private val systemFontSource: SystemFontSource = SystemFontSource { emptyList() },
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

    // Loaded once at construction, independent of the font picker ever opening —
    // a persisted SystemFont selection must resolve on a cold boot even if the
    // user never revisits the picker. Starts empty; [resolved] below recombines
    // once this populates, so a selection made before enumeration finishes
    // self-heals instead of sticking on the system-font fallback.
    private val _systemFonts = MutableStateFlow<List<SystemFontFamily>>(emptyList())

    /** Installed font families available to the picker, filtered per slot there. */
    val systemFonts: StateFlow<List<SystemFontFamily>> = _systemFonts.asStateFlow()

    // Flips exactly once when enumeration returns — even when it returns an
    // empty list, which the initial [_systemFonts] value cannot be told apart
    // from. Private with no public counterpart, hence no backing-underscore.
    private val systemFontsLoaded = MutableStateFlow(false)

    private val _resolvedOnce = MutableStateFlow(false)

    /**
     * True once a resolution pass has produced a trustworthy first value —
     * success, fallback, and failure alike. [resolved] starts on
     * [ResolvedFonts.System] and a legitimate SystemDefault selection resolves
     * to that same value, so a collector cannot tell "still resolving" apart
     * from "resolved to the system font" by watching [resolved]; the splash
     * keep-on-screen gate needs this explicit signal (see MainActivity).
     */
    val resolvedOnce: StateFlow<Boolean> = _resolvedOnce.asStateFlow()

    init {
        scope.launch {
            _systemFonts.value = systemFontSource.families()
            systemFontsLoaded.value = true
        }
    }

    /**
     * The resolved faces, recomputed whenever the selection or the installed-font
     * catalog changes (or a retry fires). Evicts the now-unused Google Fonts
     * cache first, then resolves each selected slot — so a switch frees the
     * previous download's bytes before fetching the new one. Families still
     * downloading are protected from eviction: a fast re-selection cancels the
     * previous mapLatest pass while its OkHttp call is still streaming, and
     * deleting that directory would corrupt the write. System-installed families
     * never enter the Google Fonts cache directory, so eviction never touches them.
     */
    val resolved: StateFlow<ResolvedFonts> =
        combine(
            preferences.selection,
            retryTrigger.onStart { emit(Unit) },
            _systemFonts,
            systemFontsLoaded,
        ) { selection, _, systemFonts, fontsLoaded ->
            Triple(selection, systemFonts, fontsLoaded)
        }.mapLatest { (selection, systemFonts, fontsLoaded) ->
            // A failed family that is no longer selected has no retry surface;
            // drop it so the picker does not flag a stale row.
            _downloadFailed.update { failed -> failed intersect selection.googleFamilies }
            cache.evictExcept(selection.googleFamilies, alsoProtect = _downloading.value)
            ResolvedFonts(
                latin = resolveSlot(selection.latin, systemFonts),
                cjk = resolveSlot(selection.cjk, systemFonts),
            ).also {
                // Settle only when this pass could resolve every selected slot
                // for real: a SystemFont slot matched before enumeration lands
                // falls back now and swaps a moment later — exactly the
                // post-splash reflow the signal exists to prevent.
                val systemFontPending =
                    !fontsLoaded &&
                        (selection.latin is FontSource.SystemFont || selection.cjk is FontSource.SystemFont)
                if (!systemFontPending) _resolvedOnce.value = true
            }
        }.stateIn(scope, SharingStarted.Eagerly, ResolvedFonts.System)

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    /** Persist a slot choice. [FontSource.SystemDefault] restores that slot to the system font. */
    fun choose(
        slot: FontSlot,
        source: FontSource,
    ) {
        scope.launch {
            val unchanged = selection.value.sourceFor(slot) == source
            preferences.setSource(slot, source)
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

    private suspend fun resolveSlot(
        source: FontSource,
        systemFonts: List<SystemFontFamily>,
    ): CachedFont? =
        when (source) {
            FontSource.SystemDefault -> null
            is FontSource.GoogleFonts -> resolveGoogleFont(source.family)
            is FontSource.SystemFont -> resolveSystemFont(source.familyName, systemFonts)
        }

    private suspend fun resolveGoogleFont(family: String): CachedFont? {
        cache.cachedFontOrNull(family)?.let { font ->
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

    // The family's files already live on disk (enumerated by
    // installedFontFamilies): no network, no cache write, no download-progress
    // state. A family that has since disappeared — uninstalled, or not yet
    // enumerated at cold boot (systemFonts still empty) — resolves to null,
    // which falls back to the system font exactly like an unresolvable Google
    // family; [resolved] recombines once enumeration lands, so this is a
    // transient state rather than a stuck one.
    //
    // Non-upright files are dropped with [isUprightFileName] BEFORE the
    // weight map is built: weightFromFileName has no italic token, so
    // "Roboto-Regular.ttf" and "Roboto-Italic.ttf" both guess weight 400, and
    // associateBy (last-write-wins) would let whichever one SystemFonts
    // enumerates last silently win the slot — rendering the whole family
    // slanted under the default FontStyle.Normal. A family with no upright
    // file at all (pathological: italic-only) has nothing left to serve and
    // falls back to null, same as a disappeared family, rather than serving
    // an italic file as if it were upright.
    private fun resolveSystemFont(
        familyName: String,
        systemFonts: List<SystemFontFamily>,
    ): CachedFont? =
        systemFonts
            .firstOrNull { it.familyName == familyName }
            ?.files
            ?.filter { file -> isUprightFileName(file.name) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { uprightFiles ->
                CachedFont.Static(uprightFiles.associateBy { file -> weightFromFileName(file.name) })
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
                preferences = FontPreferences(app),
                systemFontSource = SystemFontSource { installedFontFamilies() },
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
        override fun cachedFontOrNull(family: String): CachedFont? = this@asFaceStore.cachedFontOrNull(family)

        override suspend fun ensure(family: String): CachedFont? = this@asFaceStore.ensure(family)

        override fun evictExcept(
            keep: Collection<String>,
            alsoProtect: Collection<String>,
        ) = this@asFaceStore.evictExcept(keep, alsoProtect)
    }
