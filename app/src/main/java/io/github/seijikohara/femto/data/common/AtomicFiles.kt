package io.github.seijikohara.femto.data.common

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** The suffix of the temporary file [writeAtomically] writes beside its target; a sweep may delete any it finds. */
internal const val ATOMIC_WRITE_TEMP_SUFFIX = ".tmp"

/**
 * Replace [target] with [bytes] whole or not at all, and report whether it was replaced.
 *
 * A temp file plus a rename makes the target whole or absent, never half-written. The sync before the rename keeps a
 * power cut (how a head unit stops) from leaving a renamed but empty file, and a temp file that a failed write leaves
 * goes at once.
 */
internal fun writeAtomically(
    target: File,
    bytes: ByteArray,
): Boolean {
    val temp = File(target.parentFile, target.name + ATOMIC_WRITE_TEMP_SUFFIX)
    return runCatching {
        target.parentFile?.mkdirs()
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        temp.renameTo(target)
    }.getOrDefault(false)
        .also { written -> if (!written) temp.delete() }
}

/**
 * Flush the file's bytes to the disk without truncating it; false for a missing file or a failed sync.
 *
 * A rename can reach the disk before the bytes it names, so a downloaded file is synced before it is renamed into
 * place.
 */
internal fun File.syncToDisk(): Boolean =
    isFile && runCatching { RandomAccessFile(this, "rw").use { it.fd.sync() } }.isSuccess
