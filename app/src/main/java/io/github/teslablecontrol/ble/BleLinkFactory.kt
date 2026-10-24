package io.github.teslablecontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkFactory
import io.github.teslablecontrol.core.LinkFailure
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.data.ProfileStore
import io.github.teslablecontrol.protocol.transport.Transport
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Opens BLE links to cars. A remembered address is tried first (no scan, no location needed);
 * otherwise the car is found by scanning for its VIN-derived name, and the address is remembered.
 */
@SuppressLint("MissingPermission") // Permissions are checked before every Bluetooth call.
class BleLinkFactory(
    private val context: Context,
    private val profiles: ProfileStore,
    private val scanner: VehicleScanner,
    private val log: DiagnosticsLog,
    private val rememberAddress: suspend (vin: String, address: String) -> Unit,
) : LinkFactory {

    private fun adapter(): BluetoothAdapter {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) throw LinkException(LinkFailure.BLUETOOTH_OFF, "Bluetooth is off")
        if (!BlePermissions.canConnect(context)) throw LinkException(LinkFailure.PERMISSION_MISSING, "Bluetooth permission missing")
        return adapter
    }

    override suspend fun open(vin: String): Transport {
        val adapter = adapter()
        profiles.find(vin)?.bleAddress?.let { address ->
            try {
                log.info("Connecting to remembered address")
                return BleTransport.connect(context, adapter.getRemoteDevice(address), log, autoConnect = false, timeout = 8.seconds)
            } catch (e: LinkException) {
                log.warn("Direct connection failed (${e.message}); scanning instead")
            }
        }
        val sighting = scanner.find(vin, 16.seconds)
            ?: throw LinkException(LinkFailure.NOT_FOUND, "the car wasn't found nearby")
        if (!sighting.connectable) {
            throw LinkException(LinkFailure.TOO_MANY_CONNECTIONS, "the car already has the maximum number of Bluetooth connections")
        }
        val transport = connectWithRetry(sighting.device)
        rememberAddress(vin, sighting.device.address)
        return transport
    }

    /**
     * For the background connection: Android connects whenever the remembered car comes into range,
     * without scanning. Suspends until then (cancel to stop waiting).
     */
    suspend fun waitForVehicle(vin: String): Transport {
        val address = profiles.find(vin)?.bleAddress
            ?: throw LinkException(LinkFailure.NOT_FOUND, "the car hasn't been found yet; open the app near the car first")
        return BleTransport.connect(context, adapter().getRemoteDevice(address), log, autoConnect = true, timeout = null)
    }

    /** Android's generic GATT error 133 is often transient, so try a few times with a short pause. */
    private suspend fun connectWithRetry(device: BluetoothDevice): Transport {
        var last: LinkException? = null
        repeat(3) { attempt ->
            try {
                return BleTransport.connect(context, device, log, autoConnect = false, timeout = 10.seconds)
            } catch (e: LinkException) {
                last = e
                log.warn("Connection attempt ${attempt + 1} failed: ${e.message}")
                delay((500 * (attempt + 1)).milliseconds)
            }
        }
        throw last ?: LinkException(LinkFailure.CONNECTION_FAILED, "could not connect")
    }
}
