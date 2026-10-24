package io.github.teslablecontrol.protocol.dispatch

import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import io.github.teslablecontrol.protocol.AuthMethod
import io.github.teslablecontrol.protocol.Clock
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.crypto.EcdhKey
import io.github.teslablecontrol.protocol.session.Signer
import kotlinx.coroutines.CompletableDeferred
import java.security.SecureRandom
import kotlin.time.Duration

/** Authenticated session state for one domain (port of `internal/dispatcher/session.go`). */
internal class DomainSession(val domain: Domain) {
    private var signer: Signer? = null

    @Volatile
    var isReady = false
        private set

    /** Completes once the session can authorize commands. */
    val readySignal = CompletableDeferred<Unit>()

    val hasSigner: Boolean
        @Synchronized get() = signer != null

    /** Verifies session info from the vehicle and creates or resynchronizes the signer (`processHello`). */
    @Synchronized
    fun processHello(
        key: EcdhKey,
        vin: ByteArray,
        challenge: ByteArray,
        encodedInfo: ByteArray,
        tag: ByteArray,
        clock: Clock,
        random: SecureRandom,
    ) {
        val current = signer
        if (current == null) {
            signer = Signer.authenticated(key, vin, challenge, encodedInfo, tag, clock, random)
        } else {
            current.updateSignedSessionInfo(challenge, encodedInfo, tag)
        }
        markReady()
    }

    @Synchronized
    fun loadFromCache(cached: Signer) {
        signer = cached
        markReady()
    }

    private fun markReady() {
        if (!isReady) {
            isReady = true
            readySignal.complete(Unit)
        }
    }

    @Synchronized
    fun authorize(message: RoutableMessage.Builder, method: AuthMethod, lifetime: Duration) {
        when (method) {
            AuthMethod.NONE -> Unit
            AuthMethod.GCM -> (signer ?: throw VehicleErrors.noSession()).encrypt(message, lifetime)
        }
    }

    /** Decrypts a reply and enforces its anti-replay window. */
    @Synchronized
    fun decrypt(message: RoutableMessage, receiver: Receiver): RoutableMessage {
        val current = signer ?: throw VehicleErrors.noDecryptionContext()
        val (plaintext, counter) = current.decrypt(message, receiver.requestId)
        if (!receiver.antiReplay.update(counter)) throw VehicleErrors.replayedResponse()
        return plaintext
    }

    @Synchronized
    fun export(): ByteArray? = signer?.exportSessionInfo()
}
