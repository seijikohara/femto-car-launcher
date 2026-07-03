package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BYTES_PER_MB = 1024L * 1024L

// Below this much free data-partition space the app's own writes (DataStore,
// font cache, WebView caches) start failing silently.
private const val LOW_DATA_FREE_MB = 200L

/** Collects the STORAGE diagnostics section. */
internal class StorageFactsCollector(
    private val context: Context,
) {
    suspend fun storageFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            SectionPayload.Facts(
                listOf(
                    dataFact(),
                    cacheFact(),
                    allocatableFact(),
                ),
            )
        }

    private fun dataFact(): DiagnosticFact {
        val stat = StatFs(context.filesDir.path)
        val availMb = stat.availableBytes / BYTES_PER_MB
        val totalMb = stat.totalBytes / BYTES_PER_MB
        val value = "$availMb / $totalMb MB free"
        return DiagnosticFact(
            "Data",
            if (availMb < LOW_DATA_FREE_MB) FactValue.Status(value, FactHealth.WARNING) else FactValue.Text(value),
        )
    }

    private fun cacheFact(): DiagnosticFact {
        val usedMb =
            context.cacheDir
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() } / BYTES_PER_MB
        val availMb = StatFs(context.cacheDir.path).availableBytes / BYTES_PER_MB
        return DiagnosticFact("Cache", FactValue.Text("$usedMb MB used, $availMb MB free"))
    }

    // getAllocatableBytes counts space the platform can free on demand
    // (cached apps' data), so it can exceed the raw free space above.
    private fun allocatableFact(): DiagnosticFact =
        DiagnosticFact(
            "Allocatable",
            FactValue.Text(
                runCatching {
                    val storageManager = context.getSystemService<StorageManager>()!!
                    "${storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT) / BYTES_PER_MB} MB"
                }.getOrDefault("unknown"),
            ),
        )
}
