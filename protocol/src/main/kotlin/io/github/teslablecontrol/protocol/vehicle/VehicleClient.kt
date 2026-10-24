package io.github.teslablecontrol.protocol.vehicle

import com.google.protobuf.InvalidProtocolBufferException
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.errors.Errors
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.AuthMethod
import io.github.teslablecontrol.protocol.Clock
import io.github.teslablecontrol.protocol.CommandException
import io.github.teslablecontrol.protocol.Deadline
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.ErrorKind
import io.github.teslablecontrol.protocol.KeychainException
import io.github.teslablecontrol.protocol.ProtocolConfig
import io.github.teslablecontrol.protocol.ProtocolLog
import io.github.teslablecontrol.protocol.VehicleErrors
import io.github.teslablecontrol.protocol.VehicleException
import io.github.teslablecontrol.protocol.VehicleRefusedException
import io.github.teslablecontrol.protocol.crypto.EcdhKey
import io.github.teslablecontrol.protocol.debug
import io.github.teslablecontrol.protocol.dispatch.Dispatcher
import io.github.teslablecontrol.protocol.dispatch.Receiver
import io.github.teslablecontrol.protocol.dispatch.SessionCacheEntry
import io.github.teslablecontrol.protocol.protocolError
import io.github.teslablecontrol.protocol.shouldRetry
import io.github.teslablecontrol.protocol.toByteString
import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.transport.TransportException
import io.github.teslablecontrol.protocol.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.time.Duration

/**
 * One connection to a vehicle (port of `pkg/vehicle.Vehicle`). Create a new client for each link.
 */
class VehicleClient(
    val vin: String,
    private val transport: Transport,
    val privateKey: EcdhKey?,
    private val config: ProtocolConfig = ProtocolConfig(),
    private val clock: Clock = Clock.SYSTEM,
    private val log: ProtocolLog = ProtocolLog.NONE,
    random: SecureRandom = SecureRandom(),
) {
    private val dispatcher = Dispatcher(transport, privateKey, vin, config, clock, log, random)

    // VCSEC can't reliably tell simultaneous replies apart, so its requests go one at a time.
    private val vcsecLock = Mutex()

    /** Request flags; always asks vehicles (2024.38+) to encrypt their responses. */
    var flags: Int = 1 shl UniversalMessage.Flags.FLAG_ENCRYPT_RESPONSE_VALUE

    val isConnected: Boolean get() = dispatcher.isConnected

    /** Suspends until the link to the vehicle closes. */
    suspend fun awaitDisconnected() = dispatcher.awaitClosed()

    /** Starts listening for vehicle messages in [scope]. */
    fun connect(scope: CoroutineScope) = dispatcher.start(scope)

    /** Closes the link; pending requests fail at once. Safe to call repeatedly. */
    fun disconnect() {
        dispatcher.stop()
        transport.close()
    }

    fun hasSession(domain: Domain): Boolean = dispatcher.hasSession(domain)

    /** Handshakes with [domain] unless a session (possibly cached) already exists. Fails if the key isn't paired. */
    suspend fun startSession(domain: Domain, timeout: Duration) {
        val deadline = Deadline.after(timeout, clock)
        if (domain == Domain.VCSEC) {
            vcsecLock.withLock { dispatcher.startSession(domain, deadline) }
        } else {
            dispatcher.startSession(domain, deadline)
        }
    }

    fun exportSessionCache(): List<SessionCacheEntry> = dispatcher.exportCache()

    fun loadSessionCache(entries: List<SessionCacheEntry>) = dispatcher.loadCache(entries)

    /** Executes [request], retrying transient failures until [timeout]. */
    suspend fun execute(request: VehicleRequest, timeout: Duration): CommandResult {
        val deadline = Deadline.after(timeout, clock)
        return when (request) {
            is VehicleRequest.CarServerAction -> {
                try {
                    getCarServerResponse(request.action, deadline)
                } catch (e: VehicleRefusedException) {
                    val suffix = request.successIfErrorEndsWith
                    if (suffix == null || !e.reason.endsWith(suffix)) throw e
                }
                CommandResult.Done
            }
            is VehicleRequest.State ->
                CommandResult.State(getCarServerResponse(request.action, deadline).vehicleData)
            is VehicleRequest.Rke, is VehicleRequest.Closure -> {
                vcsecResult(request.payload(), AuthMethod.GCM, deadline, ::isCommandComplete)
                CommandResult.Done
            }
            is VehicleRequest.Whitelist -> {
                vcsecResult(request.payload(), AuthMethod.GCM, deadline, ::isWhitelistOperationComplete)
                CommandResult.Done
            }
            VehicleRequest.BodyControllerState ->
                CommandResult.BodyStatus(vcsecInfo(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_STATUS, null, deadline).vehicleStatus)
            VehicleRequest.ListKeys -> CommandResult.KeyList(listKeys(deadline))
            is VehicleRequest.AddKeyRequest -> {
                sendRaw(request.payload())
                CommandResult.Sent
            }
            is VehicleRequest.SessionInfoQuery ->
                CommandResult.SessionInfo(sessionInfo(request.publicKey, request.domain, deadline))
        }
    }

    // --- Infotainment (infotainment.go) ---

    private suspend fun getCarServerResponse(action: CarServer.VehicleAction, deadline: Deadline): CarServer.Response {
        val payload = CarServer.Action.newBuilder().setVehicleAction(action).build().toByteArray()
        val responsePayload = send(Domain.INFOTAINMENT, payload, AuthMethod.GCM, deadline)
        val response = try {
            CarServer.Response.parseFrom(responsePayload)
        } catch (e: InvalidProtocolBufferException) {
            throw VehicleErrors.badResponse("unable to parse vehicle response", mayHaveSucceeded = true, cause = e)
        }
        if (response.actionStatus.resultValue == CarServer.OperationStatus_E.OPERATIONSTATUS_ERROR_VALUE) {
            val description = response.actionStatus.resultReason.plainText.ifEmpty { "unspecified error" }
            throw VehicleRefusedException("car could not execute command: $description")
        }
        return response
    }

    /** `Vehicle.Send`: retries until the vehicle gives a terminal answer or the deadline passes. */
    private suspend fun send(domain: Domain, payload: ByteArray, auth: AuthMethod, deadline: Deadline): ByteArray {
        while (true) {
            try {
                return trySend(domain, payload, auth, deadline)
            } catch (e: VehicleException) {
                if (!e.shouldRetry()) throw e
                log.debug { "Retrying after: ${e.message}" }
            }
            waitBeforeRetry(deadline)
        }
    }

    private suspend fun trySend(domain: Domain, payload: ByteArray, auth: AuthMethod, deadline: Deadline): ByteArray {
        val receiver = getReceiver(domain, payload, auth, deadline)
        try {
            val response = receiver.receive(deadline.remaining()) ?: throw VehicleErrors.responseTimeout()
            response.protocolError()?.let { throw it }
            return response.protobufMessageAsBytes.toByteArray()
        } finally {
            receiver.close()
        }
    }

    private suspend fun getReceiver(domain: Domain, payload: ByteArray, auth: AuthMethod, deadline: Deadline): Receiver {
        if (deadline.isExpired) throw VehicleErrors.deadlineExceeded()
        val message = RoutableMessage.newBuilder()
            .setToDestination(UniversalMessage.Destination.newBuilder().setDomain(domain.proto))
            .setProtobufMessageAsBytes(payload.toByteString())
            .setFlags(flags)
        return dispatcher.send(message, auth, deadline.remaining())
    }

    private suspend fun waitBeforeRetry(deadline: Deadline) {
        if (deadline.remaining() <= config.retryInterval) throw VehicleErrors.deadlineExceeded()
        delay(config.retryInterval)
    }

    // --- VCSEC (vcsec.go) ---

    private fun interface TerminalTest {
        /** Returns null if more replies are expected, or the outcome (null error = success). */
        fun check(fromVcsec: Vcsec.FromVCSECMessage): Result<Unit>?
    }

    private fun isCommandComplete(fromVcsec: Vcsec.FromVCSECMessage): Result<Unit>? =
        if (fromVcsec.subMessageCase != Vcsec.FromVCSECMessage.SubMessageCase.COMMANDSTATUS) Result.success(Unit) else null

    private fun isWhitelistOperationComplete(fromVcsec: Vcsec.FromVCSECMessage): Result<Unit>? {
        if (fromVcsec.subMessageCase != Vcsec.FromVCSECMessage.SubMessageCase.COMMANDSTATUS) return null
        val status = fromVcsec.commandStatus
        if (status.subMessageCase != Vcsec.CommandStatus.SubMessageCase.WHITELISTOPERATIONSTATUS) return null
        val code = status.whitelistOperationStatus.whitelistOperationInformationValue
        return if (code == Vcsec.WhitelistOperation_information_E.WHITELISTOPERATION_INFORMATION_NONE_VALUE) {
            Result.success(Unit)
        } else {
            Result.failure(KeychainException(code))
        }
    }

    private suspend fun vcsecResult(
        payload: ByteArray,
        auth: AuthMethod,
        deadline: Deadline,
        done: TerminalTest,
    ): Vcsec.FromVCSECMessage = vcsecLock.withLock {
        var result: Vcsec.FromVCSECMessage? = null
        while (result == null) {
            try {
                val receiver = getReceiver(Domain.VCSEC, payload, auth, deadline)
                try {
                    result = readUntil(receiver, deadline, done)
                } finally {
                    receiver.close()
                }
            } catch (e: VehicleException) {
                if (!e.shouldRetry()) throw e
                log.debug { "Retrying VCSEC request after: ${e.message}" }
                waitBeforeRetry(deadline)
            }
        }
        result
    }

    /** VCSEC sends up to three replies per request; read until one is terminal. */
    private suspend fun readUntil(receiver: Receiver, deadline: Deadline, done: TerminalTest): Vcsec.FromVCSECMessage {
        while (true) {
            val reply = receiver.receive(deadline.remaining()) ?: throw VehicleErrors.responseTimeout()
            val fromVcsec = unmarshalVcsecResponse(reply)
            val outcome = done.check(fromVcsec) ?: continue
            outcome.getOrThrow()
            return fromVcsec
        }
    }

    private fun unmarshalVcsecResponse(message: RoutableMessage): Vcsec.FromVCSECMessage {
        message.protocolError()?.let { throw it }
        when (message.payloadCase) {
            RoutableMessage.PayloadCase.PROTOBUF_MESSAGE_AS_BYTES -> Unit
            RoutableMessage.PayloadCase.PAYLOAD_NOT_SET -> return Vcsec.FromVCSECMessage.getDefaultInstance()
            else -> throw CommandException(ErrorKind.BAD_RESPONSE, "payload missing from vehicle response", mayHaveSucceeded = true)
        }
        val fromVcsec = try {
            Vcsec.FromVCSECMessage.parseFrom(message.protobufMessageAsBytes)
        } catch (e: InvalidProtocolBufferException) {
            throw VehicleErrors.badResponse("malformed VCSEC response", mayHaveSucceeded = true, cause = e)
        }
        if (fromVcsec.subMessageCase == Vcsec.FromVCSECMessage.SubMessageCase.NOMINALERROR) {
            val error = fromVcsec.nominalError
            throw VehicleRefusedException("vcsec could not execute command: ${describe(error)}", error)
        }
        if (fromVcsec.subMessageCase == Vcsec.FromVCSECMessage.SubMessageCase.COMMANDSTATUS) {
            val status = fromVcsec.commandStatus
            when (status.operationStatusValue) {
                Vcsec.OperationStatus_E.OPERATIONSTATUS_OK_VALUE -> Unit
                Vcsec.OperationStatus_E.OPERATIONSTATUS_WAIT_VALUE -> throw VehicleErrors.busy()
                Vcsec.OperationStatus_E.OPERATIONSTATUS_ERROR_VALUE -> {
                    if (status.subMessageCase == Vcsec.CommandStatus.SubMessageCase.WHITELISTOPERATIONSTATUS) {
                        val code = status.whitelistOperationStatus.whitelistOperationInformationValue
                        if (code != Vcsec.WhitelistOperation_information_E.WHITELISTOPERATION_INFORMATION_NONE_VALUE) {
                            throw KeychainException(code)
                        }
                    }
                    if (status.subMessageCase != Vcsec.CommandStatus.SubMessageCase.SIGNEDMESSAGESTATUS) {
                        throw VehicleErrors.unknownStatus()
                    }
                }
            }
        }
        return fromVcsec
    }

    private fun describe(error: Errors.NominalError): String = error.genericError.name

    private suspend fun vcsecInfo(type: Vcsec.InformationRequestType, slot: Int?, deadline: Deadline): Vcsec.FromVCSECMessage =
        vcsecResult(VehicleRequest.informationRequest(type, slot), AuthMethod.NONE, deadline) { Result.success(Unit) }

    /** Reads every enrolled key (`list-keys`); a single unreadable slot is skipped, as the CLI does. */
    private suspend fun listKeys(deadline: Deadline): List<KeyEntry> {
        val summary = vcsecInfo(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_WHITELIST_INFO, null, deadline).whitelistInfo
        val keys = mutableListOf<KeyEntry>()
        var mask = summary.slotMask.toUInt()
        var slot = 0
        while (mask > 0u) {
            if (mask and 1u == 1u) {
                try {
                    val details = vcsecInfo(
                        Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_WHITELIST_ENTRY_INFO, slot, deadline,
                    ).whitelistEntryInfo
                    keys += KeyEntry(
                        slot = slot,
                        publicKey = details.publicKey.publicKeyRaw.toByteArray(),
                        role = details.keyRole,
                        formFactor = details.metadataForKey.keyFormFactor,
                    )
                } catch (e: CommandException) {
                    if (e.kind == ErrorKind.TIMEOUT || e.kind == ErrorKind.CONNECTION_LOST) throw e
                    log.warn { "Error fetching key slot $slot: ${e.message}" }
                } catch (e: VehicleException) {
                    log.warn { "Error fetching key slot $slot: ${e.message}" }
                }
            }
            mask = mask shr 1
            slot++
        }
        return keys
    }

    /** Session info for any key (`Vehicle.SessionInfo`); fails with KEY_NOT_PAIRED for unknown keys. */
    private suspend fun sessionInfo(publicKey: ByteArray, domain: Domain, deadline: Deadline): Signatures.SessionInfo {
        suspend fun query(): Signatures.SessionInfo {
            val receiver = dispatcher.send(Dispatcher.sessionInfoRequest(domain, publicKey), AuthMethod.NONE, deadline.remaining())
            try {
                val reply = receiver.receive(deadline.remaining()) ?: throw VehicleErrors.deadlineExceeded()
                reply.protocolError()?.let { throw it }
                if (reply.payloadCase != RoutableMessage.PayloadCase.SESSION_INFO) throw VehicleErrors.badResponse("missing session info")
                return try {
                    Signatures.SessionInfo.parseFrom(reply.sessionInfo)
                } catch (e: InvalidProtocolBufferException) {
                    throw VehicleErrors.badResponse("malformed session info", cause = e)
                }
            } finally {
                receiver.close()
            }
        }
        return if (domain == Domain.VCSEC) vcsecLock.withLock { query() } else query()
    }

    /** Sends a message outside the routable-message protocol (key-card pairing requests). */
    private suspend fun sendRaw(message: ByteArray) {
        if (!isConnected) throw VehicleErrors.notConnected()
        try {
            transport.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VehicleException) {
            throw e
        } catch (e: Exception) {
            throw TransportException("failed to send to vehicle: ${e.message}", e)
        }
    }
}
