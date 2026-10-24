package io.github.teslablecontrol.protocol.crypto

import com.tesla.generated.signatures.Signatures
import io.github.teslablecontrol.protocol.toBigEndianBytes
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Canonical metadata serialization (port of `internal/authentication/metadata.go`): tag-length-value
 * items in ascending tag order, terminated by `TAG_END` (0xFF).
 */
class Metadata {
    private val out = ByteArrayOutputStream()
    private var last = -1

    /** Adds an item; a null value is skipped (as the Go implementation skips nil slices). */
    fun add(tag: Signatures.Tag, value: ByteArray?): Metadata {
        val number = tag.number
        check(number >= last) { "metadata items need to be added in increasing tag order" }
        if (value == null) return this
        require(value.size <= 255) { "metadata fields can't be more than 255 bytes long" }
        last = number
        out.write(number)
        out.write(value.size)
        out.write(value)
        return this
    }

    fun addByte(tag: Signatures.Tag, value: Int): Metadata = add(tag, byteArrayOf(value.toByte()))

    /** Adds a uint32 (protobuf-java stores uint32/fixed32 in an [Int]) as four big-endian bytes. */
    fun addUint32(tag: Signatures.Tag, value: Int): Metadata = add(tag, value.toBigEndianBytes())

    /** The serialized items followed by `TAG_END`. */
    fun serialize(): ByteArray = out.toByteArray() + byteArrayOf(Signatures.Tag.TAG_END.number.toByte())

    /** SHA-256 over the serialized metadata, used as AES-GCM associated data. */
    fun sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(serialize())
}
