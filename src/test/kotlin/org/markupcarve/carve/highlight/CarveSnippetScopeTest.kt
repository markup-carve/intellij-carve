package org.markupcarve.carve.highlight

import org.junit.Assert.assertEquals
import org.junit.Test
import org.markupcarve.carve.corpus.CarveTextMateTokenizer

class CarveSnippetScopeTest {
    private fun covered(source: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(source)
            .filter { scope in it.scope.split(' ') }
            .joinToString("") { it.text }

    @Test
    fun `definitions and uses have snippet scopes`() {
        val source = "{\$ver}: 0.2.3\n\nUse {{ \$ver }}.\n"
        assertEquals("{\$ver}: 0.2.3", covered(source, "meta.snippet.definition.carve"))
        assertEquals("{{ \$ver }}", covered(source, "meta.snippet.use.carve"))
        assertEquals("ver\$ver", covered(source, "variable.other.snippet.carve"))
        assertEquals("", covered(source, "string.other.link.include.carve"))
    }

    @Test
    fun `include paths keep include scopes`() {
        assertEquals("", covered("Use {{ chapter.crv }}.\n", "meta.snippet.use.carve"))
        assertEquals("a_b", covered("{\$a_b}: value\n", "variable.other.snippet.carve"))
    }
}
