package io.github.teslablecontrol.protocol

import com.tesla.generated.carserver.server.CarServer
import io.github.teslablecontrol.protocol.commands.Args
import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.protocol.commands.SessionRequirement
import io.github.teslablecontrol.protocol.crypto.SoftwareEcdhKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArgsAndCatalogTest {
    @Test
    fun catalogHasEveryBleCommandOnce() {
        val ids = CommandCatalog.all.map { it.id }
        assertEquals(85, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            setOf("body-controller-state", "list-keys", "add-key-request", "session-info"),
            CommandCatalog.all.filter { it.requirement == SessionRequirement.NONE }.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyCommandBuildsFromItsDefaultsAndSamples() {
        val key = SoftwareEcdhKey.generate().publicKey.toHex()
        val samples = mapOf(
            "PUBLIC_KEY" to key, "TEMP" to "21.5", "PERCENT" to "80", "AMPS" to "16", "TIME" to "22:00",
            "DEPART_AT" to "07:30", "DAYS" to "weekdays", "LATITUDE" to "46.05", "LONGITUDE" to "14.5",
            "LEVEL" to "low", "SEAT" to "front-left", "POSITIONS" to "LR", "MODE" to "dog", "STATE" to "on",
            "VOLUME" to "5.5", "PIN" to "1234", "MPH" to "65", "SETTING" to "curfew", "DELAY" to "2h",
            "CATEGORY" to "charge", "NAME" to "Blue", "TYPE" to "home",
        )
        for (spec in CommandCatalog.all) {
            val values = spec.params.mapNotNull { param ->
                val value = when {
                    spec.id == "charging-schedule-add" && param.name == "TIME" -> "22:00-6:00"
                    spec.id == "sunroof" && param.name == "LEVEL" -> "15"
                    else -> samples[param.name] ?: param.default
                }
                value?.let { param.name to it }
            }.toMap()
            val request = spec.build(values) { 1_700_000_000 }
            assertTrue(request.payload().isNotEmpty() || spec.id == "session-info", "${spec.id} payload")
        }
    }

    @Test
    fun temperaturesMatchCliConversion() {
        assertEquals(21.0f, Args(mapOf("T" to "21")).temperatureCelsius("T"))
        assertEquals(21.0f, Args(mapOf("T" to "21C")).temperatureCelsius("T"))
        assertEquals((70f - 32f) * 5f / 9f, Args(mapOf("T" to "70f")).temperatureCelsius("T"))
        assertFailsWith<CommandException> { Args(mapOf("T" to "warm")).temperatureCelsius("T") }
    }

    @Test
    fun daysAndTimes() {
        assertEquals(62, Args(mapOf("D" to "weekdays")).days("D"))
        assertEquals(1 or 64, Args(mapOf("D" to "Sun, sat")).days("D"))
        assertEquals(22 * 60 to 6 * 60, Args(mapOf("R" to "22:00-6:00")).timeRange("R"))
        assertEquals(null to 6 * 60, Args(mapOf("R" to "-6:00")).timeRange("R"))
        assertEquals(20 * 60 + 32 to null, Args(mapOf("R" to "20:32-")).timeRange("R"))
        assertFailsWith<CommandException> { Args(mapOf("R" to "25:00")).minutesAfterMidnight("R") }
    }

    @Test
    fun goDurations() {
        assertEquals(7200, Args(mapOf("D" to "2h")).durationSeconds("D"))
        assertEquals(5400, Args(mapOf("D" to "1h30m")).durationSeconds("D"))
        assertEquals(90, Args(mapOf("D" to "1.5m")).durationSeconds("D"))
        assertFailsWith<CommandException> { Args(mapOf("D" to "soon")).durationSeconds("D") }
    }

    @Test
    fun valetOffToleratesAlreadyOff() {
        val request = CommandCatalog["valet-mode-off"].build(emptyMap()) as io.github.teslablecontrol.protocol.vehicle.VehicleRequest.CarServerAction
        assertEquals("already off", request.successIfErrorEndsWith)
        assertTrue(request.action.hasVehicleControlSetValetModeAction())
    }

    @Test
    fun pinsAreValidated() {
        assertFailsWith<CommandException> { CommandCatalog["valet-mode-on"].build(mapOf("PIN" to "12a4")) }
        val climateKeeper = CommandCatalog["climate-keeper"].build(mapOf("MODE" to "camp"))
            as io.github.teslablecontrol.protocol.vehicle.VehicleRequest.CarServerAction
        assertEquals(
            CarServer.HvacClimateKeeperAction.ClimateKeeperAction_E.ClimateKeeperAction_Camp,
            climateKeeper.action.hvacClimateKeeperAction.climateKeeperAction,
        )
    }
}
