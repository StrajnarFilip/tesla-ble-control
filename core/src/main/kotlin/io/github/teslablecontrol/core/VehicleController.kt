package io.github.teslablecontrol.core

import io.github.teslablecontrol.protocol.transport.Transport
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import kotlinx.coroutines.flow.StateFlow

enum class LinkStatus { DISCONNECTED, CONNECTING, CONNECTED }

/** What the controller is doing right now, for progress text ("Waking car…"). */
enum class Stage { CONNECTING, SECURING, WAKING, SENDING, REFRESHING }

data class Activity(val commandId: String, val stage: Stage)

data class ControllerState(
    val link: LinkStatus = LinkStatus.DISCONNECTED,
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val activity: Activity? = null,
    /** False once the car reports this phone's key isn't enrolled; null until known. */
    val keyPaired: Boolean? = null,
    val lastFailure: CommandOutcome.Failure? = null,
)

/** Everything the UI does with one vehicle. Commands run one at a time. */
interface VehicleController {
    val vin: String
    val state: StateFlow<ControllerState>

    /** Runs a catalog command, connecting, securing the session and waking the car as needed. */
    suspend fun execute(commandId: String, args: Map<String, String> = emptyMap()): CommandOutcome

    /** Reads state categories. When the car is asleep they are only fetched if [wake] is true. */
    suspend fun refresh(categories: Collection<StateCategory>, wake: Boolean = false): CommandOutcome

    /** Reads lock/closure/sleep state from the body controller; never wakes the car. */
    suspend fun refreshBodyState(): CommandOutcome

    /** Asks the car to enroll this phone's key; the user approves with the key card. */
    suspend fun requestPairing(role: String): CommandOutcome

    /** True once the car accepts this phone's key; null if the car couldn't be asked. */
    suspend fun checkPaired(): Boolean?

    /** Opens the link without sending anything. */
    suspend fun connect(): CommandOutcome

    /**
     * Uses a link that was opened elsewhere (the background connection waits for the car to come into
     * range). Returns false, closing [transport], if the controller already has a working link.
     */
    suspend fun attach(transport: Transport): Boolean

    fun disconnect()

    /**
     * Forgets the link, cached sessions and pairing status. Called after this phone's key is replaced,
     * because everything the car knew about the old key no longer applies.
     */
    fun resetKey()

    /** Suspends until the current link closes; returns at once if there is none. */
    suspend fun awaitDisconnected()
}
