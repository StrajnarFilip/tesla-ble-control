package io.github.teslablecontrol.core

import io.github.teslablecontrol.protocol.dispatch.SessionCacheEntry
import io.github.teslablecontrol.protocol.transport.Transport

/** Opens links to vehicles: BLE in the app, TCP to the simulator in tests. */
fun interface LinkFactory {
    /** Finds and connects to the vehicle; throws [LinkException] if that isn't possible. */
    suspend fun open(vin: String): Transport
}

enum class LinkFailure {
    BLUETOOTH_OFF,
    PERMISSION_MISSING,
    /** Android only delivers BLE scan results while location services are on. */
    LOCATION_OFF,
    NOT_FOUND,
    /** The car already holds its maximum number of BLE connections. */
    TOO_MANY_CONNECTIONS,
    CONNECTION_FAILED,
}

class LinkException(val failure: LinkFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** Persists session state per vehicle so reconnects skip the handshake. */
interface SessionStore {
    fun load(vin: String): List<SessionCacheEntry>
    fun save(vin: String, entries: List<SessionCacheEntry>)
    fun clear(vin: String)
}

class InMemorySessionStore : SessionStore {
    private val entries = mutableMapOf<String, List<SessionCacheEntry>>()

    @Synchronized
    override fun load(vin: String): List<SessionCacheEntry> = entries[vin].orEmpty()

    @Synchronized
    override fun save(vin: String, entries: List<SessionCacheEntry>) {
        this.entries[vin] = entries
    }

    @Synchronized
    override fun clear(vin: String) {
        entries.remove(vin)
    }
}
