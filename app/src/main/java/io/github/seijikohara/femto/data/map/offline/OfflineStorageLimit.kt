package io.github.seijikohara.femto.data.map.offline

/** The most the saved regions may occupy together; decimal units, like `Formatter.formatShortFileSize`. */
internal enum class OfflineStorageLimit(
    val bytes: Long?,
) {
    NONE(null),
    GB_1(1_000_000_000L),
    GB_2(2_000_000_000L),
    GB_5(5_000_000_000L),
    GB_10(10_000_000_000L),
    GB_20(20_000_000_000L),
    GB_50(50_000_000_000L),
}

internal sealed interface StorageDecision {
    data object Allowed : StorageDecision

    data class OverLimit(
        val overByBytes: Long,
        val limitBytes: Long,
    ) : StorageDecision
}

/**
 * Decide whether a file of [incomingBytes] fits under [limitBytes] (null: no limit).
 *
 * [keptBytes] is what the saved regions occupy, [reservedBytes] the full size of every other download that has
 * started, and [replacedBytes] the file an update replaces (0 for a new region). An update that does not grow the
 * total always goes through, so lowering the limit never blocks a refresh of a region that is already saved.
 */
internal fun storageDecision(
    limitBytes: Long?,
    keptBytes: Long,
    reservedBytes: Long,
    replacedBytes: Long,
    incomingBytes: Long,
): StorageDecision {
    val total = keptBytes + reservedBytes - replacedBytes + incomingBytes
    return when {
        limitBytes == null || total <= limitBytes || incomingBytes <= replacedBytes -> StorageDecision.Allowed
        else -> StorageDecision.OverLimit(total - limitBytes, limitBytes)
    }
}
