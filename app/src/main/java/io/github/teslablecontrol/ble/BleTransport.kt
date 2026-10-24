package io.github.teslablecontrol.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import io.github.teslablecontrol.data.DiagnosticsLog
import io.github.teslablecontrol.protocol.transport.BleFraming
import io.github.teslablecontrol.protocol.transport.FrameReassembler
import io.github.teslablecontrol.protocol.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/** The protocol's [Transport] over a GATT connection: length-prefixed frames split to the MTU. */
class BleTransport private constructor(context: Context, device: BluetoothDevice, log: DiagnosticsLog) : Transport {
    private val reassembler = FrameReassembler()
    private val messages = Channel<ByteArray>(Channel.UNLIMITED)
    private val sendLock = Mutex()
    private val gatt = GattClient(
        context, device, log,
        onChunk = { chunk -> reassembler.push(chunk).forEach { messages.trySend(it) } },
        onClosed = { messages.close() },
    )

    override val incoming: ReceiveChannel<ByteArray> get() = messages

    val mtu: Int get() = gatt.mtu

    override suspend fun send(message: ByteArray) = sendLock.withLock {
        // Chunks of one message must not interleave with another's.
        for (chunk in BleFraming.chunk(BleFraming.encode(message), gatt.mtu)) gatt.write(chunk)
    }

    override fun close() = gatt.close()

    suspend fun awaitClosed() = gatt.awaitClosed()

    companion object {
        /** Connects to [device]; see [GattClient.open] for [autoConnect] and [timeout]. */
        suspend fun connect(
            context: Context,
            device: BluetoothDevice,
            log: DiagnosticsLog,
            autoConnect: Boolean,
            timeout: Duration?,
        ): BleTransport = BleTransport(context, device, log).also { it.gatt.open(autoConnect, timeout) }
    }
}
