package io.github.teslablecontrol.protocol

import io.github.teslablecontrol.protocol.testing.FakeClock
import io.github.teslablecontrol.protocol.transport.BleFraming
import io.github.teslablecontrol.protocol.transport.FrameReassembler
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FramingTest {
    @Test
    fun chunksRespectMtu() {
        val frame = BleFraming.encode(ByteArray(300) { it.toByte() })
        val chunks = BleFraming.chunk(frame, mtu = 23)
        assertTrue(chunks.all { it.size <= 20 })
        assertContentEquals(frame, chunks.reduce { acc, bytes -> acc + bytes })
        assertEquals(1, BleFraming.chunk(BleFraming.encode(ByteArray(100)), mtu = 517).size)
    }

    @Test
    fun reassemblesRandomlySplitMessages() {
        val random = Random(42)
        repeat(200) {
            val messages = List(random.nextInt(1, 4)) { ByteArray(random.nextInt(0, 1024)) { random.nextInt().toByte() } }
            val stream = messages.fold(ByteArray(0)) { acc, m -> acc + BleFraming.encode(m) }
            val reassembler = FrameReassembler(FakeClock())
            val received = mutableListOf<ByteArray>()
            var position = 0
            while (position < stream.size) {
                val size = random.nextInt(1, 200).coerceAtMost(stream.size - position)
                received += reassembler.push(stream.copyOfRange(position, position + size))
                position += size
            }
            assertEquals(messages.size, received.size)
            messages.zip(received).forEach { (expected, actual) -> assertContentEquals(expected, actual) }
        }
    }

    @Test
    fun dropsOversizedMessages() {
        val reassembler = FrameReassembler(FakeClock())
        val oversized = byteArrayOf(0x04, 0x01) + ByteArray(10) // claims 1025 bytes
        assertTrue(reassembler.push(oversized).isEmpty())
        // The buffer was cleared, so the next well-formed message is delivered.
        assertContentEquals(byteArrayOf(1, 2, 3), reassembler.push(BleFraming.encode(byteArrayOf(1, 2, 3))).single())
    }

    @Test
    fun discardsStalePartialMessages() {
        val clock = FakeClock()
        val reassembler = FrameReassembler(clock)
        val frame = BleFraming.encode(ByteArray(50) { 7 })
        assertTrue(reassembler.push(frame.copyOfRange(0, 10)).isEmpty())
        clock.advance(1500.milliseconds) // More than the 1 s chunk gap the official connector allows.
        assertTrue(reassembler.push(frame.copyOfRange(10, frame.size)).isEmpty())
        assertContentEquals(ByteArray(50) { 7 }, reassembler.push(frame).single())
    }
}
