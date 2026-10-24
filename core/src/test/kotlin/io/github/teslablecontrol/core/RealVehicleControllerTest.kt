package io.github.teslablecontrol.core

import io.github.teslablecontrol.protocol.crypto.SoftwareEcdhKey
import io.github.teslablecontrol.protocol.testing.TcpTransport
import io.github.teslablecontrol.protocol.testing.VehicleSimulator
import io.github.teslablecontrol.protocol.vehicle.CommandResult
import io.github.teslablecontrol.protocol.vehicle.StateCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The controller's orchestration (connect, secure, wake, refresh) against the reference simulator. */
class RealVehicleControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val key = SoftwareEcdhKey.generate()
    private val store = InMemorySessionStore()
    private val opened = Collections.synchronizedList(mutableListOf<TcpTransport>())

    @Before
    fun requireSimulator() {
        assumeTrue("vehicle simulator not built", VehicleSimulator.binary != null)
    }

    @After
    fun cleanUp() {
        scope.cancel()
    }

    private fun controller(
        sim: VehicleSimulator,
        autoWake: Boolean = true,
        phoneKey: SoftwareEcdhKey = key,
    ) = RealVehicleController(
        vin = VehicleSimulator.VIN,
        linkFactory = LinkFactory { sim.connect().also(opened::add) },
        keyProvider = { phoneKey },
        sessionStore = store,
        scope = scope,
        config = ControllerConfig(autoWake = { autoWake }, followUpDelay = 50.milliseconds),
    )

    /** Records each stage the controller reports, in order. */
    private fun stagesOf(controller: VehicleController): MutableList<Stage> {
        val stages = Collections.synchronizedList(mutableListOf<Stage>())
        scope.launch {
            controller.state.collect { state -> state.activity?.stage?.let { if (stages.lastOrNull() != it) stages += it } }
        }
        return stages
    }

    /** After this phone's key is replaced, the old link (made with the old key) mustn't be reused. */
    @Test
    fun aNewKeyGetsANewLink(): Unit = runBlocking {
        val second = SoftwareEcdhKey.generate()
        VehicleSimulator.builder().owner(key.publicKey).owner(second.publicKey).start().use { sim ->
            var current = key
            val controller = RealVehicleController(
                vin = VehicleSimulator.VIN,
                linkFactory = LinkFactory { sim.connect().also(opened::add) },
                keyProvider = { current },
                sessionStore = store,
                scope = scope,
                config = ControllerConfig(followUpDelay = 50.milliseconds),
            )
            assertIs<CommandOutcome.Success>(controller.execute("lock"))
            assertEquals(1, opened.size)

            current = second
            assertIs<CommandOutcome.Success>(controller.execute("lock"))
            assertEquals(2, opened.size, "the controller should reconnect with the new key")

            controller.resetKey()
            assertEquals(LinkStatus.DISCONNECTED, controller.state.value.link)
            assertEquals(null, controller.state.value.keyPaired)
            assertTrue(store.load(VehicleSimulator.VIN).isEmpty(), "cached sessions belong to the old key")
        }
    }

    @Test
    fun commandsConnectAndSecureOnDemand(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            val stages = stagesOf(controller)
            assertIs<CommandOutcome.Success>(controller.execute("unlock"))
            assertEquals(LinkStatus.CONNECTED, controller.state.value.link)
            assertEquals(true, controller.state.value.keyPaired)
            assertTrue(stages.containsAll(listOf(Stage.CONNECTING, Stage.SECURING, Stage.SENDING)), "stages: $stages")

            // The follow-up body read shows the result without the caller asking.
            delay(500)
            assertEquals(false, controller.state.value.snapshot.isLocked)
            assertIs<CommandOutcome.Success>(controller.execute("lock"))
            delay(500)
            assertEquals(true, controller.state.value.snapshot.isLocked)
            assertTrue(store.load(VehicleSimulator.VIN).isNotEmpty(), "session cache was not saved")
        }
    }

    @Test
    fun infotainmentCommandsWakeTheCar(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).asleep(wakeDelayMillis = 1200).start().use { sim ->
            val controller = controller(sim)
            val stages = stagesOf(controller)
            val outcome = controller.execute("climate-set-temp", mapOf("TEMP" to "22.5"))
            assertIs<CommandOutcome.Success>(outcome, "$outcome")
            assertTrue(Stage.WAKING in stages, "stages: $stages")
            assertIs<CommandOutcome.Success>(controller.refresh(listOf(StateCategory.CLIMATE)))
            assertEquals(22.5f, controller.state.value.snapshot.driverTempSetting)
        }
    }

    @Test
    fun withoutAutoWakeSleepingCarIsReported(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).asleep().start().use { sim ->
            val controller = controller(sim, autoWake = false)
            val outcome = controller.execute("climate-on")
            assertIs<CommandOutcome.Failure>(outcome)
            assertEquals(FailureKind.ASLEEP, outcome.kind)
            assertEquals(true, controller.state.value.snapshot.isAsleep)
            // State reads don't wake the car either; locks still work while it sleeps.
            assertEquals(FailureKind.ASLEEP, (controller.refresh(listOf(StateCategory.CHARGE)) as CommandOutcome.Failure).kind)
            assertIs<CommandOutcome.Success>(controller.execute("unlock"))
        }
    }

    @Test
    fun unpairedKeyIsReportedAndPairingCompletes(): Unit = runBlocking {
        VehicleSimulator.builder().pairingDelay(800).start().use { sim ->
            val phone = SoftwareEcdhKey.generate()
            val controller = controller(sim, phoneKey = phone)
            val denied = controller.execute("lock")
            assertIs<CommandOutcome.Failure>(denied)
            assertEquals(FailureKind.NOT_PAIRED, denied.kind)
            assertEquals(false, controller.state.value.keyPaired)
            assertEquals(false, controller.checkPaired())

            assertIs<CommandOutcome.Success>(controller.requestPairing("owner"))
            var paired: Boolean? = false
            repeat(20) { if (paired != true) { delay(250); paired = controller.checkPaired() } }
            assertEquals(true, paired)
            assertIs<CommandOutcome.Success>(controller.execute("lock"))
        }
    }

    @Test
    fun reconnectsAfterTheLinkDrops(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            assertIs<CommandOutcome.Success>(controller.execute("honk"))
            // Simulate walking out of range.
            opened.last().close()
            delay(200)
            assertEquals(LinkStatus.DISCONNECTED, controller.state.value.link)
            val stages = stagesOf(controller)
            assertIs<CommandOutcome.Success>(controller.execute("flash-lights"))
            assertEquals(2, opened.size)
            // The cached session made a second handshake unnecessary.
            assertFalse(Stage.SECURING in stages, "stages: $stages")
        }
    }

    @Test
    fun attachedLinkIsUsedAndDuplicatesAreClosed(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            val background = sim.connect()
            assertTrue(controller.attach(background))
            assertEquals(LinkStatus.CONNECTED, controller.state.value.link)
            assertIs<CommandOutcome.Success>(controller.execute("lock"))
            assertTrue(opened.isEmpty(), "the attached link should have been used")
            // A second link while one is working is refused (and closed).
            assertFalse(controller.attach(sim.connect()))
        }
    }

    @Test
    fun invalidInputNeverReachesTheCar(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            val outcome = controller.execute("valet-mode-on", mapOf("PIN" to "12"))
            assertIs<CommandOutcome.Failure>(outcome)
            assertEquals(FailureKind.INVALID_INPUT, outcome.kind)
            assertTrue(opened.isEmpty())
        }
    }

    @Test
    fun refusalsExplainWhy(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            val outcome = controller.execute("charging-start")
            assertIs<CommandOutcome.Failure>(outcome)
            assertEquals(FailureKind.REFUSED, outcome.kind)
            assertEquals("cable not connected", outcome.message)
            assertEquals(outcome, controller.state.value.lastFailure)
        }
    }

    @Test
    fun keysAndStateRoundTrip(): Unit = runBlocking {
        VehicleSimulator.builder().owner(key.publicKey).start().use { sim ->
            val controller = controller(sim)
            val keys = controller.execute("list-keys")
            assertIs<CommandOutcome.Success>(keys)
            assertEquals(1, (keys.result as CommandResult.KeyList).keys.size)
            assertIs<CommandOutcome.Success>(controller.refresh(StateCategory.entries, wake = true))
            val snapshot = controller.state.value.snapshot
            assertEquals(78, snapshot.batteryLevel)
            assertEquals(StateCategory.entries.toSet(), snapshot.categories.keys)
        }
    }

    @Test
    fun demoControllerBehavesLikeACar(): Unit = runBlocking {
        val demo = DemoVehicleController(stepDelay = 1.milliseconds)
        assertIs<CommandOutcome.Success>(demo.execute("unlock"))
        assertEquals(false, demo.state.value.snapshot.isLocked)
        assertIs<CommandOutcome.Success>(demo.execute("charging-set-limit", mapOf("PERCENT" to "90")))
        assertEquals(90, demo.state.value.snapshot.chargeLimit)
        assertEquals(FailureKind.REFUSED, (demo.execute("charging-start") as CommandOutcome.Failure).kind)
        assertEquals(FailureKind.INVALID_INPUT, (demo.execute("valet-mode-on", mapOf("PIN" to "x")) as CommandOutcome.Failure).kind)
        assertIs<CommandOutcome.Success>(demo.execute("state", mapOf("CATEGORY" to "climate")))
    }
}
