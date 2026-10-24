package io.github.teslablecontrol.protocol.vehicle

import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.keys.Keys
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.Domain
import io.github.teslablecontrol.protocol.toByteString

/** A fully built vehicle command: which domain handles it, how it is authenticated, and its payload. */
sealed class VehicleRequest {
    abstract val domain: Domain
    abstract val requiresAuth: Boolean

    /** The plaintext protobuf payload, as the vehicle sees it after decryption. */
    abstract fun payload(): ByteArray

    /** An infotainment action (`executeCarServerAction`). */
    class CarServerAction(
        val action: CarServer.VehicleAction,
        /** Errors ending with this text count as success (valet mode "already off"). */
        val successIfErrorEndsWith: String? = null,
    ) : VehicleRequest() {
        override val domain get() = Domain.INFOTAINMENT
        override val requiresAuth get() = true
        override fun payload(): ByteArray = CarServer.Action.newBuilder().setVehicleAction(action).build().toByteArray()
    }

    /** Fetches one category of vehicle state (`GetState`). */
    class State(val category: StateCategory) : VehicleRequest() {
        override val domain get() = Domain.INFOTAINMENT
        override val requiresAuth get() = true
        val action: CarServer.VehicleAction
            get() = CarServer.VehicleAction.newBuilder().setGetVehicleData(category.request()).build()
        override fun payload(): ByteArray = CarServer.Action.newBuilder().setVehicleAction(action).build().toByteArray()
    }

    /** A key-fob style command (`executeRKEAction`). */
    class Rke(val action: Vcsec.RKEAction_E) : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = true
        override fun payload(): ByteArray = Vcsec.UnsignedMessage.newBuilder().setRKEAction(action).build().toByteArray()
    }

    /** Moves a trunk, frunk or tonneau (`executeClosureAction`). */
    class Closure(val request: Vcsec.ClosureMoveRequest) : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = true
        override fun payload(): ByteArray = Vcsec.UnsignedMessage.newBuilder().setClosureMoveRequest(request).build().toByteArray()
    }

    /** Adds or removes a key, authorized by an owner key (`executeWhitelistOperation`). */
    class Whitelist(val operation: Vcsec.WhitelistOperation) : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = true
        override fun payload(): ByteArray = Vcsec.UnsignedMessage.newBuilder().setWhitelistOperation(operation).build().toByteArray()
    }

    /** Locks, closures and sleep state; works without a session while infotainment sleeps. */
    object BodyControllerState : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = false
        override fun payload(): ByteArray = informationRequest(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_STATUS, null)
    }

    /** Lists the keys enrolled on the vehicle (unauthenticated whitelist queries). */
    object ListKeys : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = false
        override fun payload(): ByteArray =
            informationRequest(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_WHITELIST_INFO, null)
    }

    /**
     * Asks the vehicle to enroll [publicKey]; someone must approve it with a key card
     * (`SendAddKeyRequestWithRole`). Sent as a raw `ToVCSECMessage`, not a routable message.
     */
    class AddKeyRequest(
        val publicKey: ByteArray,
        val role: Keys.Role,
        val formFactor: Vcsec.KeyFormFactor,
    ) : VehicleRequest() {
        override val domain get() = Domain.VCSEC
        override val requiresAuth get() = false
        override fun payload(): ByteArray = Vcsec.ToVCSECMessage.newBuilder()
            .setSignedMessage(
                Vcsec.SignedMessage.newBuilder()
                    .setProtobufMessageAsBytes(
                        Vcsec.UnsignedMessage.newBuilder()
                            .setWhitelistOperation(addKeyOperation(publicKey, role, formFactor))
                            .build()
                            .toByteString(),
                    )
                    .setSignatureType(Vcsec.SignatureType.SIGNATURE_TYPE_PRESENT_KEY),
            )
            .build()
            .toByteArray()
    }

    /** Requests session info for any public key; shows whether that key is enrolled (`SessionInfo`). */
    class SessionInfoQuery(val publicKey: ByteArray, override val domain: Domain) : VehicleRequest() {
        override val requiresAuth get() = false
        override fun payload(): ByteArray = publicKey.copyOf()
    }

    companion object {
        internal fun informationRequest(type: Vcsec.InformationRequestType, slot: Int?): ByteArray {
            val request = Vcsec.InformationRequest.newBuilder().setInformationRequestType(type)
            if (slot != null) request.setSlot(slot)
            return Vcsec.UnsignedMessage.newBuilder().setInformationRequest(request).build().toByteArray()
        }

        internal fun addKeyOperation(
            publicKey: ByteArray,
            role: Keys.Role,
            formFactor: Vcsec.KeyFormFactor,
        ): Vcsec.WhitelistOperation = Vcsec.WhitelistOperation.newBuilder()
            .setAddKeyToWhitelistAndAddPermissions(
                Vcsec.PermissionChange.newBuilder()
                    .setKey(Vcsec.PublicKey.newBuilder().setPublicKeyRaw(publicKey.toByteString()))
                    .setKeyRole(role),
            )
            .setMetadataForKey(Vcsec.KeyMetadata.newBuilder().setKeyFormFactor(formFactor))
            .build()
    }
}

/** A key enrolled on the vehicle. */
class KeyEntry(
    val slot: Int,
    val publicKey: ByteArray,
    val role: Keys.Role,
    val formFactor: Vcsec.KeyFormFactor,
)

/** What a successful command returns. */
sealed interface CommandResult {
    /** The vehicle confirmed the command. */
    data object Done : CommandResult

    /** Sent without an acknowledgement (pairing requests are approved on the vehicle). */
    data object Sent : CommandResult

    class State(val data: Vehicle.VehicleData) : CommandResult
    class BodyStatus(val status: Vcsec.VehicleStatus) : CommandResult
    class KeyList(val keys: List<KeyEntry>) : CommandResult
    class SessionInfo(val info: Signatures.SessionInfo) : CommandResult
}
