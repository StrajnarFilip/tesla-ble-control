package io.github.teslablecontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkFailure
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.protocol.transport.TeslaBle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Finds cars by their advertised name, which is derived from the VIN. */
@SuppressLint("MissingPermission") // Callers check BlePermissions first.
class VehicleScanner(private val context: Context, private val log: DiagnosticsLog) {

    data class Sighting(val device: BluetoothDevice, val name: String, val rssi: Int, val connectable: Boolean)

    private fun scanner() = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?.takeIf { it.isEnabled }?.bluetoothLeScanner
        ?: throw LinkException(LinkFailure.BLUETOOTH_OFF, "Bluetooth is off")

    private fun checkReady() {
        if (!BlePermissions.canScan(context)) throw LinkException(LinkFailure.PERMISSION_MISSING, "Bluetooth permission missing")
        if (!BlePermissions.isLocationOn(context)) throw LinkException(LinkFailure.LOCATION_OFF, "Location is off")
    }

    /**
     * Car advertisements as they arrive. With [name] set, the controller filters by name (works with
     * the screen off and saves power); without it, every vehicle-shaped name is reported.
     */
    fun sightings(name: String? = null): Flow<Sighting> = callbackFlow {
        checkReady()
        val scanner = scanner()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = result.scanRecord?.deviceName ?: result.device.name
                if (advertised != null && (if (name != null) advertised == name else TeslaBle.isVehicleLocalName(advertised))) {
                    trySend(Sighting(result.device, advertised, result.rssi, result.isConnectable))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth scan failed (code $errorCode)"))
            }
        }
        val filters = if (name != null) listOf(ScanFilter.Builder().setDeviceName(name).build()) else emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /**
     * Looks for the car with [vin]. Some Bluetooth chips only match names in the main advertisement,
     * so a filtered scan is followed by an unfiltered one if the first finds nothing.
     */
    suspend fun find(vin: String, timeout: Duration): Sighting? {
        val name = TeslaBle.localName(vin)
        log.info("Scanning for $name")
        val found = withTimeoutOrNull(timeout / 2) { sightings(name).first() }
            ?: withTimeoutOrNull(timeout / 2) { sightings(null).first { it.name == name } }
        log.info(if (found != null) "Found car at ${found.rssi} dBm" else "Car not found in scan")
        return found
    }
}
