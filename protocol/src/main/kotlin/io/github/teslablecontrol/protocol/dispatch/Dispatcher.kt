package io.github.teslablecontrol.protocol.dispatch

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import io.github.teslablecontrol.protocol.AuthMethod
import io.github.teslablecontrol.protocol.Clock
import io.github.teslablecontrol.protocol.CommandException
import io.github.teslablecontrol.protocol.Deadline
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.ErrorKind
import io.github.teslablecontrol.protocol.ProtocolConfig
import io.github.teslablecontrol.protocol.ProtocolLog
import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.VehicleException
import io.github.teslablecontrol.protocol.bytes
import io.github.teslablecontrol.protocol.crypto.EcdhKey
import io.github.teslablecontrol.protocol.debug
import io.github.teslablecontrol.protocol.info
import io.github.teslablecontrol.protocol.protocolError
import io.github.teslablecontrol.protocol.session.Signer
import io.github.teslablecontrol.protocol.session.SignerException
import io.github.teslablecontrol.protocol.toByteString
import io.github.teslablecontrol.protocol.toHex
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.transport.TransportException
import io.github.teslablecontrol.protocol.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import kotlin.time.Duration

/** Session state that lets a later connection skip the handshake. */
class SessionCacheEntry(val domain: Domain, val sessionInfo: ByteArray, val createdAtMillis: Long)

/**
 * Sends (encrypted) messages to a vehicle and routes replies to the request that caused them
 * (port of `internal/dispatcher/dispatcher.go`).
 */
class Dispatcher(
    private val transport: Transport,
    private val privateKey: EcdhKey?,
    vin: String,
    private val config: ProtocolConfig = ProtocolConfig(),
    private val clock: Clock = Clock.SYSTEM,
    private val log: ProtocolLog = ProtocolLog.NONE,
    private val random: SecureRandom = SecureRandom(),
) {
    private val vinBytes = vin.toByteArray(Charsets.UTF_8)
    private val connectionAddress: ByteString = random.bytes(ADDRESS_LENGTH).toByteString()

    private val lock = Any()
    private val sessions = HashMap<Domain, DomainSession>()
    private val handlers = HashMap<ReceiverKey, Receiver>()
    private var started = false
    private var stopped = false
    private var readerJob: Job? = null
    private val closed = CompletableDeferred<Unit>()

    val isConnected: Boolean get() = synchronized(lock) { started && !stopped }

    /** Suspends until the link is closed (by either side). */
    suspend fun awaitClosed() = closed.await()

    /** Starts routing incoming messages. A dispatcher serves exactly one connection. */
    fun start(scope: CoroutineScope) {
        synchronized(lock) {
            check(!started) { "dispatcher already started" }
            started = true
        }
        readerJob = scope.launch(CoroutineName("tesla-ble-dispatcher")) {
            try {
                for (frame in transport.incoming) handleFrame(frame)
                log.info { "Vehicle link closed" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn { "Dispatcher stopped: $e" }
            } finally {
                shutdown()
            }
        }
    }

    /** Stops routing; pending requests fail immediately instead of waiting for their timeouts. */
    fun stop() {
        readerJob?.cancel()
        shutdown()
    }

    private fun shutdown() {
        val pending = synchronized(lock) {
            stopped = true
            handlers.values.toList().also { handlers.clear() }
        }
        pending.forEach { it.fail(VehicleErrors.connectionLost()) }
        closed.complete(Unit)
    }

    fun hasSession(domain: Domain): Boolean = synchronized(lock) { sessions[domain] }?.isReady == true

    private fun handleFrame(frame: ByteArray) {
        val message = try {
            RoutableMessage.parseFrom(frame)
        } catch (e: InvalidProtocolBufferException) {
            log.warn { "Dropping unparseable message (${frame.size} bytes)" }
            return
        }
        log.debug { "RX ${describe(message)}" }
        try {
            process(message)
        } catch (e: Exception) {
            // A malformed reply must never take the connection down.
            log.warn { "Dropping message after processing error: $e" }
        }
    }

    private fun process(message: RoutableMessage) {
        if (!message.hasFromDestination()) {
            log.warn { "Dropping message with missing source" }
            return
        }
        val domain = message.fromDestination.domainValue
        val requestUuid = message.requestUuid
        if (requestUuid.size() != UUID_LENGTH && requestUuid.size() != 0) {
            log.warn { "Dropping message with invalid request UUID length" }
            return
        }
        val uuidKey = if (domain == Domain.VCSEC.number) ByteString.EMPTY else requestUuid
        if (!message.hasToDestination()) {
            log.warn { "Dropping message with missing destination" }
            return
        }
        when (message.toDestination.subDestinationCase) {
            UniversalMessage.Destination.SubDestinationCase.ROUTING_ADDRESS -> Unit
            else -> {
                log.debug { "Dropping message not addressed to this client" }
                return
            }
        }
        val address = message.toDestination.routingAddress
        if (address.size() != ADDRESS_LENGTH) {
            log.warn { "Dropping message with invalid address length" }
            return
        }
        val handler = synchronized(lock) { handlers[ReceiverKey(address, uuidKey, domain)] }
        if (handler == null) {
            log.warn { "Dropping message without registered handler" }
            return
        }

        // Vehicles may volunteer session info when they suspect a desync, typically alongside an
        // error; the reply still goes to the handler afterwards.
        checkForSessionUpdate(message, handler)

        val delivered = try {
            decrypt(message, handler)
        } catch (e: CommandException) {
            if (e.kind == ErrorKind.REPLAYED_RESPONSE) {
                log.info { "Dropping duplicate vehicle response" }
            } else {
                log.warn { "Error decrypting vehicle response: ${e.message}" }
            }
            return
        } catch (e: SignerException) {
            log.warn { "Error decrypting vehicle response: ${e.message}" }
            return
        }

        if (handler.channel.trySend(delivered).isFailure) {
            log.warn { "Dropping response because the handler queue is full or closed" }
        }
    }

    private fun checkForSessionUpdate(message: RoutableMessage, handler: Receiver) {
        if (message.payloadCase != RoutableMessage.PayloadCase.SESSION_INFO) return
        val key = privateKey
        if (key == null) {
            log.warn { "Discarding session info because client does not have a private key" }
            return
        }
        if (handler.isExpired(clock.monotonicNanos(), config.maxLatency)) {
            log.warn { "Discarding session info received more than ${config.maxLatency} after request" }
            return
        }
        val signature = message.signatureData
        if (!message.hasSignatureData() ||
            signature.sigTypeCase != Signatures.SignatureData.SigTypeCase.SESSION_INFO_TAG ||
            signature.sessionInfoTag.tag.isEmpty
        ) {
            log.warn { "Discarding unauthenticated session info" }
            return
        }
        val domain = Domain.fromNumber(handler.key.domain) ?: return
        val session = synchronized(lock) { sessions[domain] }
        if (session == null) {
            log.warn { "Dropping session info from unregistered domain $domain" }
            return
        }
        try {
            session.processHello(
                key, vinBytes, message.requestUuid.toByteArray(), message.sessionInfo.toByteArray(),
                signature.sessionInfoTag.tag.toByteArray(), clock, random,
            )
            log.info { "Updated session info for $domain" }
        } catch (e: Exception) {
            log.warn { "Session info error: ${e.message}" }
        }
    }

    private fun decrypt(message: RoutableMessage, handler: Receiver): RoutableMessage {
        if (!message.hasSignatureData() ||
            message.signatureData.sigTypeCase != Signatures.SignatureData.SigTypeCase.AES_GCM_RESPONSE_DATA
        ) {
            return message // Plaintext reply.
        }
        val domain = Domain.fromNumber(handler.key.domain) ?: throw VehicleErrors.noDecryptionContext()
        val session = synchronized(lock) { sessions[domain] } ?: throw VehicleErrors.noDecryptionContext()
        return session.decrypt(message, handler)
    }

    /**
     * Sends [message] (which must name its destination domain) and returns a [Receiver] for the
     * replies. The caller must close the receiver. [lifetime] bounds the command's validity.
     */
    suspend fun send(message: RoutableMessage.Builder, auth: AuthMethod, lifetime: Duration): Receiver {
        if (!isConnected) throw VehicleErrors.notConnected()
        val destination = message.toDestination
        val domain = if (destination.subDestinationCase == UniversalMessage.Destination.SubDestinationCase.DOMAIN) {
            Domain.fromNumber(destination.domainValue)
        } else {
            null
        } ?: throw VehicleErrors.protocol("cannot send message without a destination domain")

        val uuid = random.bytes(UUID_LENGTH).toByteString()
        val address = if (domain == Domain.VCSEC) random.bytes(ADDRESS_LENGTH).toByteString() else connectionAddress
        val key = ReceiverKey(address, if (domain == Domain.VCSEC) ByteString.EMPTY else uuid, domain.number)
        message.setUuid(uuid)
        message.setFromDestination(UniversalMessage.Destination.newBuilder().setRoutingAddress(address))

        if (auth != AuthMethod.NONE) {
            val session = synchronized(lock) { sessions[domain] }
            if (session == null || !session.isReady) {
                log.warn { "No session available for $domain" }
                throw VehicleErrors.noSession()
            }
            try {
                session.authorize(message, auth, lifetime.coerceAtMost(config.maxCommandLifetime))
            } catch (e: SignerException) {
                throw VehicleErrors.protocol("could not authorize command: ${e.message}", e)
            }
        }

        val receiver = Receiver(key, Signer.requestId(message), clock.monotonicNanos(), config.receiverBufferSize, ::closeHandler)
        synchronized(lock) {
            if (stopped) throw VehicleErrors.notConnected()
            handlers[key] = receiver
        }
        val encoded = message.build()
        log.debug { "TX ${describe(encoded)}" }
        try {
            transport.send(encoded.toByteArray())
        } catch (e: CancellationException) {
            receiver.close()
            throw e
        } catch (e: VehicleException) {
            receiver.close()
            throw e
        } catch (e: Exception) {
            receiver.close()
            throw TransportException("failed to send to vehicle: ${e.message}", e)
        }
        return receiver
    }

    private fun closeHandler(receiver: Receiver) {
        synchronized(lock) {
            if (handlers[receiver.key] === receiver) handlers.remove(receiver.key)
        }
        receiver.channel.close()
    }

    /**
     * Performs the handshake with [domain] (`StartSession`). Returns at once if a session (handshake
     * or cache) already exists; invalid cached sessions are repaired by the vehicle's error replies.
     */
    suspend fun startSession(domain: Domain, deadline: Deadline) {
        val session = synchronized(lock) { sessions.getOrPut(domain) { DomainSession(domain) } }
        if (session.hasSigner) {
            log.info { "Session for $domain already established" }
            return
        }
        while (tryStartSession(session, domain, deadline)) {
            // Retry until the deadline.
        }
    }

    /** Handshakes with several domains concurrently; the first failure cancels the rest. */
    suspend fun startSessions(domains: Collection<Domain>, deadline: Deadline) = coroutineScope {
        domains.map { async { startSession(it, deadline) } }.awaitAll()
        Unit
    }

    private object Ready

    /** One handshake attempt; returns true if it should be retried. */
    private suspend fun tryStartSession(session: DomainSession, domain: Domain, deadline: Deadline): Boolean {
        val key = privateKey ?: throw VehicleErrors.requiresKey()
        log.info { "Requesting session info from $domain" }
        val receiver = send(sessionInfoRequest(domain, key.publicKey), AuthMethod.NONE, config.defaultCommandLifetime)
        try {
            val first = withTimeoutOrNull(minOf(config.retryInterval, deadline.remaining())) {
                select<Any> {
                    session.readySignal.onAwait { Ready }
                    receiver.channel.onReceive { it }
                }
            }
            when (first) {
                null -> {
                    if (deadline.isExpired) throw VehicleErrors.deadlineExceeded()
                    return true
                }
                Ready -> return false
                is RoutableMessage -> first.protocolError()?.let { throw it }
            }
            // The reply was processed before delivery, which normally completed readySignal; the
            // wait only matters for malformed replies.
            val ready = withTimeoutOrNull(minOf(config.retryInterval, deadline.remaining())) { session.readySignal.await() }
            if (ready != null) return false
            if (deadline.isExpired) throw VehicleErrors.deadlineExceeded()
            return true
        } finally {
            receiver.close()
        }
    }

    /** Session state for every established domain. */
    fun exportCache(): List<SessionCacheEntry> {
        val now = clock.wallMillis()
        val snapshot = synchronized(lock) { sessions.toMap() }
        return snapshot.mapNotNull { (domain, session) -> session.export()?.let { SessionCacheEntry(domain, it, now) } }
    }

    /** Replaces all sessions with cached ones (`LoadCache`). */
    fun loadCache(entries: List<SessionCacheEntry>) {
        val key = privateKey ?: throw VehicleErrors.requiresKey()
        val loaded = HashMap<Domain, DomainSession>()
        val now = clock.wallMillis()
        for (entry in entries) {
            val signer = try {
                Signer.import(key, vinBytes, entry.sessionInfo, (now - entry.createdAtMillis) * 1_000_000, clock, random)
            } catch (e: SignerException) {
                throw VehicleErrors.protocol("invalid session cache: ${e.message}", e)
            }
            loaded[entry.domain] = DomainSession(entry.domain).apply { loadFromCache(signer) }
        }
        synchronized(lock) {
            sessions.clear()
            sessions.putAll(loaded)
        }
    }

    companion object {
        const val UUID_LENGTH = 16
        const val ADDRESS_LENGTH = 16

        fun sessionInfoRequest(domain: Domain, publicKey: ByteArray): RoutableMessage.Builder =
            RoutableMessage.newBuilder()
                .setToDestination(UniversalMessage.Destination.newBuilder().setDomain(domain.proto))
                .setSessionInfoRequest(UniversalMessage.SessionInfoRequest.newBuilder().setPublicKey(publicKey.toByteString()))

        /** One-line summary for the diagnostics log (payloads stay opaque). */
        fun describe(message: RoutableMessage): String = buildString {
            val to = message.toDestination
            append(if (to.subDestinationCase == UniversalMessage.Destination.SubDestinationCase.DOMAIN) to.domain.name else "client")
            append(" payload=").append(message.payloadCase.name)
            if (message.hasSignatureData()) append(" sig=").append(message.signatureData.sigTypeCase.name)
            if (message.hasSignedMessageStatus()) {
                append(" status=").append(message.signedMessageStatus.operationStatus.name)
                    .append('/').append(message.signedMessageStatus.signedMessageFault.name)
            }
            if (!message.uuid.isEmpty) append(" uuid=").append(message.uuid.toHex().take(8))
            if (!message.requestUuid.isEmpty) append(" req=").append(message.requestUuid.toHex().take(8))
        }
    }
}
