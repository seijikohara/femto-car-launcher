package io.github.seijikohara.femto.data.map.offline

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Decides whether a downloaded file is a map database worth keeping. */
internal fun interface RegionFileCheck {
    fun isUsable(
        file: File,
        expectedBytes: Long,
    ): Boolean
}

private val SqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** Length, the SQLite header, and a read-only open that finds a `tiles` table. */
internal val SqliteRegionFileCheck: RegionFileCheck =
    RegionFileCheck { file, expectedBytes ->
        runCatching {
            file.isFile && file.length() == expectedBytes && hasSqliteHeader(file) && hasTilesTable(file)
        }.getOrDefault(false)
    }

private fun hasSqliteHeader(file: File): Boolean =
    file.inputStream().use { it.readNBytes(SqliteHeader.size) }.contentEquals(SqliteHeader)

private fun hasTilesTable(file: File): Boolean =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db
            .rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'tiles'",
                null,
            ).use { it.moveToFirst() }
    }
