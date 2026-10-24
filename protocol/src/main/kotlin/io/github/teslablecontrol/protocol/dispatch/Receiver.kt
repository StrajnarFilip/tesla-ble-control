package io.github.teslablecontrol.protocol.dispatch

import com.google.protobuf.ByteString
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import io.github.teslablecontrol.protocol.session.SlidingWindow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Identifies the replies to one request: VCSEC rarely echoes the UUID, so its requests get unique addresses. */
internal data class ReceiverKey(val address: ByteString, val uuid: ByteString, val domain: Int)

/** Pending replies to one request (port of `internal/dispatcher/receiver.go`). */
class Receiver internal constructor(
    internal val key: ReceiverKey,
    /** Identifies the request inside the metadata of its encrypted reply. */
    internal val requestId: ByteArray?,
    private val sentAtNanos: Long,
    capacity: Int,
    private val onClose: (Receiver) -> Unit,
) {
    internal val channel = Channel<RoutableMessage>(capacity)

    /** Response counters already seen; accessed under the owning `DomainSession` lock. */
    internal val antiReplay = SlidingWindow()

    /** The next reply, or null if [timeout] elapses. Throws if the link to the vehicle went down. */
    suspend fun receive(timeout: Duration): RoutableMessage? = withTimeoutOrNull(timeout) { channel.receive() }

    /** True if session info arriving now would be too stale to trust. */
    internal fun isExpired(nowNanos: Long, lifetime: Duration): Boolean =
        nowNanos - sentAtNanos > lifetime.inWholeNanoseconds

    internal fun fail(reason: Throwable) {
        channel.close(reason)
    }

    /** Stops listening for replies. */
    fun close() = onClose(this)
}
