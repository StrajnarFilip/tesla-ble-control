package io.github.teslablecontrol.protocol

import com.tesla.generated.signatures.Signatures
import io.github.teslablecontrol.protocol.crypto.Metadata
import io.github.teslablecontrol.protocol.session.SlidingWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Cases ported from vehicle-command's `window_test.go` and `metadata_test.go`. */
class PortedGoTest {
    private data class WindowCase(
        val counter: UInt,
        val window: Long,
        val newCounter: UInt,
        val expectedCounter: UInt,
        val expectedWindow: Long,
        val expectedOk: Boolean,
    )

    @Test
    fun slidingWindow() {
        val base = (1L shl 0) or (1L shl 5)
        val cases = listOf(
            WindowCase(100u, base, 101u, 101u, 1L or (1L shl 1) or (1L shl 6), true),
            WindowCase(100u, base, 103u, 103u, (1L shl 2) or (1L shl 3) or (1L shl 8), true),
            WindowCase(100u, base, 500u, 500u, 0, true),
            WindowCase(100u, base, 98u, 100u, (1L shl 0) or (1L shl 1) or (1L shl 5), true),
            WindowCase(100u, base, 99u, 100u, base, false),
            WindowCase(100u, base, 3u, 100u, base, false),
            WindowCase(100u, base, 100u, 100u, base, false),
        )
        for (case in cases) {
            val result = SlidingWindow.slide(case.counter, case.window, case.newCounter)
            if (case.expectedOk) {
                assertEquals(case.expectedCounter to case.expectedWindow, result, "case $case")
            } else {
                assertEquals(null, result, "case $case")
            }
        }
    }

    @Test
    fun slidingWindowFirstUseAndReplay() {
        val window = SlidingWindow()
        assert(window.update(7u))
        assert(!window.update(7u))
        assert(window.update(9u))
        assert(window.update(8u))
        assert(!window.update(8u))
    }

    @Test
    fun metadataOutOfOrder() {
        val meta = Metadata()
        meta.add(Signatures.Tag.TAG_DOMAIN, "hello".toByteArray())
        meta.add(Signatures.Tag.TAG_PERSONALIZATION, "world".toByteArray())
        assertFailsWith<IllegalStateException> { meta.add(Signatures.Tag.TAG_DOMAIN, "world".toByteArray()) }
    }

    @Test
    fun metadataValueTooLong() {
        assertFailsWith<IllegalArgumentException> { Metadata().add(Signatures.Tag.TAG_DOMAIN, ByteArray(256)) }
    }

    @Test
    fun metadataChecksum() {
        val meta = Metadata()
            .add(Signatures.Tag.TAG_SIGNATURE_TYPE, byteArrayOf(0x05))
            .add(Signatures.Tag.TAG_DOMAIN, byteArrayOf(0x02))
            .add(Signatures.Tag.TAG_PERSONALIZATION, "testVIN".toByteArray())
            .add(Signatures.Tag.TAG_EPOCH, "aada928a4f215f55f9e6e45e66b6521e".hexToBytes())
            .add(Signatures.Tag.TAG_EXPIRES_AT, "00000e74".hexToBytes())
            .add(Signatures.Tag.TAG_COUNTER, "0000053a".hexToBytes())
        assertEquals("abab04d804499813382efd74a06791ce2de777439603246dfbaa8392ca05868e", meta.sha256().toHex())
    }
}
