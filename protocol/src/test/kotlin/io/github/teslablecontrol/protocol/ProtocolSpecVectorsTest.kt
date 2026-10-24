package io.github.teslablecontrol.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import io.github.teslablecontrol.protocol.crypto.KeyCodec
import io.github.teslablecontrol.protocol.crypto.Metadata
import io.github.teslablecontrol.protocol.crypto.SessionCrypto
import io.github.teslablecontrol.protocol.session.Signer
import io.github.teslablecontrol.protocol.testing.FakeClock
import io.github.teslablecontrol.protocol.transport.TeslaBle
import io.github.teslablecontrol.protocol.vehicle.VehicleRequest
import io.github.teslablecontrol.protocol.vehicle.VehicleRequests
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Every worked example in vehicle-command's `pkg/protocol/protocol.md`. */
class ProtocolSpecVectorsTest {
    private val vin = "5YJ30123456789ABC"
    private val random = SecureRandom()

    private val clientKey = KeyCodec.parsePrivateKeyPem(SpecKeys.CLIENT_KEY_PEM)
    private val vehicleKey = KeyCodec.parsePrivateKeyPem(SpecKeys.VEHICLE_KEY_PEM)

    @Test
    fun testKeysDecodeToDocumentedPoints() {
        assertEquals(SpecKeys.CLIENT_PUBLIC_HEX, clientKey.publicKey.toHex())
        assertEquals(SpecKeys.VEHICLE_PUBLIC_HEX, vehicleKey.publicKey.toHex())
        assertEquals("2538cdc29a97c19c1e99a637d6cf4f8c970c118b56ede1e6323e6d162c4b30db", clientKey.privateScalar().toHex())
        assertEquals("344ee5b466a7cf1eeb12b6f50331db2e5ec5834ef5f4befcfd8cbe55c2528d70", vehicleKey.privateScalar().toHex())
        assertContentEquals(vehicleKey.publicKey, KeyCodec.parsePublicKey(SpecKeys.VEHICLE_PUBLIC_PEM))
    }

    @Test
    fun sharedKeyMatchesBothDirections() {
        val clientSide = SessionCrypto(clientKey.sharedSecret(vehicleKey.publicKey), clientKey.publicKey, random)
        val vehicleSide = SessionCrypto(vehicleKey.sharedSecret(clientKey.publicKey), vehicleKey.publicKey, random)
        assertEquals("1b2fce19967b79db696f909cff89ea9a", clientSide.key.toHex())
        assertContentEquals(clientSide.key, vehicleSide.key)
    }

    @Test
    fun sessionInfoKeyAndTag() {
        val crypto = SessionCrypto(clientKey.sharedSecret(vehicleKey.publicKey), clientKey.publicKey, random)
        assertEquals(
            "fceb679ee7bca756fcd441bf238bf2f338629b41d9eb9c67be1b32c9672ce300",
            crypto.subkey(SessionCrypto.LABEL_SESSION_INFO).toHex(),
        )
        val metadata = Metadata()
            .addByte(Signatures.Tag.TAG_SIGNATURE_TYPE, Signatures.SignatureType.SIGNATURE_TYPE_HMAC.number)
            .add(Signatures.Tag.TAG_PERSONALIZATION, vin.toByteArray())
            .add(Signatures.Tag.TAG_CHALLENGE, SpecKeys.CHALLENGE_HEX.hexToBytes())
        assertEquals(
            "000106021135594a333031323334353637383941424306101588d5a30eabc6f8fc9a951b11f6fd11ff",
            metadata.serialize().toHex(),
        )
        val tag = crypto.sessionInfoHmac(vin.toByteArray(), SpecKeys.CHALLENGE_HEX.hexToBytes(), SpecKeys.SESSION_INFO_HEX.hexToBytes())
        assertEquals(SpecKeys.SESSION_INFO_TAG_HEX, tag.toHex())
    }

    @Test
    fun sessionInfoDecodes() {
        val info = Signatures.SessionInfo.parseFrom(SpecKeys.SESSION_INFO_HEX.hexToBytes())
        assertEquals(6, info.counter)
        assertEquals(SpecKeys.VEHICLE_PUBLIC_HEX, info.publicKey.toHex())
        assertEquals("4c463f9cc0d3d26906e982ed224adde6", info.epoch.toHex())
        assertEquals(2650, info.clockTime)
    }

    @Test
    fun authenticatedSignerAcceptsDocumentedHandshake() {
        val signer = Signer.authenticated(
            clientKey, vin.toByteArray(), SpecKeys.CHALLENGE_HEX.hexToBytes(), SpecKeys.SESSION_INFO_HEX.hexToBytes(),
            SpecKeys.SESSION_INFO_TAG_HEX.hexToBytes(), FakeClock(), random,
        )
        assertEquals(6u, signer.currentCounter)
    }

    @Test
    fun hvacOnPayloadEncoding() {
        assertEquals("120452020801", VehicleRequests.climateOn().payload().toHex())
    }

    @Test
    fun aesGcmCommandExampleDecrypts() {
        val crypto = SessionCrypto(clientKey.sharedSecret(vehicleKey.publicKey), clientKey.publicKey, random)
        val aad = MessageDigest.getInstance("SHA-256").digest(SpecKeys.HVAC_METADATA_HEX.hexToBytes())
        val plaintext = crypto.decrypt(
            "dbf79447fa156674dae1caed".hexToBytes(), "38038e8c0f2e".hexToBytes(), aad,
            "c228e0ff64991481db3a7bbc133696c5".hexToBytes(),
        )
        assertEquals("120452020801", plaintext.toHex())
    }

    @Test
    fun signerProducesDocumentedMetadata() {
        // Session info from the handshake example: counter 6, clock 2650. Five seconds of lifetime
        // gives expires_at 2655 and the next counter is 7, as in the worked AES-GCM example.
        val signer = Signer.authenticated(
            clientKey, vin.toByteArray(), SpecKeys.CHALLENGE_HEX.hexToBytes(), SpecKeys.SESSION_INFO_HEX.hexToBytes(),
            SpecKeys.SESSION_INFO_TAG_HEX.hexToBytes(), FakeClock(), random,
        )
        val message = UniversalMessage.RoutableMessage.newBuilder()
            .setToDestination(UniversalMessage.Destination.newBuilder().setDomain(UniversalMessage.Domain.DOMAIN_INFOTAINMENT))
            .setProtobufMessageAsBytes(ByteString.copyFrom("120452020801".hexToBytes()))
            .setFlags(1 shl UniversalMessage.Flags.FLAG_ENCRYPT_RESPONSE_VALUE)
        signer.encrypt(message, 5.seconds)

        val gcm = message.signatureData.getAESGCMPersonalizedData()
        assertEquals(7, gcm.counter)
        assertEquals(2655, gcm.expiresAt)
        assertEquals("4c463f9cc0d3d26906e982ed224adde6", gcm.epoch.toHex())
        assertEquals(SpecKeys.CLIENT_PUBLIC_HEX, message.signatureData.signerIdentity.publicKey.toHex())

        // The vehicle side decrypts with AAD = SHA256(documented metadata), proving we hashed the same bytes.
        val vehicleSide = SessionCrypto(vehicleKey.sharedSecret(clientKey.publicKey), vehicleKey.publicKey, random)
        val aad = MessageDigest.getInstance("SHA-256").digest(SpecKeys.HVAC_METADATA_HEX.hexToBytes())
        val plaintext = vehicleSide.decrypt(
            gcm.nonce.toByteArray(), message.protobufMessageAsBytes.toByteArray(), aad, gcm.tag.toByteArray(),
        )
        assertEquals("120452020801", plaintext.toHex())
    }

    @Test
    fun bleLocalName() {
        assertEquals("S1a87a5a75f3df858C", TeslaBle.localName("5YJS0000000000000"))
        assert(TeslaBle.isVehicleLocalName("S1a87a5a75f3df858C"))
    }

    @Test
    fun documentedListKeysFrameDecodes() {
        val frame = UniversalMessage.RoutableMessage.parseFrom(
            "320208023a1212100a7962c10d38b61dd2a7722780a4f0969a031005514f57616bcc81a8ce0f9d7b48322952040a020805".hexToBytes(),
        )
        assertEquals(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY, frame.toDestination.domain)
        assertEquals("0a7962c10d38b61dd2a7722780a4f096", frame.fromDestination.routingAddress.toHex())
        assertEquals("05514f57616bcc81a8ce0f9d7b483229", frame.uuid.toHex())
        assertContentEquals(VehicleRequest.ListKeys.payload(), frame.protobufMessageAsBytes.toByteArray())
    }
}

/** Test keys published in protocol.md. Never enroll them on a real vehicle. */
object SpecKeys {
    const val CLIENT_KEY_PEM = """-----BEGIN EC PRIVATE KEY-----
MHcCAQEEICU4zcKal8GcHpmmN9bPT4yXDBGLVu3h5jI+bRYsSzDboAoGCCqGSM49
AwEHoUQDQgAEsra8aMLaBmXOZWgVWUmWxiOU7di+qQX+eBp1T+aoRacUMwkC8iXp
Jp1GbgWzSZgf2p2FzCPG+0RKpztikQXcbg==
-----END EC PRIVATE KEY-----"""

    const val VEHICLE_KEY_PEM = """-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIDRO5bRmp88e6xK29QMx2y5exYNO9fS+/P2MvlXCUo1woAoGCCqGSM49
AwEHoUQDQgAEx6H0cThIaqRymXFJSHjTOxok45Vx90im4WxZVbPYd9OmqqDpVRZk
dK9dMsQQ9DmiI0E3rRuwhf1OiBPJWPEdlw==
-----END EC PRIVATE KEY-----"""

    const val VEHICLE_PUBLIC_PEM = """-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEx6H0cThIaqRymXFJSHjTOxok45Vx
90im4WxZVbPYd9OmqqDpVRZkdK9dMsQQ9DmiI0E3rRuwhf1OiBPJWPEdlw==
-----END PUBLIC KEY-----"""

    const val CLIENT_PUBLIC_HEX =
        "04b2b6bc68c2da0665ce656815594996c62394edd8bea905fe781a754fe6a845a714330902f225e9269d466e05b349981fda9d85cc23c6fb444aa73b629105dc6e"
    const val VEHICLE_PUBLIC_HEX =
        "04c7a1f47138486aa4729971494878d33b1a24e39571f748a6e16c5955b3d877d3a6aaa0e955166474af5d32c410f439a2234137ad1bb085fd4e8813c958f11d97"
    const val CHALLENGE_HEX = "1588d5a30eabc6f8fc9a951b11f6fd11"
    const val SESSION_INFO_HEX =
        "0806124104c7a1f47138486aa4729971494878d33b1a24e39571f748a6e16c5955b3d877d3a6aaa0e955166474af5d32c410f439a2234137ad1bb085fd4e8813c958f11d971a104c463f9cc0d3d26906e982ed224adde6255a0a0000"
    const val SESSION_INFO_TAG_HEX = "996c1fe38331be138f8039c194b14db2198846ed7d8251e6749284d7b32ea002"
    const val HVAC_METADATA_HEX =
        "000105010103021135594a333031323334353637383941424303104c463f9cc0d3d26906e982ed224adde6040400000a5f050400000007070400000002ff"
}
