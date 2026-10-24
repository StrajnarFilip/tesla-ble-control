package io.github.teslablecontrol.protocol

import com.google.protobuf.ByteString
import java.math.BigInteger
import java.security.SecureRandom

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** Lower-case hex encoding. */
fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_DIGITS[v ushr 4]
        out[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
    }
    return String(out)
}

fun ByteString.toHex(): String = toByteArray().toHex()

/** Decodes hex (either case, optional whitespace); throws [IllegalArgumentException] if malformed. */
fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "hex string has odd length" }
    return ByteArray(clean.length / 2) { i ->
        val hi = Character.digit(clean[i * 2], 16)
        val lo = Character.digit(clean[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex character" }
        ((hi shl 4) or lo).toByte()
    }
}

internal fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)

/** Four-byte big-endian encoding of a protobuf uint32/fixed32 (stored by protobuf-java in an [Int]). */
internal fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this ushr 24).toByte(),
    (this ushr 16).toByte(),
    (this ushr 8).toByte(),
    this.toByte(),
)

internal fun SecureRandom.bytes(count: Int): ByteArray = ByteArray(count).also { nextBytes(it) }

/** Unsigned big-endian encoding padded (or validated) to exactly [size] bytes. */
internal fun BigInteger.toFixedBytes(size: Int): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == size -> raw
        raw.size == size + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
        raw.size < size -> ByteArray(size - raw.size) + raw
        else -> throw IllegalArgumentException("value does not fit in $size bytes")
    }
}

internal fun ByteArray.leftPad(size: Int): ByteArray =
    if (this.size >= size) this else ByteArray(size - this.size) + this
