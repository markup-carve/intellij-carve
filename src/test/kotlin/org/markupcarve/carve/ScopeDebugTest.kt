package org.markupcarve.carve

import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTheme
import org.markupcarve.carve.corpus.CarveTextMateTokenizer
import org.junit.Test

class ScopeDebugTest {
    private fun resolvedKey(scope: String): String? =
        TextMateTheme.INSTANCE.rules.map { it.toString() }
            .filter { scope.contains(it) }
            .maxByOrNull { it.length }
            ?.let { TextMateTheme.INSTANCE.getTextAttributesKey(it)?.externalName }

    @Test
    fun debug() {
        for (src in listOf("{#top}", "%% a comment line\n", "- item\n")) {
            println("### INPUT: ${src.replace("\n", "\\n")}")
            for (t in CarveTextMateTokenizer.tokenize(src)) {
                println("   %-10s | %-55s | %s".format(
                    "\"${t.text.replace("\n", "\\n")}\"", t.scope, resolvedKey(t.scope) ?: "-"))
            }
        }
    }
}
