package io.github.teslablecontrol.protocol

import com.tesla.generated.universalmessage.UniversalMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Vehicle subsystems with their own keys and sessions. VCSEC stays reachable while infotainment sleeps. */
enum class Domain(val proto: UniversalMessage.Domain) {
    VCSEC(UniversalMessage.Domain.DOMAIN_VEHICLE_SECURITY),
    INFOTAINMENT(UniversalMessage.Domain.DOMAIN_INFOTAINMENT);

    val number: Int get() = proto.number

    companion object {
        fun fromNumber(number: Int): Domain? = entries.firstOrNull { it.number == number }
    }
}

/** How a request is authenticated. BLE always uses AES-GCM (as the official BLE connector does). */
enum class AuthMethod { NONE, GCM }

/** Timing parameters, defaulting to the values of the official BLE connector. */
data class ProtocolConfig(
    /** Delay between retransmissions (`ble.Connection.RetryInterval`). */
    val retryInterval: Duration = 1.seconds,
    /** Session info arriving later than this after the request is discarded (`ble.Connection.AllowedLatency`). */
    val maxLatency: Duration = 4.seconds,
    /** Command lifetime when the caller gives no deadline (`dispatcher.defaultExpiration`). */
    val defaultCommandLifetime: Duration = 5.seconds,
    /** Upper bound on a command's lifetime, well under what vehicles accept. */
    val maxCommandLifetime: Duration = 30.seconds,
    val receiverBufferSize: Int = 10,
)
