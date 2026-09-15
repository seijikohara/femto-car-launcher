package io.github.seijikohara.femto.ui.licenses

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import io.github.seijikohara.femto.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "LicensesViewModel"

/**
 * Owns the open-source-licenses screen state: the credited components loaded once
 * on creation, plus which license body is open. [loadLibraries] is a plain
 * suspend seam so JVM tests drive the load and failure paths without Android or
 * AboutLibraries types.
 */
internal class LicensesViewModel(
    private val loadLibraries: suspend () -> List<LicenseItem>,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LicensesUiState.Initial)
    val uiState: StateFlow<LicensesUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded =
                try {
                    loadLibraries()
                } catch (e: CancellationException) {
                    // runCatching would also trap cancellation; rethrow to keep
                    // structured concurrency intact (DiagnosticsViewModel precedent).
                    throw e
                } catch (e: Exception) {
                    // Informational screen: a broken load degrades to the empty
                    // "unavailable" state rather than crashing Settings.
                    Log.e(TAG, "loading open-source licenses failed", e)
                    emptyList()
                }
            mutableUiState.update { it.copy(isLoading = false, libraries = loaded) }
        }
    }

    fun onAction(action: LicensesAction) =
        when (action) {
            is LicensesAction.Select -> mutableUiState.update { it.copy(selected = action.item) }
            LicensesAction.ClearSelection -> mutableUiState.update { it.copy(selected = null) }
        }
}

/** Wires the AboutLibraries-generated metadata without an UNCHECKED_CAST factory. */
internal val LicensesViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            LicensesViewModel(loadLibraries = { loadAboutLibraries(application) })
        }
    }

// AboutLibraries parses the bundled res/raw/aboutlibraries.json (Gradle deps plus
// the app/config manual entries) with no network. Off the main thread: the parse
// touches disk and JSON on screen open.
private suspend fun loadAboutLibraries(context: Context): List<LicenseItem> =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        Libs
            .Builder()
            .withContext(context)
            .build()
            .libraries
            .map { library -> library.toLicenseItem() } + listOfNotNull(webMapNoticesOrNull(context))
    }

// The map page's transitive npm notices, generated at build time by
// webmap/scripts/third-party-notices.mjs into the web assets: the MapLibre
// distribution inlines its dependencies without their notices, so no manual
// AboutLibraries entry could cover them. A missing file only means an older
// build — the screen degrades to the collected list rather than failing.
private fun webMapNoticesOrNull(context: Context): LicenseItem? =
    runCatching {
        context.assets
            .open(WEBMAP_NOTICES_ASSET)
            .bufferedReader()
            .use { it.readText() }
    }.onFailure { Log.w(TAG, "web map third-party notices unavailable", it) }
        .getOrNull()
        ?.let { notices ->
            LicenseItem(
                id = WEBMAP_NOTICES_ID,
                name = context.getString(R.string.licenses_webmap_notices),
                licenseName = null,
                licenseText = notices,
                url = null,
            )
        }

private const val WEBMAP_NOTICES_ASSET = "web/THIRD-PARTY-NOTICES.txt"
private const val WEBMAP_NOTICES_ID = "femto.webmap.third-party-notices"

private fun Library.toLicenseItem(): LicenseItem {
    val license = licenses.firstOrNull()
    return LicenseItem(
        id = uniqueId,
        name = name,
        licenseName = license?.spdxId ?: license?.name,
        licenseText = license?.licenseContent,
        url = website ?: scm?.url,
        licenseUrl = license?.url,
    )
}
