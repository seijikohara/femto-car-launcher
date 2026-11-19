package io.github.seijikohara.femto.data.map.offline

import android.database.sqlite.SQLiteDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RegionFileCheckTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun database(createTable: String): File =
        File(folder.root, "region.mbtiles").also { file ->
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db -> db.execSQL(createTable) }
        }

    @Test
    fun `a database with a tiles table passes`() {
        val file = database("CREATE TABLE tiles (zoom_level INTEGER, tile_data BLOB)")
        assertTrue(SqliteRegionFileCheck.isUsable(file, file.length()))
    }

    @Test
    fun `the same file with a wrong expected size fails`() {
        val file = database("CREATE TABLE tiles (zoom_level INTEGER)")
        assertFalse(SqliteRegionFileCheck.isUsable(file, file.length() + 1))
    }

    @Test
    fun `a file of the right size filled with zeros fails`() {
        val file = folder.newFile().apply { writeBytes(ByteArray(8192)) }
        assertFalse(SqliteRegionFileCheck.isUsable(file, 8192L))
    }

    @Test
    fun `a database without a tiles table fails`() {
        val file = database("CREATE TABLE other (x INTEGER)")
        assertFalse(SqliteRegionFileCheck.isUsable(file, file.length()))
    }

    @Test
    fun `a missing file fails`() {
        assertFalse(SqliteRegionFileCheck.isUsable(File(folder.root, "missing.mbtiles"), 10L))
    }
}
