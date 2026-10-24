package io.github.teslablecontrol.protocol

import com.tesla.generated.errors.Errors
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec
import com.google.protobuf.InvalidProtocolBufferException

/**
 * Every failure reported by the protocol. Like `protocol.Error` in the official library, each one
 * says whether the command may still have executed and whether retrying could help.
 */
open class VehicleException(
    message: String,
    val mayHaveSucceeded: Boolean = false,
    val temporary: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class ErrorKind {
    BUSY,
    UNKNOWN_STATUS,
    NOT_CONNECTED,
    CONNECTION_LOST,
    NO_SESSION,
    REQUIRES_KEY,
    KEY_NOT_PAIRED,
    BAD_RESPONSE,
    TIMEOUT,
    TRANSPORT,
    REPLAYED_RESPONSE,
    NO_DECRYPTION_CONTEXT,
    INVALID_ARGUMENT,
    PROTOCOL,
}

/** Failures with a fixed meaning (the official library's sentinel errors). */
open class CommandException(
    val kind: ErrorKind,
    message: String,
    mayHaveSucceeded: Boolean = false,
    temporary: Boolean = false,
    cause: Throwable? = null,
) : VehicleException(message, mayHaveSucceeded, temporary, cause)

/** A protocol-layer fault reported by the vehicle (`protocol.RoutableMessageError`). */
class MessageFaultException(val faultCode: Int) : VehicleException(
    message = UniversalMessage.MessageFault_E.forNumber(faultCode)?.name ?: "unrecognized error code $faultCode",
    mayHaveSucceeded = faultCode == UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_NONE_VALUE ||
        faultCode == UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_RESPONSE_MTU_EXCEEDED_VALUE,
    temporary = faultCode in RETRIABLE_FAULTS,
) {
    val fault: UniversalMessage.MessageFault_E? get() = UniversalMessage.MessageFault_E.forNumber(faultCode)

    companion object {
        // Faults that retrying (possibly with session info from the error reply) can fix.
        private val RETRIABLE_FAULTS = setOf(
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BUSY_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_TIMEOUT_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_SIGNATURE_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_TOKEN_OR_COUNTER_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INTERNAL_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INCORRECT_EPOCH_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_TIME_EXPIRED_VALUE,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_TIME_TO_LIVE_TOO_LONG_VALUE,
        )
    }
}

/** A key add/remove the vehicle refused (`protocol.KeychainError`). */
class KeychainException(val codeValue: Int) : VehicleException(
    "keychain operation failed: " +
        (Vcsec.WhitelistOperation_information_E.forNumber(codeValue)?.name ?: "code $codeValue"),
) {
    val code: Vcsec.WhitelistOperation_information_E? get() = Vcsec.WhitelistOperation_information_E.forNumber(codeValue)
}

/** The vehicle authenticated the command but could not execute it (`protocol.NominalError`). */
class VehicleRefusedException(
    val reason: String,
    val vcsecError: Errors.NominalError? = null,
) : VehicleException(reason)

/** Errors with fixed messages, matching the official library's sentinels. */
object VehicleErrors {
    fun busy() = CommandException(ErrorKind.BUSY, "vehicle busy or finishing wake-up", temporary = true)
    fun unknownStatus() = CommandException(ErrorKind.UNKNOWN_STATUS, "vehicle responded with an unrecognized status code")
    fun notConnected() = CommandException(ErrorKind.NOT_CONNECTED, "vehicle not connected")
    fun connectionLost() = CommandException(
        ErrorKind.CONNECTION_LOST,
        "connection to the vehicle was lost before it answered",
        mayHaveSucceeded = true,
    )
    fun noSession() = CommandException(
        ErrorKind.NO_SESSION,
        "cannot send authenticated command before establishing a vehicle session",
    )
    fun requiresKey() = CommandException(ErrorKind.REQUIRES_KEY, "no private key available")
    fun keyNotPaired() = CommandException(
        ErrorKind.KEY_NOT_PAIRED,
        "vehicle rejected request: your public key has not been paired with the vehicle",
    )
    fun badResponse(detail: String, mayHaveSucceeded: Boolean = false, cause: Throwable? = null) =
        CommandException(ErrorKind.BAD_RESPONSE, "invalid response: $detail", mayHaveSucceeded, cause = cause)
    fun responseTimeout() = CommandException(
        ErrorKind.TIMEOUT,
        "timed out waiting for the vehicle to respond",
        mayHaveSucceeded = true,
        temporary = true,
    )
    fun deadlineExceeded() = CommandException(ErrorKind.TIMEOUT, "operation timed out")
    fun replayedResponse() = CommandException(ErrorKind.REPLAYED_RESPONSE, "received vehicle response with duplicate counter")
    fun noDecryptionContext() = CommandException(
        ErrorKind.NO_DECRYPTION_CONTEXT,
        "could not decrypt vehicle response without a session",
    )
    fun invalidArgument(message: String) = CommandException(ErrorKind.INVALID_ARGUMENT, message)
    fun protocol(message: String, cause: Throwable? = null) = CommandException(ErrorKind.PROTOCOL, message, cause = cause)
}

/** True when the operation failed for a transient reason and certainly did not execute (`protocol.ShouldRetry`). */
fun Throwable.shouldRetry(): Boolean = this is VehicleException && !mayHaveSucceeded && temporary

/** Port of `protocol.GetError`: the error carried by a vehicle reply, or null if it reports success. */
fun UniversalMessage.RoutableMessage.protocolError(): VehicleException? {
    val fault = signedMessageStatus.signedMessageFaultValue
    if (fault != UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_NONE_VALUE) {
        // UNKNOWN_KEY_ID is common and cryptic, so it gets a clearer error.
        if (fault == UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID_VALUE) {
            return VehicleErrors.keyNotPaired()
        }
        return MessageFaultException(fault)
    }
    if (payloadCase == UniversalMessage.RoutableMessage.PayloadCase.SESSION_INFO) {
        val info = try {
            Signatures.SessionInfo.parseFrom(sessionInfo)
        } catch (e: InvalidProtocolBufferException) {
            return VehicleErrors.badResponse("malformed session info", cause = e)
        }
        when (info.statusValue) {
            Signatures.Session_Info_Status.SESSION_INFO_STATUS_OK_VALUE -> Unit
            Signatures.Session_Info_Status.SESSION_INFO_STATUS_KEY_NOT_ON_WHITELIST_VALUE -> return VehicleErrors.keyNotPaired()
            else -> return VehicleErrors.unknownStatus()
        }
    }
    return when (signedMessageStatus.operationStatusValue) {
        UniversalMessage.OperationStatus_E.OPERATIONSTATUS_OK_VALUE -> null
        UniversalMessage.OperationStatus_E.OPERATIONSTATUS_WAIT_VALUE -> VehicleErrors.busy()
        // An ERROR status carries its details in signed_message_fault, handled above.
        UniversalMessage.OperationStatus_E.OPERATIONSTATUS_ERROR_VALUE -> null
        else -> VehicleErrors.unknownStatus()
    }
}
