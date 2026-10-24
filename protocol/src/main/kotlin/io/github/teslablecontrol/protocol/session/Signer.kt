package io.github.teslablecontrol.protocol.session

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import io.github.teslablecontrol.protocol.Clock
import io.github.teslablecontrol.protocol.crypto.EcdhKey
import io.github.teslablecontrol.protocol.crypto.Metadata
import io.github.teslablecontrol.protocol.crypto.SessionCrypto
import io.github.teslablecontrol.protocol.toByteString
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.min
import kotlin.time.Duration

/** A protocol-level failure inside the signer (port of `authentication.Error`). */
class SignerException(
    val fault: UniversalMessage.MessageFault_E,
    message: String,
    cause: Throwable? = null,
) : Exception("${fault.name}: $message", cause)

/**
 * Authorizes commands for one vehicle domain and decrypts its responses (port of
 * `internal/authentication/signer.go` and the client half of `peer.go`).
 *
 * Not thread-safe: callers serialize access (see `DomainSession`).
 */
class Signer private constructor(
    private val crypto: SessionCrypto,
    private val verifierName: ByteArray,
    vehiclePublicKey: ByteArray,
    private val clock: Clock,
) {
    private val verifierPublicKey = vehiclePublicKey.copyOf()
    private val epoch = ByteArray(EPOCH_ID_LENGTH)
    private var counter: UInt = 0u
    /** Monotonic time at which the vehicle's epoch clock read zero. */
    private var timeZeroNanos: Long = 0
    /** Vehicle clock time of the newest authenticated session info. */
    private var setTime: UInt = 0u

    val currentCounter: UInt get() = counter

    fun remotePublicKey(): ByteArray = verifierPublicKey.copyOf()

    private fun timestamp(): UInt =
        ((clock.monotonicNanos() - timeZeroNanos).coerceAtLeast(0) / NANOS_PER_SECOND).toUInt()

    private fun setEpochStart(clockTime: UInt) {
        timeZeroNanos = clock.monotonicNanos() - clockTime.toLong() * NANOS_PER_SECOND
    }

    /** Session state for caching (`ExportSessionInfo`). */
    fun exportSessionInfo(): ByteArray = Signatures.SessionInfo.newBuilder()
        .setCounter(counter.toInt())
        .setPublicKey(verifierPublicKey.toByteString())
        .setEpoch(epoch.toByteString())
        .setClockTime(timestamp().toInt())
        .build()
        .toByteArray()

    /** Resynchronizes with session info from the vehicle (`UpdateSessionInfo`). */
    fun updateSessionInfo(info: Signatures.SessionInfo) {
        if (!info.publicKey.toByteArray().contentEquals(verifierPublicKey)) {
            throw SignerException(
                UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID,
                "public key in SessionInfo doesn't match value used to initialize Signer",
            )
        }
        val infoEpoch = info.epoch.toByteArray()
        val infoClock = info.clockTime.toUInt()
        if (!epoch.contentEquals(infoEpoch) || setTime <= infoClock) {
            // Never roll the anti-replay counter back.
            val infoCounter = info.counter.toUInt()
            if (counter < infoCounter) counter = infoCounter
            infoEpoch.copyInto(epoch, endIndex = min(infoEpoch.size, EPOCH_ID_LENGTH))
            setTime = infoClock
            setEpochStart(infoClock)
        }
    }

    /** `UpdateSignedSessionInfo`: verifies the HMAC tag before resynchronizing. */
    fun updateSignedSessionInfo(challenge: ByteArray, encodedInfo: ByteArray, tag: ByteArray) {
        verifyTag(challenge, encodedInfo, tag)
        updateSessionInfo(parseSessionInfo(encodedInfo))
    }

    private fun verifyTag(challenge: ByteArray, encodedInfo: ByteArray, tag: ByteArray) {
        val expected = crypto.sessionInfoHmac(verifierName, challenge, encodedInfo)
        if (!MessageDigest.isEqual(expected, tag)) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_SIGNATURE, "session info hmac invalid")
        }
    }

    /** Encrypts [message]'s payload in place with AES-GCM and attaches authenticated metadata (`Encrypt`). */
    fun encrypt(message: UniversalMessage.RoutableMessage.Builder, expiresIn: Duration) {
        if (counter == UInt.MAX_VALUE) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_TOKEN_OR_COUNTER, "counter rollover")
        }
        counter++
        encryptWithCounter(message, expiresIn, counter)
    }

    private fun encryptWithCounter(message: UniversalMessage.RoutableMessage.Builder, expiresIn: Duration, counter: UInt) {
        val expiresAtSeconds =
            (clock.monotonicNanos() + expiresIn.inWholeNanoseconds - timeZeroNanos).coerceAtLeast(0) / NANOS_PER_SECOND
        val expiresAt = expiresAtSeconds.toUInt()
        val meta = Metadata()
        extractMetadata(
            meta, message.toDestination, message.flags, expiresAt, counter,
            Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_PERSONALIZED,
        )
        if (message.payloadCase != UniversalMessage.RoutableMessage.PayloadCase.PROTOBUF_MESSAGE_AS_BYTES) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BAD_PARAMETER, "Missing protobuf message")
        }
        val sealed = crypto.encrypt(message.protobufMessageAsBytes.toByteArray(), meta.sha256())
        val gcmData = Signatures.AES_GCM_Personalized_Signature_Data.newBuilder()
            .setEpoch(epoch.toByteString())
            .setNonce(sealed.nonce.toByteString())
            .setCounter(counter.toInt())
            .setExpiresAt(expiresAt.toInt())
            .setTag(sealed.tag.toByteString())
        message.setSignatureData(
            Signatures.SignatureData.newBuilder()
                .setSignerIdentity(Signatures.KeyIdentity.newBuilder().setPublicKey(crypto.localPublicKey.toByteString()))
                .setAESGCMPersonalizedData(gcmData),
        )
        message.setProtobufMessageAsBytes(sealed.ciphertext.toByteString())
    }

    private fun extractMetadata(
        meta: Metadata,
        destination: UniversalMessage.Destination,
        flags: Int,
        expiresAt: UInt,
        counter: UInt,
        method: Signatures.SignatureType,
    ) {
        meta.addByte(Signatures.Tag.TAG_SIGNATURE_TYPE, method.number)
        // Authenticate the domain from the message itself, as the official signer does.
        if (destination.subDestinationCase != UniversalMessage.Destination.SubDestinationCase.DOMAIN) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_DOMAINS, "domain missing")
        }
        val domain = destination.domainValue
        if (domain < 0 || domain > 255) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_DOMAINS, "domain out of range")
        }
        meta.addByte(Signatures.Tag.TAG_DOMAIN, domain)
        if (verifierName.size > 255) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_WRONG_PERSONALIZATION, "recipient name too long")
        }
        meta.add(Signatures.Tag.TAG_PERSONALIZATION, verifierName)
        if (expiresAt.toLong() > EPOCH_LENGTH_SECONDS) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BAD_PARAMETER, "out of bounds expiration time")
        }
        meta.add(Signatures.Tag.TAG_EPOCH, epoch)
        meta.addUint32(Signatures.Tag.TAG_EXPIRES_AT, expiresAt.toInt())
        meta.addUint32(Signatures.Tag.TAG_COUNTER, counter.toInt())
        // Flags are only authenticated when set, for backwards compatibility.
        if (flags != 0) meta.addUint32(Signatures.Tag.TAG_FLAGS, flags)
    }

    /**
     * Decrypts a vehicle response (`Decrypt`). Returns the message with a plaintext payload and the
     * response counter, which the caller must check against its anti-replay window.
     */
    fun decrypt(message: UniversalMessage.RoutableMessage, requestId: ByteArray?): Pair<UniversalMessage.RoutableMessage, UInt> {
        val signature = message.signatureData
        if (signature.sigTypeCase != Signatures.SignatureData.SigTypeCase.AES_GCM_RESPONSE_DATA) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BAD_PARAMETER, "missing AES-GCM data")
        }
        val gcm = signature.getAESGCMResponseData()
        val associatedData = responseMetadata(message, requestId, gcm.counter)
        val plaintext = try {
            crypto.decrypt(gcm.nonce.toByteArray(), message.protobufMessageAsBytes.toByteArray(), associatedData, gcm.tag.toByteArray())
        } catch (e: GeneralSecurityException) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_SIGNATURE, "response failed authentication", e)
        }
        val decrypted = message.toBuilder()
            .setProtobufMessageAsBytes(ByteString.copyFrom(plaintext))
            .clearSubSigData()
            .build()
        return decrypted to gcm.counter.toUInt()
    }

    private fun responseMetadata(message: UniversalMessage.RoutableMessage, requestId: ByteArray?, counter: Int): ByteArray =
        Metadata()
            .addByte(Signatures.Tag.TAG_SIGNATURE_TYPE, Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_RESPONSE.number)
            .addByte(Signatures.Tag.TAG_DOMAIN, message.fromDestination.domainValue)
            .add(Signatures.Tag.TAG_PERSONALIZATION, verifierName)
            .addUint32(Signatures.Tag.TAG_COUNTER, counter)
            .addUint32(Signatures.Tag.TAG_FLAGS, message.flags)
            .add(Signatures.Tag.TAG_REQUEST_HASH, requestId)
            .addUint32(Signatures.Tag.TAG_FAULT, message.signedMessageStatus.signedMessageFaultValue)
            .sha256()

    companion object {
        const val EPOCH_ID_LENGTH = 16
        private const val EPOCH_LENGTH_SECONDS = 1L shl 30
        private const val NANOS_PER_SECOND = 1_000_000_000L

        private fun parseSessionInfo(encodedInfo: ByteArray): Signatures.SessionInfo = try {
            Signatures.SessionInfo.parseFrom(encodedInfo)
        } catch (e: InvalidProtocolBufferException) {
            throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_DECODING, "invalid session info protobuf", e)
        }

        /** `NewSigner`. */
        fun create(
            key: EcdhKey,
            verifierName: ByteArray,
            info: Signatures.SessionInfo,
            clock: Clock,
            random: SecureRandom,
        ): Signer {
            if (verifierName.size > 255) {
                throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_WRONG_PERSONALIZATION, "verifier name too long")
            }
            val vehicleKey = info.publicKey.toByteArray()
            val crypto = try {
                SessionCrypto.create(key, vehicleKey, random)
            } catch (e: Exception) {
                throw SignerException(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BAD_PARAMETER, "invalid public key", e)
            }
            return Signer(crypto, verifierName.copyOf(), vehicleKey, clock).apply {
                counter = info.counter.toUInt()
                setTime = info.clockTime.toUInt()
                setEpochStart(setTime)
                val infoEpoch = info.epoch.toByteArray()
                infoEpoch.copyInto(epoch, endIndex = min(infoEpoch.size, EPOCH_ID_LENGTH))
            }
        }

        /** `ImportSessionInfo`: restores a cached session that was exported [ageNanos] ago. */
        fun import(
            key: EcdhKey,
            verifierName: ByteArray,
            encodedInfo: ByteArray,
            ageNanos: Long,
            clock: Clock,
            random: SecureRandom,
        ): Signer {
            val info = parseSessionInfo(encodedInfo)
            return create(key, verifierName, info, clock, random).apply {
                timeZeroNanos = clock.monotonicNanos() - ageNanos.coerceAtLeast(0) - info.clockTime.toUInt().toLong() * NANOS_PER_SECOND
            }
        }

        /** `NewAuthenticatedSigner`: builds a signer from a handshake reply after verifying its tag. */
        fun authenticated(
            key: EcdhKey,
            verifierName: ByteArray,
            challenge: ByteArray,
            encodedInfo: ByteArray,
            tag: ByteArray,
            clock: Clock,
            random: SecureRandom,
        ): Signer = import(key, verifierName, encodedInfo, 0, clock, random).also {
            it.verifyTag(challenge, encodedInfo, tag)
        }

        /** `RequestID`: identifies a request in the metadata of its encrypted response. */
        fun requestId(message: UniversalMessage.RoutableMessageOrBuilder): ByteArray? {
            if (!message.hasSignatureData()) return null
            val signature = message.signatureData
            return when (signature.sigTypeCase) {
                Signatures.SignatureData.SigTypeCase.AES_GCM_PERSONALIZED_DATA ->
                    byteArrayOf(Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_PERSONALIZED.number.toByte()) +
                        signature.getAESGCMPersonalizedData().tag.toByteArray()
                Signatures.SignatureData.SigTypeCase.HMAC_PERSONALIZED_DATA -> {
                    var tag = signature.getHMACPersonalizedData().tag.toByteArray()
                    if (message.toDestination.domain == UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY) {
                        tag = tag.copyOf(min(tag.size, 16))
                    }
                    byteArrayOf(Signatures.SignatureType.SIGNATURE_TYPE_HMAC_PERSONALIZED.number.toByte()) + tag
                }
                else -> null
            }
        }
    }
}
