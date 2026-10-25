package io.github.teslablecontrol.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ui.commands.CommandInfos
import io.github.teslablecontrol.ui.format.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Exercise compiled Android resources, including fallback and printf escaping. No car is used. */
@RunWith(AndroidJUnit4::class)
class LocaleResourcesTest {
    @Test
    fun eachLanguageResolvesItsStringsAndFormatsCommandMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf(
            "sl" to ("Doseg" to "Ključi"),
            "hr" to ("Doseg" to "Ključevi"),
            "fr" to ("Autonomie" to "Clés"),
            "de" to ("Reichweite" to "Schlüssel"),
            "nb" to ("Rekkevidde" to "Nøkler"),
            "da" to ("Rækkevidde" to "Nøgler"),
            "it" to ("Autonomia" to "Chiavi"),
            "es" to ("Autonomía" to "Llaves"),
        )
        expected.forEach { (tag, words) ->
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(tag, words.first, localized.getString(R.string.home_range))
            assertEquals(tag, words.second, localized.getString(R.string.security_keys))
            assertEquals("Tesla BLE Control", localized.getString(R.string.app_name))
            assertEquals("HomeLink", localized.getString(R.string.controls_homelink))
            assertTrue(tag, localized.getString(R.string.commands_no_results, "TEST_QUERY").contains("TEST_QUERY"))
            val percentage = localized.getString(R.string.charging_to_standard_percent, 80)
            // A no-break space keeps the number and its percent sign on one line.
            assertTrue(tag, percentage.contains("80 %"))
            assertFalse(tag, percentage.contains("%%"))
            CommandInfos.all.forEach { command ->
                listOf(command.title, command.description, command.successText).forEach { id ->
                    assertTrue("$tag/${command.id}", localized.getString(id).isNotBlank())
                }
            }
        }
    }

    @Test
    fun relativeTimesReadInTheMiddleOfASentence() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("sl"))
            assertEquals("pred 2 minutama", relativeTime(0L, now = 2 * 60_000L))
            Locale.setDefault(Locale.UK)
            assertEquals("2 minutes ago", relativeTime(0L, now = 2 * 60_000L))
            assertEquals("3 hours ago", relativeTime(0L, now = 3 * 3_600_000L))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
