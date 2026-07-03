package io.github.seijikohara.femto.data.diagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.LocaleList
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.view.InputDevice
import android.view.View
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.location.hasFineLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private const val NANOS_PER_MINUTE = 60_000_000_000L

/**
 * Collects the LOCALE_TIME, LOCATION, and INPUT diagnostics sections — three
 * small "how is the runtime environment configured" fact sets grouped in one
 * file rather than three near-empty ones.
 */
internal class EnvironmentFactsCollector(
    private val context: Context,
) {
    suspend fun localeTimeFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val timeZone = TimeZone.getDefault()
            SectionPayload.Facts(
                buildList {
                    add(DiagnosticFact("Locales", FactValue.Text(LocaleList.getDefault().toLanguageTags())))
                    add(
                        DiagnosticFact(
                            "24-hour clock",
                            FactValue.Text(if (DateFormat.is24HourFormat(context)) "yes" else "no"),
                        ),
                    )
                    add(DiagnosticFact("Timezone", FactValue.Text("${timeZone.id} (UTC${timeZone.offsetLabel()})")))
                    add(
                        DiagnosticFact(
                            "Layout direction",
                            FactValue.Text(
                                if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                                    "rtl"
                                } else {
                                    "ltr"
                                },
                            ),
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "First day of week",
                            FactValue.Text(Calendar.getInstance().firstDayOfWeek.dayOfWeekName()),
                        ),
                    )
                    add(autoTimeFact())
                },
            )
        }

    suspend fun locationFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val locationManager = context.getSystemService<LocationManager>()!!
            SectionPayload.Facts(
                buildList {
                    add(locationEnabledFact(locationManager))
                    add(providersFact(locationManager))
                    add(lastGpsFixFact(locationManager))
                    add(gnssHardwareFact(locationManager))
                    add(motionSensorsFact(context.getSystemService<SensorManager>()!!))
                },
            )
        }

    suspend fun inputFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            SectionPayload.Facts(
                buildList {
                    add(multitouchFact(context.packageManager))
                    add(inputDevicesFact())
                },
            )
        }

    private fun TimeZone.offsetLabel(): String {
        val totalMinutes = getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (totalMinutes < 0) "-" else "+"
        val absMinutes = abs(totalMinutes)
        return "%s%02d:%02d".format(Locale.ROOT, sign, absMinutes / 60, absMinutes % 60)
    }

    private fun Int.dayOfWeekName(): String =
        when (this) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "unknown"
        }

    // Either auto-time or auto-timezone off means the head unit's clock (and
    // hence the dashboard clock/calendar) can silently drift — worth a
    // WARNING even though neither setting alone breaks the app.
    private fun autoTimeFact(): DiagnosticFact {
        val autoTime = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) != 0
        val autoTimeZone = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) != 0
        return DiagnosticFact(
            "Auto time / timezone",
            FactValue.Status(
                "$autoTime / $autoTimeZone",
                if (!autoTime || !autoTimeZone) FactHealth.WARNING else FactHealth.OK,
            ),
        )
    }

    private fun locationEnabledFact(locationManager: LocationManager): DiagnosticFact {
        val enabled = locationManager.isLocationEnabled
        return DiagnosticFact(
            "Location enabled",
            FactValue.Status(if (enabled) "enabled" else "disabled", if (enabled) FactHealth.OK else FactHealth.ERROR),
        )
    }

    private fun providersFact(locationManager: LocationManager): DiagnosticFact {
        val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network =
            locationManager.hasProvider(LocationManager.NETWORK_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return DiagnosticFact("Providers", FactValue.Text("gps=$gps, network=$network"))
    }

    // Privacy floor: only the fix's age and accuracy render, never
    // latitude/longitude — a diagnostics report is meant to be pasted into a
    // public issue tracker.
    @SuppressLint("MissingPermission") // Permission is checked via hasFineLocationPermission().
    private fun lastGpsFixFact(locationManager: LocationManager): DiagnosticFact {
        if (!context.hasFineLocationPermission()) {
            return DiagnosticFact("Last GPS fix", FactValue.Text("ACCESS_FINE_LOCATION denied"))
        }
        // getLastKnownLocation throws on some firmware when the GPS provider
        // is absent rather than returning null; the section degrades to
        // "none" either way.
        val location = runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        val value =
            location?.let {
                val ageMinutes = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / NANOS_PER_MINUTE
                "$ageMinutes min ago (±${it.accuracy} m)"
            } ?: "none"
        return DiagnosticFact("Last GPS fix", FactValue.Text(value))
    }

    private fun gnssHardwareFact(locationManager: LocationManager): DiagnosticFact {
        val modelName = locationManager.gnssHardwareModelName ?: "unknown"
        val year = locationManager.gnssYearOfHardware.takeIf { it > 0 }?.toString() ?: "unknown"
        return DiagnosticFact("GNSS hardware", FactValue.Text("$modelName (year $year)"))
    }

    private fun motionSensorsFact(sensorManager: SensorManager): DiagnosticFact {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        val total = sensorManager.getSensorList(Sensor.TYPE_ALL).size
        return DiagnosticFact(
            "Motion sensors",
            FactValue.Text("accelerometer=$accelerometer, gyro=$gyro, magnetometer=$magnetometer ($total total)"),
        )
    }

    // The feature ladder from richest to poorest touch support; the fallback
    // is a WARNING because it validates the zoom +/- mandate elsewhere in the
    // app (CLAUDE.md map-controls memory) — pinch-zoom is impossible without
    // at least basic multitouch.
    private fun multitouchFact(packageManager: PackageManager): DiagnosticFact {
        val (value, health) =
            when {
                packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> {
                    "full (5+ points)" to FactHealth.OK
                }

                packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> {
                    "distinct (2+ points)" to FactHealth.OK
                }

                packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> {
                    "basic (2 points)" to FactHealth.OK
                }

                else -> {
                    "single-point" to FactHealth.WARNING
                }
            }
        return DiagnosticFact("Multitouch", FactValue.Status(value, health))
    }

    private fun inputDevicesFact(): DiagnosticFact {
        val label =
            InputDevice
                .getDeviceIds()
                .toList()
                .mapNotNull { InputDevice.getDevice(it) }
                .filterNot { it.isVirtual }
                .joinToString("; ") { device -> "${device.name} [${device.sourceLabels()}]" }
        return DiagnosticFact("Input devices", FactValue.Text(label))
    }

    private fun InputDevice.sourceLabels(): String =
        listOfNotNull(
            "touch".takeIf { supportsSource(InputDevice.SOURCE_TOUCHSCREEN) },
            "keyboard".takeIf { supportsSource(InputDevice.SOURCE_KEYBOARD) },
            "dpad".takeIf { supportsSource(InputDevice.SOURCE_DPAD) },
            "rotary".takeIf { supportsSource(InputDevice.SOURCE_ROTARY_ENCODER) },
        ).joinToString()
}
