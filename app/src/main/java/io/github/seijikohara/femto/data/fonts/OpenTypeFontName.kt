package io.github.seijikohara.femto.data.fonts

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "OpenTypeFontName"

private const val TTC_TAG = 0x74746366L // 'ttcf' — a TrueType Collection header
private const val NAME_TABLE_TAG = 0x6e616d65L // 'name'
private const val NAME_ID_TYPOGRAPHIC_FAMILY = 16
private const val NAME_ID_FONT_FAMILY = 1
private const val PLATFORM_MACINTOSH = 1

/**
 * Best-effort family-name reader for a font file's OpenType 'name' table.
 * `android.graphics.fonts.SystemFonts` exposes files, not a family catalog, so
 * grouping installed fonts into families has to read the file itself: name ID
 * 16 (typographic family) wins when present, else ID 1 (font family).
 *
 * Handles a bare sfnt (`.ttf` / `.otf`) and the first font of a TrueType
 * Collection (`.ttc` — common for bundled CJK faces); returns null on
 * anything it cannot parse (truncated file, unexpected layout, I/O error) so
 * the caller falls back to a cleaned filename instead of crashing.
 */
internal object OpenTypeFontName {
    fun familyNameOrNull(file: File): String? =
        runCatching { RandomAccessFile(file, "r").use(::readFamilyName) }
            .onFailure { Log.w(TAG, "name-table read failed for ${file.name}", it) }
            .getOrNull()

    private fun readFamilyName(raf: RandomAccessFile): String? {
        val sfntOffset = if (raf.readUnsignedInt(0) == TTC_TAG) raf.readUnsignedInt(12) else 0L
        val numTables = raf.readUnsignedShort(sfntOffset + 4)
        val recordsStart = sfntOffset + 12
        val nameTableOffset =
            (0 until numTables)
                .asSequence()
                .map { index -> recordsStart + index * TABLE_RECORD_SIZE }
                .firstOrNull { recordOffset -> raf.readUnsignedInt(recordOffset) == NAME_TABLE_TAG }
                ?.let { recordOffset -> raf.readUnsignedInt(recordOffset + TABLE_RECORD_OFFSET_FIELD) }
                ?: return null
        return readBestFamilyName(raf, nameTableOffset)
    }

    private fun readBestFamilyName(
        raf: RandomAccessFile,
        nameTableOffset: Long,
    ): String? {
        val count = raf.readUnsignedShort(nameTableOffset + 2)
        val storageOffset = raf.readUnsignedShort(nameTableOffset + 4)
        val records =
            (0 until count).map { index ->
                parseNameRecord(
                    raf,
                    nameTableOffset + NAME_HEADER_SIZE + index * NAME_RECORD_SIZE,
                )
            }
        val chosen =
            records.firstOrNull { it.nameId == NAME_ID_TYPOGRAPHIC_FAMILY }
                ?: records.firstOrNull { it.nameId == NAME_ID_FONT_FAMILY }
                ?: return null
        return decodeName(raf, nameTableOffset + storageOffset, chosen)
    }

    private fun parseNameRecord(
        raf: RandomAccessFile,
        recordOffset: Long,
    ): NameRecordMeta =
        NameRecordMeta(
            platformId = raf.readUnsignedShort(recordOffset),
            nameId = raf.readUnsignedShort(recordOffset + 6),
            length = raf.readUnsignedShort(recordOffset + 8),
            stringOffset = raf.readUnsignedShort(recordOffset + 10),
        )

    private fun decodeName(
        raf: RandomAccessFile,
        storageBase: Long,
        record: NameRecordMeta,
    ): String? {
        if (record.length <= 0) return null
        val bytes = ByteArray(record.length)
        raf.seek(storageBase + record.stringOffset)
        raf.readFully(bytes)
        // Platform 1 (Macintosh) records are single-byte (effectively ASCII for a
        // font family name); platforms 0 (Unicode) and 3 (Windows) are UTF-16BE.
        val charset = if (record.platformId == PLATFORM_MACINTOSH) Charsets.ISO_8859_1 else Charsets.UTF_16BE
        return String(bytes, charset).trim().takeIf { it.isNotEmpty() }
    }

    private data class NameRecordMeta(
        val platformId: Int,
        val nameId: Int,
        val length: Int,
        val stringOffset: Int,
    )
}

// sfnt / 'name' table layout constants (OpenType spec): a TableRecord is
// tag(4) + checksum(4) + offset(4) + length(4) bytes, with the file offset to
// the table's own data at byte 8; a NameRecord is platformID(2) +
// encodingID(2) + languageID(2) + nameID(2) + length(2) + offset(2) bytes; the
// naming-table header (format + count + stringOffset) is 6 bytes before the
// first NameRecord.
private const val TABLE_RECORD_SIZE = 16L
private const val TABLE_RECORD_OFFSET_FIELD = 8L
private const val NAME_HEADER_SIZE = 6L
private const val NAME_RECORD_SIZE = 12L

private fun RandomAccessFile.readUnsignedInt(offset: Long): Long {
    seek(offset)
    return (read().toLong() shl 24) or (read().toLong() shl 16) or (read().toLong() shl 8) or read().toLong()
}

private fun RandomAccessFile.readUnsignedShort(offset: Long): Int {
    seek(offset)
    return (read() shl 8) or read()
}
