package io.github.teslablecontrol.protocol.crypto

import io.github.teslablecontrol.protocol.leftPad
import io.github.teslablecontrol.protocol.toFixedBytes
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPrivateKeySpec
import javax.crypto.KeyAgreement

/**
 * The client's long-term P-256 key. Mirrors the official library's `ECDHPrivateKey`, which is an
 * interface so the private key can live in protected storage.
 */
interface EcdhKey {
    /** Uncompressed public key (65 bytes). */
    val publicKey: ByteArray

    /** ECDH shared secret: the 32-byte big-endian x-coordinate of `d·Q` for the peer's point `Q`. */
    fun sharedSecret(peerPublicKey: ByteArray): ByteArray
}

/** A P-256 key held in memory; ECDH runs through the platform JCA provider. */
class SoftwareEcdhKey private constructor(
    private val d: BigInteger,
    private val publicKeyBytes: ByteArray,
) : EcdhKey {

    override val publicKey: ByteArray get() = publicKeyBytes.copyOf()

    private val jcaPrivateKey: PrivateKey by lazy {
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(d, P256.parameterSpec))
    }

    override fun sharedSecret(peerPublicKey: ByteArray): ByteArray {
        val peer = P256.toJcaPublicKey(peerPublicKey)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(jcaPrivateKey)
        agreement.doPhase(peer, true)
        return agreement.generateSecret().leftPad(P256.FIELD_SIZE)
    }

    /** The 32-byte private scalar, for encrypted storage or export. */
    fun privateScalar(): ByteArray = d.toFixedBytes(P256.FIELD_SIZE)

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): SoftwareEcdhKey {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"), random)
            val pair = generator.generateKeyPair()
            val private = pair.private as ECPrivateKey
            val public = pair.public as ECPublicKey
            return SoftwareEcdhKey(private.s, P256.encode(P256.Point(public.w.affineX, public.w.affineY)))
        }

        /** Rebuilds a key from its 32-byte private scalar, deriving the public key. */
        fun fromPrivateScalar(scalar: ByteArray): SoftwareEcdhKey {
            require(scalar.size == P256.FIELD_SIZE) { "private scalar must be 32 bytes" }
            val d = BigInteger(1, scalar)
            require(d.signum() > 0 && d < P256.N) { "invalid P-256 private key" }
            return SoftwareEcdhKey(d, P256.encode(P256.multiplyGenerator(d)))
        }
    }
}
