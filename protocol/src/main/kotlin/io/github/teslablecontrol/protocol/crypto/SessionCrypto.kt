package io.github.teslablecontrol.protocol.crypto

import com.tesla.generated.signatures.Signatures
import io.github.teslablecontrol.protocol.bytes
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric operations on the key shared with one vehicle domain (port of
 * `internal/authentication/native.go` `NativeSession`): `K = SHA1(ECDH_x)[:16]`.
 */
class SessionCrypto(
    sharedSecret: ByteArray,
    localPublicKey: ByteArray,
    private val random: SecureRandom,
) {
    // SHA1 matches the vehicle; it only maps a pseudo-random curve point to a bit string.
    internal val key: ByteArray = MessageDigest.getInstance("SHA-1").digest(sharedSecret).copyOf(SHARED_KEY_SIZE)

    val localPublicKey: ByteArray = localPublicKey.copyOf()

    /** `HMAC-SHA256(K, label)`, the KDF used for the session-info and HMAC-command subkeys. */
    fun subkey(label: String): ByteArray = hmacSha256(key, label.toByteArray(Charsets.US_ASCII))

    /** The tag a vehicle attaches to session info answering a handshake with [challenge]. */
    fun sessionInfoHmac(verifierName: ByteArray, challenge: ByteArray, encodedInfo: ByteArray): ByteArray {
        val meta = Metadata()
            .addByte(Signatures.Tag.TAG_SIGNATURE_TYPE, Signatures.SignatureType.SIGNATURE_TYPE_HMAC.number)
            .add(Signatures.Tag.TAG_PERSONALIZATION, verifierName)
            .add(Signatures.Tag.TAG_CHALLENGE, challenge)
        return hmacSha256(subkey(LABEL_SESSION_INFO), meta.serialize() + encodedInfo)
    }

    class Sealed(val nonce: ByteArray, val ciphertext: ByteArray, val tag: ByteArray)

    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): Sealed {
        val nonce = random.bytes(NONCE_SIZE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext)
        val split = sealed.size - TAG_BITS / 8
        return Sealed(nonce, sealed.copyOfRange(0, split), sealed.copyOfRange(split, sealed.size))
    }

    /** Authenticates and decrypts; throws [javax.crypto.AEADBadTagException] if anything was tampered with. */
    fun decrypt(nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray, tag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext + tag)
    }

    companion object {
        const val SHARED_KEY_SIZE = 16
        const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
        const val LABEL_SESSION_INFO = "session info"
        const val LABEL_MESSAGE_AUTH = "authenticated command"

        fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }

        fun create(key: EcdhKey, peerPublicKey: ByteArray, random: SecureRandom): SessionCrypto =
            SessionCrypto(key.sharedSecret(peerPublicKey), key.publicKey, random)
    }
}
