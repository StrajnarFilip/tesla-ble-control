package io.github.teslablecontrol.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guard the translation contract without needing an emulator or Android resource stubs. */
class LocalizationTest {
    private val resources = File("src/main/res")
    private val languages = setOf("sl", "hr", "fr", "de", "nb", "da", "it", "es")
    private val placeholders = Regex("%[1-9][0-9]*\\$[sdf]|%%")

    private fun strings(directory: String, translatableOnly: Boolean = false): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val files = File(resources, directory).listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        assertTrue("No string resources in $directory", files.isNotEmpty())
        files.forEach { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                if (translatableOnly && element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                assertFalse("Duplicate $name in $directory", result.containsKey(name))
                result[name] = element.textContent
            }
        }
        return result
    }

    @Test
    fun everyLanguageTranslatesEveryUserFacingString() {
        val english = strings("values", translatableOnly = true)
        languages.forEach { language ->
            val translated = strings("values-$language")
            assertEquals("Missing or unexpected strings in $language", english.keys, translated.keys)
            translated.forEach { (name, text) ->
                assertTrue("Empty $language/$name", text.isNotBlank())
                assertFalse("Unresolved translation marker in $language/$name", Regex("__X[0-9]+__|⟦[0-9]+⟧").containsMatchIn(text))
            }
        }
    }

    @Test
    fun translationsPreserveFormatArgumentsAndParagraphs() {
        val english = strings("values", translatableOnly = true)
        languages.forEach { language ->
            strings("values-$language").forEach { (name, text) ->
                val source = english.getValue(name)
                assertEquals(
                    "$language/$name format arguments",
                    placeholders.findAll(source).map { it.value }.sorted().toList(),
                    placeholders.findAll(text).map { it.value }.sorted().toList(),
                )
                assertEquals("$language/$name paragraph breaks", source.windowed(2).count { it == "\\n" }, text.windowed(2).count { it == "\\n" })
            }
        }
    }

    @Test
    fun androidLanguagePickerListsExactlyTheSupportedLanguages() {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "xml/locales_config.xml")).getElementsByTagName("locale")
        val declared = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        assertEquals(languages + "en", declared.toSet())
        assertEquals(declared.size, declared.toSet().size)
    }
}
