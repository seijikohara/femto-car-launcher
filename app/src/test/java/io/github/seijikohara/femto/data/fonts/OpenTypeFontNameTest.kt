package io.github.seijikohara.femto.data.fonts

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [OpenTypeFontName] reads real font files; these tests build the smallest
 * valid sfnt (and TrueType Collection) byte layouts that exercise its parsing
 * rather than shipping binary fixtures.
 */
class OpenTypeFontNameTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `prefers the typographic family (name ID 16) over the legacy family (name ID 1)`() {
        val file =
            fontFile(
                sfnt(
                    NameEntry(nameId = 1, platformId = WINDOWS, value = "Legacy Family"),
                    NameEntry(nameId = 16, platformId = WINDOWS, value = "Typographic Family"),
                ),
            )

        assertEquals("Typographic Family", OpenTypeFontName.familyNameOrNull(file))
    }

    @Test
    fun `falls back to name ID 1 when ID 16 is absent`() {
        val file = fontFile(sfnt(NameEntry(nameId = 1, platformId = WINDOWS, value = "Only Family")))

        assertEquals("Only Family", OpenTypeFontName.familyNameOrNull(file))
    }

    @Test
    fun `decodes a Macintosh-platform single-byte record`() {
        val file = fontFile(sfnt(NameEntry(nameId = 1, platformId = MACINTOSH, value = "Mac Family")))

        assertEquals("Mac Family", OpenTypeFontName.familyNameOrNull(file))
    }

    @Test
    fun `reads the first font of a TrueType Collection`() {
        val file =
            fontFile(
                ttc(
                    listOf(NameEntry(nameId = 16, platformId = WINDOWS, value = "First Font")),
                    listOf(NameEntry(nameId = 16, platformId = WINDOWS, value = "Second Font")),
                ),
            )

        assertEquals("First Font", OpenTypeFontName.familyNameOrNull(file))
    }

    @Test
    fun `returns null for a malformed file instead of throwing`() {
        val file = fontFile(byteArrayOf(1, 2, 3, 4, 5))

        assertNull(OpenTypeFontName.familyNameOrNull(file))
    }

    @Test
    fun `returns null for a missing file instead of throwing`() {
        assertNull(OpenTypeFontName.familyNameOrNull(File(tempFolder.root, "does-not-exist.ttf")))
    }

    private fun fontFile(bytes: ByteArray): File =
        File(tempFolder.root, "font-${fileCounter++}.ttf").apply {
            writeBytes(bytes)
        }

    private var fileCounter = 0
}

private const val WINDOWS = 3
private const val MACINTOSH = 1

private data class NameEntry(
    val nameId: Int,
    val platformId: Int,
    val value: String,
)

// sfnt / 'name' table layout constants, mirroring OpenTypeFontName's own
// (private) copies — see that file for the OpenType-spec field offsets.
private const val SFNT_HEADER_SIZE = 12
private const val TABLE_RECORD_SIZE = 16
private const val NAME_HEADER_SIZE = 6
private const val NAME_RECORD_SIZE = 12

/**
 * Build the smallest valid sfnt: a 12-byte header, one TableRecord for
 * 'name', and the 'name' table itself. [base] is the absolute file offset
 * this blob will be concatenated at (0 for a standalone file, or a TTC
 * member's offset) — OpenType TableRecord offsets are always absolute from
 * the start of the *file*, not from the start of the sfnt they belong to, so
 * a TTC member must bake [base] into the offset it declares even though the
 * bytes are written at a purely local (0-based) position here.
 */
private fun sfnt(
    vararg entries: NameEntry,
    base: Int = 0,
): ByteArray {
    val stringBytes =
        entries.map { entry ->
            entry.value.toByteArray(
                if (entry.platformId ==
                    MACINTOSH
                ) {
                    Charsets.US_ASCII
                } else {
                    Charsets.UTF_16BE
                },
            )
        }
    val storageStart = NAME_HEADER_SIZE + entries.size * NAME_RECORD_SIZE
    val nameTableSize = storageStart + stringBytes.sumOf { it.size }
    val localNameTableOffset = SFNT_HEADER_SIZE + TABLE_RECORD_SIZE
    val totalSize = localNameTableOffset + nameTableSize

    val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
    buffer.putInt(0x00010000) // sfntVersion
    buffer.putShort(1) // numTables
    buffer.putShort(0) // searchRange (unused by the reader)
    buffer.putShort(0) // entrySelector
    buffer.putShort(0) // rangeShift
    buffer.put("name".toByteArray(Charsets.US_ASCII))
    buffer.putInt(0) // checksum (unused by the reader)
    buffer.putInt(base + localNameTableOffset) // offset — absolute from the file start
    buffer.putInt(nameTableSize)

    buffer.putShort(0) // format
    buffer.putShort(entries.size.toShort())
    buffer.putShort(storageStart.toShort())
    val offsets = stringBytes.runningFold(0) { acc, bytes -> acc + bytes.size }
    entries.forEachIndexed { index, entry ->
        buffer.putShort(entry.platformId.toShort())
        buffer.putShort(0) // encodingId (unused by the reader)
        buffer.putShort(0) // languageId
        buffer.putShort(entry.nameId.toShort())
        buffer.putShort(stringBytes[index].size.toShort())
        buffer.putShort(offsets[index].toShort())
    }
    stringBytes.forEach(buffer::put)
    return buffer.array()
}

/** Wrap two sfnts (built with the correct per-member [sfnt] base) in a minimal 2-font TrueType Collection header. */
private fun ttc(
    entriesA: List<NameEntry>,
    entriesB: List<NameEntry>,
): ByteArray {
    val headerSize = 4 + 2 + 2 + 4 + 2 * 4 // tag + majorVersion + minorVersion + numFonts + 2 offsets
    val fontA = sfnt(*entriesA.toTypedArray(), base = headerSize)
    val offsetB = headerSize + fontA.size
    val fontB = sfnt(*entriesB.toTypedArray(), base = offsetB)

    val buffer = ByteBuffer.allocate(headerSize + fontA.size + fontB.size).order(ByteOrder.BIG_ENDIAN)
    buffer.put("ttcf".toByteArray(Charsets.US_ASCII))
    buffer.putShort(1) // majorVersion
    buffer.putShort(0) // minorVersion
    buffer.putInt(2) // numFonts
    buffer.putInt(headerSize) // offset to font A's table directory
    buffer.putInt(offsetB) // offset to font B's table directory
    buffer.put(fontA)
    buffer.put(fontB)
    return buffer.array()
}
