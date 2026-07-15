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
}
