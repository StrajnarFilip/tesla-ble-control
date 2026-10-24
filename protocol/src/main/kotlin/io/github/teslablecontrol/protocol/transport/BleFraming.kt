package io.github.teslablecontrol.protocol.transport

import io.github.teslablecontrol.protocol.Clock

/**
 * BLE message framing (port of `pkg/connector/ble/ble.go`): each message is preceded by its
 * two-byte big-endian length and split across GATT writes/indications.
 */
object BleFraming {
    /** Largest message the official BLE connector accepts. */
    const val MAX_MESSAGE_SIZE = 1024

    /** Bytes of ATT header in each write, so chunk size = MTU − 3. */
    const val ATT_HEADER_SIZE = 3

    const val DEFAULT_MTU = 23

    fun encode(message: ByteArray): ByteArray {
        require(message.size <= 0xFFFF) { "message too long for BLE framing" }
        return byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message
    }

    /** Splits an encoded frame into GATT write payloads for the negotiated [mtu]. */
    fun chunk(frame: ByteArray, mtu: Int): List<ByteArray> {
        val size = (minOf(mtu, MAX_MESSAGE_SIZE) - ATT_HEADER_SIZE).coerceAtLeast(1)
        return (frame.indices step size).map { frame.copyOfRange(it, minOf(it + size, frame.size)) }
    }
}

/**
 * Reassembles messages from GATT indications. A partial message is discarded if more than
 * [rxTimeoutMillis] pass between chunks, matching the official connector.
 */
class FrameReassembler(
    private val clock: Clock = Clock.SYSTEM,
    private val maxMessageSize: Int = BleFraming.MAX_MESSAGE_SIZE,
    private val rxTimeoutMillis: Long = 1_000,
) {
    private var buffer = ByteArray(0)
    private var lastRxNanos: Long? = null

    /** Feeds one chunk; returns the messages it completed (possibly none). */
    @Synchronized
    fun push(chunk: ByteArray): List<ByteArray> {
        val now = clock.monotonicNanos()
        val last = lastRxNanos
        if (last == null || now - last > rxTimeoutMillis * 1_000_000) buffer = ByteArray(0)
        lastRxNanos = now
        buffer += chunk

        val messages = mutableListOf<ByteArray>()
        while (buffer.size >= 2) {
            val length = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
            if (length > maxMessageSize) {
                buffer = ByteArray(0)
                break
            }
            if (buffer.size < 2 + length) break
            messages += buffer.copyOfRange(2, 2 + length)
            buffer = buffer.copyOfRange(2 + length, buffer.size)
        }
        return messages
    }

    @Synchronized
    fun reset() {
        buffer = ByteArray(0)
        lastRxNanos = null
    }
}
