package io.github.teslablecontrol.protocol

import io.github.teslablecontrol.protocol.crypto.KeyCodec
import io.github.teslablecontrol.protocol.crypto.P256
import io.github.teslablecontrol.protocol.crypto.SoftwareEcdhKey
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeyCodecTest {
    @Test
    fun generatedKeyRoundTripsThroughPem() {
        val key = SoftwareEcdhKey.generate()
        val pem = KeyCodec.privateKeyToPem(key)
        assertTrue(pem.startsWith("-----BEGIN EC PRIVATE KEY-----"))
        val parsed = KeyCodec.parsePrivateKeyPem(pem)
        assertContentEquals(key.privateScalar(), parsed.privateScalar())
        assertContentEquals(key.publicKey, parsed.publicKey)
        assertContentEquals(key.publicKey, KeyCodec.parsePublicKey(pem))
    }

    @Test
    fun publicKeyFormats() {
        val key = SoftwareEcdhKey.generate()
        val pem = KeyCodec.publicKeyToPem(key.publicKey)
        assertContentEquals(key.publicKey, KeyCodec.parsePublicKey(pem))
        assertContentEquals(key.publicKey, KeyCodec.parsePublicKey(key.publicKey))
        assertContentEquals(key.publicKey, KeyCodec.parsePublicKey(key.publicKey.toHex()))
        assertContentEquals(key.publicKey, KeyCodec.parsePublicKey((key.publicKey.toHex() + "\n").toByteArray()))
    }

    @Test
    fun scalarImportDerivesPublicKey() {
        val key = SoftwareEcdhKey.generate()
        val rebuilt = SoftwareEcdhKey.fromPrivateScalar(key.privateScalar())
        assertContentEquals(key.publicKey, rebuilt.publicKey)
        // ECDH both ways agrees.
        val other = SoftwareEcdhKey.generate()
        assertContentEquals(rebuilt.sharedSecret(other.publicKey), other.sharedSecret(key.publicKey))
    }

    @Test
    fun pkcs8PrivateKeyParses() {
        val key = SoftwareEcdhKey.generate()
        val der = java.security.KeyFactory.getInstance("EC")
            .generatePrivate(java.security.spec.ECPrivateKeySpec(BigInteger(1, key.privateScalar()), P256.parameterSpec))
            .encoded // PKCS#8
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
            "\n-----END PRIVATE KEY-----\n"
        assertContentEquals(key.publicKey, KeyCodec.parsePrivateKeyPem(pem).publicKey)
    }

    @Test
    fun rejectsInvalidKeys() {
        val notOnCurve = ByteArray(65).also { it[0] = 4; it[64] = 1 }
        assertFailsWith<KeyCodec.KeyFormatException> { KeyCodec.parsePublicKey(notOnCurve) }
        assertFailsWith<KeyCodec.KeyFormatException> { KeyCodec.parsePublicKey("hello") }
        assertFailsWith<IllegalArgumentException> { SoftwareEcdhKey.fromPrivateScalar(ByteArray(32)) }
        assertFailsWith<IllegalArgumentException> { SoftwareEcdhKey.fromPrivateScalar(P256.N.toByteArray().takeLast(32).toByteArray()) }
        assertFailsWith<KeyCodec.KeyFormatException> {
            KeyCodec.parsePrivateKeyPem("-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----")
        }
    }

    @Test
    fun specVehicleKeyMatchesDocumentedPoint() {
        val key = KeyCodec.parsePrivateKeyPem(SpecKeys.VEHICLE_KEY_PEM)
        assertEquals(SpecKeys.VEHICLE_PUBLIC_HEX, key.publicKey.toHex())
    }
}
