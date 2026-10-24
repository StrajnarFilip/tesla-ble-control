package io.github.teslablecontrol.protocol.testing

import io.github.teslablecontrol.protocol.transport.BleFraming
import io.github.teslablecontrol.protocol.transport.FrameReassembler
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.transport.TransportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Talks to the reference vehicle simulator over TCP using the BLE wire format (two-byte length
 * prefix), split into [mtu]-sized writes so reassembly is exercised as on a real link.
 */
class TcpTransport(host: String, port: Int, private val mtu: Int = 185) : Transport {
    private val socket = Socket().apply {
        tcpNoDelay = true
        connect(InetSocketAddress(host, port), 5_000)
    }
    private val output = socket.getOutputStream()
    private val writeLock = Mutex()
    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messages = Channel<ByteArray>(Channel.UNLIMITED)
    private val reassembler = FrameReassembler()

    override val incoming: ReceiveChannel<ByteArray> get() = messages

    init {
        scope.launch {
            val input = socket.getInputStream()
            val buffer = ByteArray(mtu)
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    reassembler.push(buffer.copyOf(read)).forEach { messages.trySend(it) }
                }
            } catch (_: IOException) {
                // Socket closed.
            } finally {
                messages.close()
            }
        }
    }

    override suspend fun send(message: ByteArray) {
        if (closed.get()) throw TransportException("transport closed")
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    for (chunk in BleFraming.chunk(BleFraming.encode(message), mtu)) {
                        output.write(chunk)
                        output.flush()
                    }
                } catch (e: IOException) {
                    throw TransportException("write failed: ${e.message}", e)
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.close() }
            scope.cancel()
            messages.close()
        }
    }
}
