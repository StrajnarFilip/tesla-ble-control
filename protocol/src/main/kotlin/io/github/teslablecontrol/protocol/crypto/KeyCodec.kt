package io.github.teslablecontrol.protocol.crypto

import io.github.teslablecontrol.protocol.hexToBytes
import io.github.teslablecontrol.protocol.toFixedBytes
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64

/**
 * Key import/export in the formats accepted by the official tools (`protocol.LoadPublicKey`,
 * `protocol.LoadPrivateKey`, `protocol.SavePrivateKey`).
 */
object KeyCodec {
    private val OID_EC_PUBLIC_KEY = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01)
    private val OID_PRIME256V1 = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07)

    class KeyFormatException(message: String) : IllegalArgumentException(message)

    /** Parses a SEC1 (`EC PRIVATE KEY`) or unencrypted PKCS#8 (`PRIVATE KEY`) PEM private key. */
    fun parsePrivateKeyPem(pem: String): SoftwareEcdhKey {
        val block = decodePem(pem) ?: throw KeyFormatException("expected a PEM-encoded private key")
        return when (block.type) {
            "EC PRIVATE KEY" -> parseSec1(block.der)
            "PRIVATE KEY" -> parsePkcs8(block.der)
            "ENCRYPTED PRIVATE KEY" -> throw KeyFormatException("password-protected keys are not supported")
            else -> throw KeyFormatException("unsupported PEM block type ${block.type}")
        }
    }

    /**
     * Public key from any format `protocol.LoadPublicKey` accepts: raw 65-byte point, hex point
     * (130 characters, optional trailing newline), PKIX `PUBLIC KEY` PEM, or a private-key PEM.
     */
    fun parsePublicKey(data: ByteArray): ByteArray {
        if (data.size == P256.UNCOMPRESSED_POINT_SIZE) return validated(data)
        val text = String(data, Charsets.US_ASCII)
        if (data.size == 130 || data.size == 131) {
            val decoded = runCatching { text.substring(0, 130).hexToBytes() }.getOrNull()
            if (decoded != null) return validated(decoded)
        }
        val block = decodePem(text) ?: throw KeyFormatException("unrecognized public key format")
        return when (block.type) {
            "PUBLIC KEY" -> parsePkix(block.der)
            "EC PRIVATE KEY" -> parseSec1(block.der).publicKey
            "PRIVATE KEY" -> parsePkcs8(block.der).publicKey
            else -> throw KeyFormatException("unrecognized PEM block type ${block.type}")
        }
    }

    fun parsePublicKey(text: String): ByteArray = parsePublicKey(text.trim().toByteArray(Charsets.US_ASCII))

    /** SEC1 `EC PRIVATE KEY` PEM with curve and public key, the layout `x509.MarshalECPrivateKey` writes. */
    fun privateKeyToPem(key: SoftwareEcdhKey): String {
        val der = sequence(
            derInteger(BigInteger.ONE),
            der(0x04, key.privateScalar()),
            der(0xA0, der(0x06, OID_PRIME256V1)),
            der(0xA1, der(0x03, byteArrayOf(0) + key.publicKey)),
        )
        return encodePem("EC PRIVATE KEY", der)
    }

    /** PKIX `PUBLIC KEY` PEM. */
    fun publicKeyToPem(publicKey: ByteArray): String {
        validated(publicKey)
        val der = sequence(
            sequence(der(0x06, OID_EC_PUBLIC_KEY), der(0x06, OID_PRIME256V1)),
            der(0x03, byteArrayOf(0) + publicKey),
        )
        return encodePem("PUBLIC KEY", der)
    }

    private fun validated(point: ByteArray): ByteArray {
        try {
            P256.decode(point)
        } catch (e: IllegalArgumentException) {
            throw KeyFormatException(e.message ?: "invalid public key")
        }
        return point.copyOf()
    }

    private fun parseSec1(der: ByteArray): SoftwareEcdhKey {
        val seq = DerReader(der).expect(0x30).reader()
        val version = seq.expect(0x02).value
        if (BigInteger(version) != BigInteger.ONE) throw KeyFormatException("unsupported EC private key version")
        val scalar = seq.expect(0x04).value
        var embeddedPublic: ByteArray? = null
        while (seq.hasMore()) {
            val field = seq.read()
            when (field.tag) {
                0xA0 -> {
                    val oid = field.reader().expect(0x06).value
                    if (!oid.contentEquals(OID_PRIME256V1)) throw KeyFormatException("only NIST P-256 keys are supported")
                }
                0xA1 -> {
                    val bits = field.reader().expect(0x03).value
                    if (bits.isEmpty() || bits[0] != 0.toByte()) throw KeyFormatException("malformed public key")
                    embeddedPublic = bits.copyOfRange(1, bits.size)
                }
            }
        }
        if (scalar.size > P256.FIELD_SIZE) throw KeyFormatException("private key is too long for P-256")
        val key = try {
            SoftwareEcdhKey.fromPrivateScalar(BigInteger(1, scalar).toFixedBytes(P256.FIELD_SIZE))
        } catch (e: IllegalArgumentException) {
            throw KeyFormatException(e.message ?: "invalid private key")
        }
        if (embeddedPublic != null && !embeddedPublic.contentEquals(key.publicKey)) {
            throw KeyFormatException("embedded public key does not match the private key")
        }
        return key
    }

    private fun parsePkcs8(der: ByteArray): SoftwareEcdhKey {
        val seq = DerReader(der).expect(0x30).reader()
        seq.expect(0x02)
        val algorithm = seq.expect(0x30).reader()
        val algorithmOid = algorithm.expect(0x06).value
        if (!algorithmOid.contentEquals(OID_EC_PUBLIC_KEY)) throw KeyFormatException("only elliptic curve keys are supported")
        val curveOid = algorithm.expect(0x06).value
        if (!curveOid.contentEquals(OID_PRIME256V1)) throw KeyFormatException("only NIST P-256 keys are supported")
        return parseSec1(seq.expect(0x04).value)
    }

    private fun parsePkix(der: ByteArray): ByteArray {
        val seq = DerReader(der).expect(0x30).reader()
        val algorithm = seq.expect(0x30).reader()
        if (!algorithm.expect(0x06).value.contentEquals(OID_EC_PUBLIC_KEY)) throw KeyFormatException("not an elliptic curve public key")
        if (!algorithm.expect(0x06).value.contentEquals(OID_PRIME256V1)) throw KeyFormatException("only NIST P-256 keys are supported")
        val bits = seq.expect(0x03).value
        if (bits.isEmpty() || bits[0] != 0.toByte()) throw KeyFormatException("malformed public key")
        return validated(bits.copyOfRange(1, bits.size))
    }

    private class PemBlock(val type: String, val der: ByteArray)

    private val PEM_REGEX = Regex("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", RegexOption.DOT_MATCHES_ALL)

    private fun decodePem(text: String): PemBlock? {
        val match = PEM_REGEX.find(text) ?: return null
        val body = match.groupValues[2]
        if (body.lines().any { it.contains(':') }) throw KeyFormatException("encrypted PEM keys are not supported")
        val der = try {
            Base64.getMimeDecoder().decode(body.trim())
        } catch (e: IllegalArgumentException) {
            throw KeyFormatException("invalid base64 in PEM block")
        }
        return PemBlock(match.groupValues[1], der)
    }

    private fun encodePem(type: String, der: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $type-----\n$body\n-----END $type-----\n"
    }

    // --- Minimal DER encoding/decoding for the three fixed key structures above ---

    private fun derLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
        else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
    }

    private fun der(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + derLength(content.size) + content

    private fun sequence(vararg items: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        items.forEach { out.write(it) }
        return der(0x30, out.toByteArray())
    }

    private fun derInteger(value: BigInteger): ByteArray = der(0x02, value.toByteArray())

    private class Tlv(val tag: Int, val value: ByteArray) {
        fun reader() = DerReader(value)
    }

    private class DerReader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun read(): Tlv {
            if (pos + 2 > data.size) throw KeyFormatException("truncated DER")
            val tag = data[pos++].toInt() and 0xFF
            var length = data[pos++].toInt() and 0xFF
            if (length and 0x80 != 0) {
                val count = length and 0x7F
                if (count == 0 || count > 3 || pos + count > data.size) throw KeyFormatException("bad DER length")
                length = 0
                repeat(count) { length = (length shl 8) or (data[pos++].toInt() and 0xFF) }
            }
            if (pos + length > data.size) throw KeyFormatException("truncated DER")
            val value = data.copyOfRange(pos, pos + length)
            pos += length
            return Tlv(tag, value)
        }

        fun expect(tag: Int): Tlv {
            val tlv = read()
            if (tlv.tag != tag) throw KeyFormatException("unexpected DER structure")
            return tlv
        }
    }
}
