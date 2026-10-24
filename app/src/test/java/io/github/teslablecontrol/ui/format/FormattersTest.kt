package io.github.teslablecontrol.ui.format

import io.github.teslablecontrol.data.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormattersTest {
    @Before
    fun englishNumbers() = Locale.setDefault(Locale.US)

    @Test
    fun unitsFollowTheRegionUnlessChosen() {
        assertEquals(Units(fahrenheit = true, miles = true, psi = true), Units.resolve(UnitSystem.AUTO, Locale.US))
        assertEquals(Units(fahrenheit = false, miles = true, psi = false), Units.resolve(UnitSystem.AUTO, Locale.UK))
        assertEquals(Units(fahrenheit = false, miles = false, psi = false), Units.resolve(UnitSystem.AUTO, Locale.GERMANY))
        assertEquals(Units(fahrenheit = false, miles = false, psi = false), Units.resolve(UnitSystem.METRIC, Locale.US))
        assertEquals(Units(fahrenheit = true, miles = true, psi = true), Units.resolve(UnitSystem.IMPERIAL, Locale.GERMANY))
    }

    @Test
    fun temperatures() {
        val metric = Units.resolve(UnitSystem.METRIC)
        val imperial = Units.resolve(UnitSystem.IMPERIAL)
        assertEquals("21.5 °C", metric.temperature(21.5f))
        assertEquals("71 °F", imperial.temperature(21.5f))
        assertEquals("—", metric.temperature(null))
        assertEquals(21.5f, imperial.celsiusFrom(imperial.temperatureValue(21.5f)), 0.001f)
    }

    @Test
    fun distancesAndPressures() {
        assertEquals("161 km", Units.resolve(UnitSystem.METRIC).distanceFromMiles(100f))
        assertEquals("100 mi", Units.resolve(UnitSystem.IMPERIAL).distanceFromMiles(100f))
        assertEquals("2.9 bar", Units.resolve(UnitSystem.METRIC).pressureFromBar(2.9f))
        assertEquals("42 psi", Units.resolve(UnitSystem.IMPERIAL).pressureFromBar(2.9f))
        assertEquals("105 km/h", Units.resolve(UnitSystem.METRIC).speedFromMph(65.0))
    }

    @Test
    fun timesAndDays() {
        assertEquals("07:05", minutesToClock(425))
        assertEquals("00:00", minutesToClock(24 * 60))
        assertEquals("45 min", durationText(45))
        assertEquals("2 h", durationText(120))
        assertEquals("1 h 20 min", durationText(80))
        assertEquals("Every day", daysText(127))
        assertEquals("Weekdays", daysText(62))
        assertEquals("Sun, Wed", daysText(1 or 8))
        assertEquals("No days", daysText(0))
    }
}
