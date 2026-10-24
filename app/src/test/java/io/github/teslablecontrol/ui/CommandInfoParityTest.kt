package io.github.teslablecontrol.ui

import io.github.teslablecontrol.protocol.commands.CommandCatalog
import io.github.teslablecontrol.ui.commands.CommandInfos
import io.github.teslablecontrol.ui.commands.PIN_COMMANDS
import io.github.teslablecontrol.ui.commands.ParamLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandInfoParityTest {
    @Test
    fun everyCatalogCommandHasWordsAndEveryEntryIsInTheCatalog() {
        val catalog = CommandCatalog.all.map { it.id }.toSet()
        val described = CommandInfos.all.map { it.id }.toSet()
        assertEquals("described but not in the catalog", emptySet<String>(), described - catalog)
        assertEquals("in the catalog but not described", emptySet<String>(), catalog - described)
        assertEquals(85, catalog.size)
    }

    @Test
    fun everyParameterHasALabel() {
        val unlabelled = CommandCatalog.all.flatMap { it.params }.map { it.name }.distinct().filterNot(ParamLabels::has)
        assertEquals(emptyList<String>(), unlabelled)
    }

    @Test
    fun pinCommandsTakeAPin() {
        PIN_COMMANDS.forEach { id -> assertEquals(id, listOf("PIN"), CommandCatalog[id].params.map { it.name }) }
    }

    @Test
    fun descriptionsAndSuccessTextsAreFilledIn() {
        CommandInfos.all.forEach {
            assertTrue(it.id, it.title.isNotBlank() && it.description.isNotBlank() && it.successText.isNotBlank())
        }
    }

    /** Catches a mistyped command id in a screen, which would otherwise fail only when tapped. */
    @Test
    fun commandIdsUsedByScreensExist() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sources not found from ${File(".").absolutePath}", sources.size > 20)
        val call = Regex("""(?:\bsend|\brun|CommandRoute|execute)\(\s*"([a-z0-9-]+)"""")
        val unknown = sources.flatMap { file ->
            call.findAll(file.readText()).map { it.groupValues[1] }.filter { CommandCatalog.find(it) == null }.map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), unknown)
    }
}
