package io.github.antocanza.iptvreceiver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcesTest {
    private val resources = File("src/main/res")
    private val qualifiers = listOf("it", "es", "fr", "de", "pt", "ar", "ru", "b+zh+Hans", "ja")
    private val placeholders = Regex("%[0-9]+\\$[sd]")

    @Test fun `every supported language has every app string with matching format arguments`() {
        val english = strings(File(resources, "values/strings.xml"))
        assertEquals(45, english.size)
        for (qualifier in qualifiers) {
            val translated = strings(File(resources, "values-$qualifier/strings.xml"))
            assertEquals("Missing or extra keys for $qualifier", english.keys, translated.keys)
            english.forEach { (key, default) ->
                val value = checkNotNull(translated[key])
                assertTrue("Empty translation: $qualifier/$key", value.isNotBlank())
                assertEquals("Format mismatch: $qualifier/$key",
                    placeholders.findAll(default).map { it.value }.sorted().toList(),
                    placeholders.findAll(value).map { it.value }.sorted().toList())
                if (key == "season_number") assertFalse(String.format(Locale.ROOT, value, 12).contains("%"))
                if (key == "episode_number") assertTrue(String.format(Locale.ROOT, value, 2, "Sample").contains("Sample"))
            }
        }
    }

    @Test fun `system language list and picker both expose the ten requested languages`() {
        val document = factory().newDocumentBuilder().parse(File(resources, "xml/locales_config.xml"))
        val nodes = document.getElementsByTagName("locale")
        val tags = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        assertEquals(listOf("en", "it", "es", "fr", "de", "pt", "ar", "ru", "zh-Hans", "ja"), tags)
        assertEquals(tags, supportedLanguages.keys.toList())
    }

    private fun strings(file: File): Map<String, String> {
        val nodes = factory().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun factory() = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
}
