package io.github.teslablecontrol.protocol.transport

import io.github.teslablecontrol.protocol.ErrorKind
import io.github.teslablecontrol.protocol.CommandException
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * A datagram link to one vehicle (the official library's `connector.Connector`). Messages are
 * complete protobuf-encoded frames; BLE framing and chunking are the implementation's business.
 */
interface Transport {
    /** Messages from the vehicle. The channel closes when the link goes down. Single consumer. */
    val incoming: ReceiveChannel<ByteArray>

    /** Sends one message, suspending until the link layer has accepted all of it. */
    suspend fun send(message: ByteArray)

    /** Closes the link. Idempotent. */
    fun close()
}

/** The link failed while sending; the vehicle may or may not have received the message. */
class TransportException(message: String, cause: Throwable? = null) :
    CommandException(ErrorKind.TRANSPORT, message, mayHaveSucceeded = false, temporary = false, cause = cause)
