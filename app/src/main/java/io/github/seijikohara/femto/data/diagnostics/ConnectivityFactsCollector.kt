package io.github.seijikohara.femto.data.diagnostics

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.provider.Settings
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.location.hasBluetoothConnectPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Reproduces the v1 wording verbatim (data/system/DiagnosticsRepository.kt +
// ui/diagnostics/DiagnosticsReport.kt, both retired by this registry) so the
// "Online (Wi-Fi, Cellular)" / "OFFLINE" tokens stay grep-stable across the
// rewrite.
private fun NetworkCapabilities.transportLabels(): List<String> =
    listOfNotNull(
        "Wi-Fi".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
        "Cellular".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
        "Ethernet".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
        "Bluetooth".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
    )

/** Collects the NETWORK diagnostics section. */
internal class ConnectivityFactsCollector(
    private val context: Context,
) {
    suspend fun networkFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            val linkProperties = activeNetwork?.let(connectivityManager::getLinkProperties)

            SectionPayload.Facts(
                buildList {
                    add(onlineFact(capabilities))
                    capabilities?.let { validated ->
                        add(meteredFact(validated))
                        add(captivePortalFact(validated))
                        add(vpnFact(validated))
                        add(bandwidthFact(validated))
                        wifiSignalFact(validated)?.let(::add)
                    }
                    linkProperties?.let { add(dnsFact(it)) }
                    add(airplaneModeFact())
                    add(dataSaverFact(connectivityManager))
                    add(bluetoothFact())
                },
            )
        }

    // v1 token: DiagnosticsRepository.networkOnline used
    // NET_CAPABILITY_VALIDATED (actual internet reachability), not just an
    // active network handle — a captive portal or a dead Wi-Fi AP still
    // reports an active network with no validated route.
    private fun onlineFact(capabilities: NetworkCapabilities?): DiagnosticFact {
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return if (online) {
            val transports = capabilities.transportLabels().joinToString().ifEmpty { "unknown transport" }
            DiagnosticFact("Online", FactValue.Status("Online ($transports)", FactHealth.OK))
        } else {
            DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR))
        }
    }

    // A metered connection is a budget for the tile/weather fetchers, not a
    // failure — flagged WARNING only to surface it in the issues feed, never
    // ERROR.
    private fun meteredFact(capabilities: NetworkCapabilities): DiagnosticFact {
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return DiagnosticFact(
            "Metered",
            FactValue.Status(if (metered) "yes" else "no", if (metered) FactHealth.WARNING else FactHealth.OK),
        )
    }

    private fun captivePortalFact(capabilities: NetworkCapabilities): DiagnosticFact {
        val detected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        return DiagnosticFact(
            "Captive portal",
            FactValue.Status(
                if (detected) "detected" else "none",
                if (detected) FactHealth.WARNING else FactHealth.OK,
            ),
        )
    }

    private fun vpnFact(capabilities: NetworkCapabilities): DiagnosticFact {
        val active = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        return DiagnosticFact("VPN", FactValue.Status(if (active) "active" else "none", FactHealth.INFO))
    }

    private fun bandwidthFact(capabilities: NetworkCapabilities): DiagnosticFact =
        DiagnosticFact(
            "Bandwidth",
            FactValue.Text(
                "down ${capabilities.linkDownstreamBandwidthKbps} kbps / " +
                    "up ${capabilities.linkUpstreamBandwidthKbps} kbps",
            ),
        )

    // Absent (no active Wi-Fi transport) means the fact is skipped entirely,
    // not rendered as an empty/placeholder row.
    private fun wifiSignalFact(capabilities: NetworkCapabilities): DiagnosticFact? =
        (capabilities.transportInfo as? WifiInfo)?.let { wifiInfo ->
            DiagnosticFact("Wi-Fi signal", FactValue.Text("${wifiInfo.rssi} dBm, ${wifiInfo.linkSpeed} Mbps"))
        }

    // Privacy floor: server addresses never render, only the private-DNS
    // flag and a count — enough to diagnose "DNS is broken" without leaking
    // the user's resolver configuration into a pasted report.
    private fun dnsFact(linkProperties: LinkProperties): DiagnosticFact =
        DiagnosticFact(
            "Private DNS / DNS servers",
            FactValue.Text("${linkProperties.isPrivateDnsActive}, ${linkProperties.dnsServers.size} servers"),
        )

    private fun airplaneModeFact(): DiagnosticFact {
        val on = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        return DiagnosticFact(
            "Airplane mode",
            FactValue.Status(if (on) "on" else "off", if (on) FactHealth.WARNING else FactHealth.OK),
        )
    }

    private fun dataSaverFact(connectivityManager: ConnectivityManager): DiagnosticFact {
        val status = connectivityManager.restrictBackgroundStatus
        val enabled = status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val value =
            when (status) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                else -> "disabled"
            }
        return DiagnosticFact("Data saver", FactValue.Status(value, if (enabled) FactHealth.WARNING else FactHealth.OK))
    }

    private fun bluetoothFact(): DiagnosticFact {
        if (!context.hasBluetoothConnectPermission()) {
            return DiagnosticFact("Bluetooth", FactValue.Text("BLUETOOTH_CONNECT denied"))
        }
        val adapter = context.getSystemService<BluetoothManager>()?.adapter
        val enabled = adapter?.isEnabled == true
        val a2dpState = adapter?.getProfileConnectionState(BluetoothProfile.A2DP)
        return DiagnosticFact(
            "Bluetooth",
            FactValue.Text("${if (enabled) "enabled" else "disabled"}, A2DP ${a2dpState.profileStateName()}"),
        )
    }

    private fun Int?.profileStateName(): String =
        when (this) {
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            else -> "UNKNOWN"
        }
}
