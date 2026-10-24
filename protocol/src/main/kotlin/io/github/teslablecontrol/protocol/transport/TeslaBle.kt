package io.github.teslablecontrol.protocol.transport

import io.github.teslablecontrol.protocol.toHex
import java.security.MessageDigest

/** BLE identifiers from the protocol specification. */
object TeslaBle {
    const val SERVICE_UUID = "00000211-b2d1-43f0-9b88-960cebf8b91e"

    /** Vehicle write characteristic; use write *with* response. */
    const val TO_VEHICLE_UUID = "00000212-b2d1-43f0-9b88-960cebf8b91e"

    /** Vehicle read characteristic (indications). */
    const val FROM_VEHICLE_UUID = "00000213-b2d1-43f0-9b88-960cebf8b91e"

    const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** VCSEC can only reliably hold this many simultaneous BLE connections (key fobs + phone keys). */
    const val MAX_CONNECTIONS = 3

    private val LOCAL_NAME = Regex("^S[0-9a-f]{16}C$")

    /** Advertised local name: `"S" + hex(sha1(VIN)[0:8]) + "C"`. */
    fun localName(vin: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(vin.toByteArray(Charsets.UTF_8))
        return "S" + digest.copyOf(8).toHex() + "C"
    }

    fun isVehicleLocalName(name: String?): Boolean = name != null && LOCAL_NAME.matches(name)
}
