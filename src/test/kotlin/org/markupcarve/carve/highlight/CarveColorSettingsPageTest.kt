package org.markupcarve.carve.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarveColorSettingsPageTest {

    private val page = CarveColorSettingsPage()

    private val allKeys = listOf(
        CarveColors.HEADING_MARKER,
        CarveColors.LIST_MARKER,
        CarveColors.CONTINUATION_MARKER,
        CarveColors.DIV_MARKER,
        CarveColors.TABLE_PIPE,
        CarveColors.QUOTE_MARKER,
        CarveColors.FENCE_MARKER,
        CarveColors.HIGHLIGHT,
    )

    @Test
    fun everyColorKeyHasASettingsDescriptor() {
        val descriptorKeys = page.attributeDescriptors.map { it.key }.toSet()
        val missing = allKeys.filterNot { it in descriptorKeys }
        assertTrue("keys missing a Color Scheme descriptor: $missing", missing.isEmpty())
    }

    @Test
    fun everyDemoTagMapsToAKnownKey() {
        val map: Map<String, TextAttributesKey> = page.additionalHighlightingTagToDescriptorMap
        assertTrue("demo defines tags but the demo text has none", map.isNotEmpty())
        for ((tag, key) in map) {
            assertTrue("demo tag <$tag> maps to an unknown key", key in allKeys)
            assertTrue("demo text is missing tag <$tag>", page.demoText.contains("<$tag>"))
        }
    }

    @Test
    fun colorKeyExternalNamesAreUnique() {
        val names = allKeys.map { it.externalName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun highlightHasAHighContrastDefaultInLightAndDarkSchemes() {
        for (resource in listOf("CarveHighlightDefault.xml", "CarveHighlightDarcula.xml")) {
            val scheme = javaClass.classLoader
                .getResourceAsStream("colorSchemes/$resource")!!
                .bufferedReader().readText()

            assertTrue("$resource does not define the highlight key", scheme.contains("CARVE_HIGHLIGHT"))
            assertTrue("$resource lost the pale-yellow background", scheme.contains("FFF1A8"))
            assertTrue("$resource lost the dark foreground", scheme.contains("1F1F1F"))
            assertTrue("$resource lost bold text", scheme.contains("FONT_TYPE\" value=\"1"))
        }
    }
}
