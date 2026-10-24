package io.github.teslablecontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import io.github.teslablecontrol.core.LinkException
import io.github.teslablecontrol.core.LinkFailure
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.protocol.transport.BleFraming
import io.github.teslablecontrol.protocol.transport.TeslaBle
import io.github.teslablecontrol.protocol.transport.TransportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One GATT connection to the car's vehicle service. All callbacks arrive on a private thread, and
 * only one GATT operation runs at a time (Android silently drops overlapping ones). A disconnected
 * [BluetoothGatt] is always closed and never reused.
 */
@SuppressLint("MissingPermission") // Callers check BlePermissions first.
internal class GattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val log: DiagnosticsLog,
    private val onChunk: (ByteArray) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val thread = HandlerThread("tesla-ble-gatt").apply { start() }
    private val handler = Handler(thread.looper)
    private val connected = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()
    private val operationLock = Mutex()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var pending: CompletableDeferred<Int>? = null
    @Volatile private var toVehicle: BluetoothGattCharacteristic? = null

    @Volatile
    var mtu: Int = BleFraming.DEFAULT_MTU
        private set

    val isOpen: Boolean get() = connected.isCompleted && !closed.isCompleted

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log.info("Bluetooth state $newState (status $status)")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val error = LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth link closed (status $status)")
                connected.completeExceptionally(error)
                pending?.completeExceptionally(error)
                runCatching { g.close() }
                markClosed()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@GattClient.mtu = mtu
            pending?.complete(status)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pending?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == FROM_VEHICLE) onChunk(value.copyOf())
        }

        @Deprecated("Used before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == FROM_VEHICLE) {
                characteristic.value?.let { onChunk(it.copyOf()) }
            }
        }
    }

    /**
     * Connects and prepares the vehicle service. With [autoConnect] Android waits in the background
     * until the car is in range ([timeout] null waits indefinitely).
     */
    suspend fun open(autoConnect: Boolean, timeout: Duration?) {
        try {
            // Deprecated in API 37 for a settings-object overload that older Android versions don't have.
            @Suppress("DEPRECATION")
            val g = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler)
                ?: throw LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth refused to connect")
            gatt = g
            if (timeout == null) connected.await() else withTimeout(timeout) { connected.await() }

            // A larger MTU means fewer writes per message; 23 bytes still works if refused.
            runCatching { operation(5.seconds) { g.requestMtu(517) } }
                .onFailure { log.warn("MTU negotiation failed; using ${BleFraming.DEFAULT_MTU}") }
            log.info("MTU $mtu")

            if (operation(10.seconds) { g.discoverServices() } != BluetoothGatt.GATT_SUCCESS) {
                throw LinkException(LinkFailure.CONNECTION_FAILED, "service discovery failed")
            }
            val service = g.getService(SERVICE)
                ?: throw LinkException(LinkFailure.CONNECTION_FAILED, "this device is not a Tesla vehicle")
            toVehicle = service.getCharacteristic(TO_VEHICLE)
                ?: throw LinkException(LinkFailure.CONNECTION_FAILED, "vehicle write characteristic missing")
            val fromVehicle = service.getCharacteristic(FROM_VEHICLE)
                ?: throw LinkException(LinkFailure.CONNECTION_FAILED, "vehicle read characteristic missing")
            subscribe(g, fromVehicle)
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } catch (e: Throwable) {
            // Including cancellation: an abandoned connection attempt must not stay registered.
            close()
            throw when (e) {
                is TimeoutCancellationException -> LinkException(LinkFailure.CONNECTION_FAILED, "timed out connecting to the car", e)
                is SecurityException -> LinkException(LinkFailure.PERMISSION_MISSING, "Bluetooth permission missing", e)
                else -> e
            }
        }
    }

    private suspend fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!g.setCharacteristicNotification(characteristic, true)) {
            throw LinkException(LinkFailure.CONNECTION_FAILED, "could not subscribe to the car")
        }
        val descriptor = characteristic.getDescriptor(CCCD)
            ?: throw LinkException(LinkFailure.CONNECTION_FAILED, "subscription descriptor missing")
        // The official connector uses indications; fall back to notifications if that's all there is.
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val status = operation(5.seconds) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
        if (status != BluetoothGatt.GATT_SUCCESS) throw LinkException(LinkFailure.CONNECTION_FAILED, "subscription failed (status $status)")
    }

    /** Writes one chunk with response, suspending until the car acknowledges it. */
    suspend fun write(chunk: ByteArray) {
        val g = gatt ?: throw TransportException("not connected")
        val characteristic = toVehicle ?: throw TransportException("not connected")
        val status = try {
            operation(5.seconds) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(characteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    characteristic.value = chunk
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(characteristic)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw TransportException("Bluetooth write timed out", e)
        } catch (e: LinkException) {
            throw TransportException(e.message ?: "Bluetooth link closed", e)
        }
        if (status != BluetoothGatt.GATT_SUCCESS) throw TransportException("Bluetooth write failed (status $status)")
    }

    /** Runs one GATT operation; [start] returns false if Android refused to start it. */
    private suspend fun operation(timeout: Duration, start: () -> Boolean): Int = operationLock.withLock {
        if (closed.isCompleted) throw LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth link closed")
        val result = CompletableDeferred<Int>()
        pending = result
        try {
            if (!start()) throw LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth was busy")
            withTimeout(timeout) { result.await() }
        } finally {
            pending = null
        }
    }

    suspend fun awaitClosed() = closed.await()

    /** Disconnects and releases the connection. Idempotent. */
    fun close() {
        val g = gatt
        gatt = null
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        markClosed()
    }

    private fun markClosed() {
        if (closed.complete(Unit)) {
            pending?.completeExceptionally(LinkException(LinkFailure.CONNECTION_FAILED, "Bluetooth link closed"))
            onClosed()
            thread.quitSafely()
        }
    }

    companion object {
        val SERVICE: UUID = UUID.fromString(TeslaBle.SERVICE_UUID)
        val TO_VEHICLE: UUID = UUID.fromString(TeslaBle.TO_VEHICLE_UUID)
        val FROM_VEHICLE: UUID = UUID.fromString(TeslaBle.FROM_VEHICLE_UUID)
        val CCCD: UUID = UUID.fromString(TeslaBle.CLIENT_CHARACTERISTIC_CONFIG_UUID)
    }
}
