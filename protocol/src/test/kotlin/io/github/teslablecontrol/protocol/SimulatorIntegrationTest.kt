package io.github.teslablecontrol.protocol

import com.tesla.generated.keys.Keys
import com.tesla.generated.vcsec.Vcsec
import io.github.teslablecontrol.protocol.crypto.SoftwareEcdhKey
import io.github.teslablecontrol.protocol.testing.VehicleSimulator
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import io.github.teslablecontrol.protocol.vehicle.VehicleClient
import io.github.teslablecontrol.protocol.vehicle.VehicleRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The Kotlin protocol stack against the reference simulator, which authenticates and encrypts with
 * Tesla's own vehicle-side code. Frames travel over TCP in the BLE wire format.
 */
class SimulatorIntegrationTest {
    private val vin = VehicleSimulator.VIN
    private val owner = SoftwareEcdhKey.generate()

    @Before
    fun requireSimulator() {
        assumeTrue("vehicle simulator not built; run tools/vehicle-sim/build.sh", VehicleSimulator.binary != null)
    }

    private fun <T> withClient(
        sim: VehicleSimulator,
        key: SoftwareEcdhKey? = owner,
        block: suspend CoroutineScope.(VehicleClient) -> T,
    ): T = runBlocking {
        val client = VehicleClient(vin, sim.connect(), key)
        client.connect(this)
        try {
            block(client)
        } finally {
            client.disconnect()
        }
    }

    private suspend fun VehicleClient.run(id: String, vararg args: Pair<String, String>): CommandResult =
        execute(io.github.teslablecontrol.protocol.commands.CommandCatalog[id].build(args.toMap()), 10.seconds)

    private suspend fun VehicleClient.lockState(): Vcsec.VehicleLockState_E =
        (run("body-controller-state") as CommandResult.BodyStatus).status.vehicleLockState

    @Test
    fun handshakeLockUnlockAndState() {
        VehicleSimulator.builder().owner(owner.publicKey).start().use { sim ->
            withClient(sim) { client ->
                client.startSession(Domain.VCSEC, 10.seconds)
                client.startSession(Domain.INFOTAINMENT, 10.seconds)
                assertEquals(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, client.lockState())
                client.run("unlock")
                assertEquals(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED, client.lockState())
                client.run("lock")
                assertEquals(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, client.lockState())

                client.run("charging-set-limit", "PERCENT" to "90")
                client.run("climate-on")
                val charge = (client.run("state", "CATEGORY" to "charge") as CommandResult.State).data.chargeState
                assertEquals(90, charge.chargeLimitSoc)
                val climate = (client.run("state", "CATEGORY" to "climate") as CommandResult.State).data.climateState
                assertTrue(climate.isClimateOn)
            }
        }
    }

    @Test
    fun bodyStateWorksWithoutPairingAndSessionsRequireIt() {
        VehicleSimulator.builder().start().use { sim ->
            withClient(sim, key = SoftwareEcdhKey.generate()) { client ->
                assertEquals(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, client.lockState())
                val error = assertFailsWith<CommandException> { client.startSession(Domain.VCSEC, 5.seconds) }
                assertEquals(ErrorKind.KEY_NOT_PAIRED, error.kind)
            }
        }
    }

    @Test
    fun keyCardPairingFlow() {
        VehicleSimulator.builder().pairingDelay(1500).start().use { sim ->
            val phone = SoftwareEcdhKey.generate()
            withClient(sim, key = phone) { client ->
                assertIs<CommandResult.Sent>(
                    client.execute(
                        VehicleRequests.sendAddKeyRequestWithRole(
                            phone.publicKey, Keys.Role.ROLE_OWNER, Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE,
                        ),
                        5.seconds,
                    ),
                )
                // Poll the way the app does until the simulated key-card tap enrolls the key.
                var paired = false
                repeat(20) {
                    if (!paired) {
                        paired = runCatching {
                            client.execute(VehicleRequests.sessionInfo(phone.publicKey, Domain.VCSEC), 3.seconds)
                        }.isSuccess
                        if (!paired) delay(250)
                    }
                }
                assertTrue(paired, "key was not enrolled")
                client.startSession(Domain.VCSEC, 5.seconds)
                client.run("unlock")
                val keys = (client.run("list-keys") as CommandResult.KeyList).keys
                assertEquals(Keys.Role.ROLE_OWNER, keys.single { it.publicKey.contentEquals(phone.publicKey) }.role)
            }
        }
    }

    @Test
    fun wakesSleepingInfotainment() {
        VehicleSimulator.builder().owner(owner.publicKey).asleep(wakeDelayMillis = 1000).start().use { sim ->
            withClient(sim) { client ->
                client.startSession(Domain.VCSEC, 5.seconds)
                val status = (client.run("body-controller-state") as CommandResult.BodyStatus).status
                assertEquals(Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP, status.vehicleSleepStatus)
                client.run("wake")
                // The handshake retries every second while infotainment boots.
                client.startSession(Domain.INFOTAINMENT, 15.seconds)
                client.run("climate-on")
            }
        }
    }

    @Test
    fun cachedSessionsSurviveReconnectAndVehicleReboot() {
        VehicleSimulator.builder().owner(owner.publicKey).start().use { sim ->
            val cache = withClient(sim) { client ->
                client.startSession(Domain.VCSEC, 5.seconds)
                client.startSession(Domain.INFOTAINMENT, 5.seconds)
                client.run("honk")
                client.exportSessionCache()
            }
            assertEquals(2, cache.size)

            // A new connection resumes without a handshake.
            withClient(sim) { client ->
                client.loadSessionCache(cache)
                assertTrue(client.hasSession(Domain.VCSEC) && client.hasSession(Domain.INFOTAINMENT))
                client.run("flash-lights")
                client.run("lock")
            }

            // After a reboot the cached epoch is stale: the vehicle's error carries fresh session info
            // and the command is retried transparently.
            sim.control("reboot")
            withClient(sim) { client ->
                client.loadSessionCache(cache)
                client.run("flash-lights")
                client.run("unlock")
            }
        }
    }

    @Test
    fun toleratesIntermediateAndDuplicateReplies() {
        VehicleSimulator.builder().owner(owner.publicKey).intermediateReplies().duplicateReplies().start().use { sim ->
                withClient(sim) { client ->
                    client.startSession(Domain.VCSEC, 5.seconds)
                    client.startSession(Domain.INFOTAINMENT, 5.seconds)
                    client.run("unlock")
                    client.run("frunk-open")
                    client.run("sentry-mode", "STATE" to "on")
                    val closures = (client.run("state", "CATEGORY" to "closures") as CommandResult.State).data.closuresState
                    assertFalse(closures.locked)
                    assertTrue(closures.doorOpenTrunkFront)
                }
            }
    }

    @Test
    fun retriesBusyVehicle() {
        VehicleSimulator.builder().owner(owner.publicKey).busy(2).start().use { sim ->
            withClient(sim) { client ->
                client.startSession(Domain.INFOTAINMENT, 5.seconds)
                client.run("charging-set-amps", "AMPS" to "12")
                val charge = (client.run("state", "CATEGORY" to "charge") as CommandResult.State).data.chargeState
                assertEquals(12, charge.chargingAmps)
            }
        }
    }

    @Test
    fun droppedRepliesAreRetriedForHandshakesAndReportedForCommands() {
        VehicleSimulator.builder().owner(owner.publicKey).dropEvery(2).start().use { sim ->
                withClient(sim) { client ->
                    // Handshakes are idempotent, so dropped handshakes are simply retried.
                    client.startSession(Domain.VCSEC, 10.seconds)
                    client.startSession(Domain.INFOTAINMENT, 10.seconds)
                    var timeouts = 0
                    var successes = 0
                    repeat(6) {
                        try {
                            client.execute(VehicleRequests.honkHorn(), 2.seconds)
                            successes++
                        } catch (e: CommandException) {
                            // A lost reply means the command may have run, so it is never resent blindly.
                            assertEquals(ErrorKind.TIMEOUT, e.kind)
                            assertTrue(e.mayHaveSucceeded)
                            timeouts++
                        }
                    }
                    assertTrue(timeouts > 0 && successes > 0, "expected a mix of results, saw $successes ok / $timeouts lost")
                }
            }
    }

    @Test
    fun vehicleRefusalsCarryTheReason() {
        VehicleSimulator.builder().owner(owner.publicKey).start().use { sim ->
            withClient(sim) { client ->
                client.startSession(Domain.INFOTAINMENT, 5.seconds)
                val refused = assertFailsWith<VehicleRefusedException> { client.run("charging-set-limit", "PERCENT" to "20") }
                assertTrue(refused.reason.endsWith("invalid charge limit"))
                assertFalse(refused.shouldRetry())
                // Turning valet mode off when it is already off counts as success, as in the official library.
                client.run("valet-mode-off")
            }
        }
    }

    @Test
    fun ownerManagesKeys() {
        VehicleSimulator.builder().owner(owner.publicKey).start().use { sim ->
            val friend = SoftwareEcdhKey.generate()
            withClient(sim) { client ->
                client.startSession(Domain.VCSEC, 5.seconds)
                client.run("add-key", "PUBLIC_KEY" to friend.publicKey.toHex(), "ROLE" to "driver", "FORM_FACTOR" to "ios_device")
                val keys = (client.run("list-keys") as CommandResult.KeyList).keys
                val added = keys.single { it.publicKey.contentEquals(friend.publicKey) }
                assertEquals(Keys.Role.ROLE_DRIVER, added.role)
                assertEquals(Vcsec.KeyFormFactor.KEY_FORM_FACTOR_IOS_DEVICE, added.formFactor)

                val duplicate = assertFailsWith<KeychainException> {
                    client.run("add-key", "PUBLIC_KEY" to friend.publicKey.toHex(), "ROLE" to "driver", "FORM_FACTOR" to "ios_device")
                }
                assertEquals(
                    Vcsec.WhitelistOperation_information_E.WHITELISTOPERATION_INFORMATION_ATTEMPTING_TO_ADD_KEY_THAT_IS_ALREADY_ON_THE_WHITELIST,
                    duplicate.code,
                )
                client.run("remove-key", "PUBLIC_KEY" to friend.publicKey.toHex())
                assertEquals(1, (client.run("list-keys") as CommandResult.KeyList).keys.size)
            }
        }
    }

    @Test
    fun linkLossFailsPendingCommandsImmediately() {
        VehicleSimulator.builder().owner(owner.publicKey).asleep(wakeDelayMillis = 60_000).start().use { sim ->
            withClient(sim) { client ->
                client.startSession(Domain.VCSEC, 5.seconds)
                // Infotainment never answers; dropping the link must not leave the caller waiting.
                val started = System.nanoTime()
                val failure = async {
                    runCatching { client.startSession(Domain.INFOTAINMENT, 30.seconds) }.exceptionOrNull()
                }
                delay(500)
                client.disconnect()
                val error = failure.await()
                assertTrue(error is VehicleException, "expected a vehicle error, got $error")
                assertTrue(System.nanoTime() - started < 5_000_000_000L, "disconnect did not fail the handshake promptly")
            }
        }
    }
}
