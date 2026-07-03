package io.github.seijikohara.femto.data.diagnostics

import android.content.pm.PackageInfo
import org.junit.Test
import kotlin.test.assertEquals

class PermissionFactsTest {
    @Test
    fun `permissionRowsFrom decodes the granted flag per index`() {
        val rows =
            permissionRowsFrom(
                requested = arrayOf("android.permission.INTERNET", "android.permission.READ_CALENDAR"),
                flags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED, 0),
                dangerous = emptySet(),
            )

        assertEquals(
            listOf(
                PermissionRow("INTERNET", granted = true, dangerous = false),
                PermissionRow("READ_CALENDAR", granted = false, dangerous = false),
            ),
            rows,
        )
    }

    @Test
    fun `permissionRowsFrom sorts dangerous permissions before normal ones, alphabetically within each group`() {
        val rows =
            permissionRowsFrom(
                requested =
                    arrayOf(
                        "android.permission.INTERNET",
                        "android.permission.RECORD_AUDIO",
                        "android.permission.ACCESS_FINE_LOCATION",
                    ),
                flags =
                    intArrayOf(
                        PackageInfo.REQUESTED_PERMISSION_GRANTED,
                        PackageInfo.REQUESTED_PERMISSION_GRANTED,
                        0,
                    ),
                dangerous = setOf("android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION"),
            )

        assertEquals(listOf("ACCESS_FINE_LOCATION", "RECORD_AUDIO", "INTERNET"), rows.map { it.name })
    }

    @Test
    fun `permissionRowsFrom marks a permission dangerous only when present in the dangerous set`() {
        val rows =
            permissionRowsFrom(
                requested = arrayOf("android.permission.CAMERA"),
                flags = intArrayOf(0),
                dangerous = setOf("android.permission.CAMERA"),
            )

        assertEquals(PermissionRow("CAMERA", granted = false, dangerous = true), rows.single())
    }

    @Test
    fun `permissionRowsFrom treats a missing flags entry as ungranted`() {
        val rows =
            permissionRowsFrom(
                requested = arrayOf("android.permission.CAMERA"),
                flags = intArrayOf(),
                dangerous = emptySet(),
            )

        assertEquals(PermissionRow("CAMERA", granted = false, dangerous = false), rows.single())
    }

    @Test
    fun `permissionRowsFrom returns an empty list when the package requests no permissions`() {
        val rows = permissionRowsFrom(requested = null, flags = null, dangerous = emptySet())

        assertEquals(emptyList(), rows)
    }
}
