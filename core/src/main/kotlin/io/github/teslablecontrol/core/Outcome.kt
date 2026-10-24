package io.github.teslablecontrol.core

import io.github.teslablecontrol.protocol.CommandException
import io.github.teslablecontrol.protocol.ErrorKind
import io.github.teslablecontrol.protocol.KeychainException
import io.github.teslablecontrol.protocol.MessageFaultException
import io.github.teslablecontrol.protocol.VehicleException
import io.github.teslablecontrol.protocol.VehicleRefusedException
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import com.tesla.generated.universalmessage.UniversalMessage.MessageFault_E

/** Why a command failed, in terms the UI can explain and offer a fix for. */
enum class FailureKind {
    /** This phone's key is not enrolled on the car (or was removed). */
    NOT_PAIRED,
    BLUETOOTH_OFF,
    PERMISSION_MISSING,
    LOCATION_OFF,
    /** The car didn't show up in a Bluetooth scan. */
    OUT_OF_RANGE,
    TOO_MANY_CONNECTIONS,
    CONNECTION_FAILED,
    /** The car is asleep and auto-wake is off, or it didn't wake in time. */
    ASLEEP,
    TIMEOUT,
    BUSY,
    /** The car understood the command but refused it; [Failure.message] has the car's reason. */
    REFUSED,
    /** A key add/remove was refused (permissions, duplicates, full keychain). */
    KEYCHAIN,
    /** The key's role doesn't allow this command. */
    NOT_ALLOWED,
    /** Mobile access is disabled on the car's touchscreen. */
    MOBILE_ACCESS_DISABLED,
    INVALID_INPUT,
    CONNECTION_LOST,
    PROTOCOL,
}

sealed interface CommandOutcome {
    data class Success(val result: CommandResult) : CommandOutcome

    data class Failure(
        val kind: FailureKind,
        val message: String,
        /** The command may have executed even though no confirmation arrived. */
        val mayHaveSucceeded: Boolean = false,
    ) : CommandOutcome
}

/** Maps any failure from the link or protocol into a [CommandOutcome.Failure]. */
fun Throwable.toFailure(): CommandOutcome.Failure = when (this) {
    is LinkException -> CommandOutcome.Failure(
        when (failure) {
            LinkFailure.BLUETOOTH_OFF -> FailureKind.BLUETOOTH_OFF
            LinkFailure.PERMISSION_MISSING -> FailureKind.PERMISSION_MISSING
            LinkFailure.LOCATION_OFF -> FailureKind.LOCATION_OFF
            LinkFailure.NOT_FOUND -> FailureKind.OUT_OF_RANGE
            LinkFailure.TOO_MANY_CONNECTIONS -> FailureKind.TOO_MANY_CONNECTIONS
            LinkFailure.CONNECTION_FAILED -> FailureKind.CONNECTION_FAILED
        },
        message ?: "could not connect",
    )
    is AsleepException -> CommandOutcome.Failure(FailureKind.ASLEEP, message ?: "the car is asleep")
    is VehicleRefusedException -> CommandOutcome.Failure(FailureKind.REFUSED, reason.removePrefix("car could not execute command: "))
    is KeychainException -> CommandOutcome.Failure(FailureKind.KEYCHAIN, message ?: "keychain operation failed")
    is MessageFaultException -> CommandOutcome.Failure(
        when (fault) {
            MessageFault_E.MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES -> FailureKind.NOT_ALLOWED
            MessageFault_E.MESSAGEFAULT_ERROR_REMOTE_ACCESS_DISABLED -> FailureKind.MOBILE_ACCESS_DISABLED
            MessageFault_E.MESSAGEFAULT_ERROR_INACTIVE_KEY -> FailureKind.NOT_PAIRED
            MessageFault_E.MESSAGEFAULT_ERROR_BUSY -> FailureKind.BUSY
            else -> FailureKind.PROTOCOL
        },
        message ?: "vehicle fault",
        mayHaveSucceeded,
    )
    is CommandException -> CommandOutcome.Failure(
        when (kind) {
            ErrorKind.KEY_NOT_PAIRED -> FailureKind.NOT_PAIRED
            ErrorKind.BUSY -> FailureKind.BUSY
            ErrorKind.TIMEOUT -> FailureKind.TIMEOUT
            ErrorKind.NOT_CONNECTED, ErrorKind.CONNECTION_LOST, ErrorKind.TRANSPORT -> FailureKind.CONNECTION_LOST
            ErrorKind.INVALID_ARGUMENT -> FailureKind.INVALID_INPUT
            ErrorKind.REQUIRES_KEY -> FailureKind.NOT_PAIRED
            else -> FailureKind.PROTOCOL
        },
        message ?: kind.name,
        mayHaveSucceeded,
    )
    is VehicleException -> CommandOutcome.Failure(FailureKind.PROTOCOL, message ?: "vehicle error", mayHaveSucceeded)
    else -> CommandOutcome.Failure(FailureKind.PROTOCOL, message ?: javaClass.simpleName)
}

/** The car is asleep and the caller asked not to wake it (or waking took too long). */
class AsleepException(message: String) : Exception(message)
