package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins construct identity where a broader sibling rule can otherwise appear to cover it. */
class CarveConstructAttributionTest {
    private fun scopesOf(src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("Test input has no $needle", at >= 0)
        val end = at + needle.length
        var offset = 0
        return buildString {
            for (token in CarveTextMateTokenizer.tokenize(src)) {
                val start = offset
                offset += token.text.length
                if (start < end && offset > at) append(token.scope).append(' ')
            }
        }
    }

    @Test
    fun referenceImagesAreImagesRatherThanLinks() {
        for (src in listOf("![alt][ref]\n", "![alt][]\n")) {
            val scopes = scopesOf(src, "alt")
            assertTrue("reference image lost image identity: $scopes", scopes.contains("meta.image.reference.carve"))
            assertFalse("reference image was colored as a link: $scopes", scopes.contains("meta.link.reference.carve"))
        }
    }

    @Test
    fun footnoteDefinitionsAreNotLinkDefinitions() {
        val scopes = scopesOf("[^note]: body text\n", "body")
        assertTrue("footnote definition lost its identity: $scopes", scopes.contains("meta.footnote.definition.carve"))
        assertFalse("footnote body was colored as a URL: $scopes", scopes.contains("markup.underline.link.carve"))
    }

    @Test
    fun symbolsCarryTheirOwnScope() {
        val scopes = scopesOf("A :warning: symbol.\n", ":warning:")
        assertTrue("symbol lost its identity: $scopes", scopes.contains("constant.language.symbol.carve"))
    }

    /**
     * The reserved include directive is one construct, not the constructs its selector
     * happens to be spelled with (spec PART 9 §19, markup-carve/carve#291).
     *
     * `#section` IS a tag everywhere else and `@key` IS a mention, so before the rule
     * existed the grammar shredded `{{ chapters/intro.crv #intro }}` into a path, a
     * hashtag and punctuation - the same defect that `cross-reference` exists to fix for
     * `</#id>`. An assertion rather than a golden alone: a golden agrees with whatever
     * the grammar does, and the wrong reading would have snapshotted just as cleanly.
     */
    @Test
    fun theIncludeDirectiveIsOneConstruct() {
        val src = "See {{ chapters/intro.crv #intro }} here.\n"
        val directive = "{{ chapters/intro.crv #intro }}"
        val scopes = scopesOf(src, directive)
        assertTrue(
            "the directive carries no directive scope: $scopes",
            scopes.contains("meta.directive.include.carve"),
        )
    }

    @Test
    fun theIncludeSelectorIsNotATag() {
        val src = "See {{ chapters/intro.crv #intro }} here.\n"
        val scopes = scopesOf(src, "#intro")
        assertFalse(
            "the directive's section selector was coloured as a hashtag: $scopes",
            scopes.contains("entity.name.tag.hashtag.carve"),
        )
        assertFalse(
            "the directive's section selector kept a tag scope: $scopes",
            scopes.contains(".tag."),
        )
        assertTrue(
            "the section selector lost its own scope: $scopes",
            scopes.contains("entity.name.section.include.carve"),
        )
    }

    /** A tag outside a directive is still a tag - the rule narrows nothing else. */
    @Test
    fun anOrdinaryTagIsStillATag() {
        val scopes = scopesOf("See #intro here.\n", "#intro")
        assertTrue("an ordinary hashtag lost its scope: $scopes", scopes.contains("entity.name.tag.hashtag.carve"))
    }

    /** An option slot reads as a mention the same way a section reads as a tag. */
    @Test
    fun theIncludeOptionIsNotAMention() {
        val src = "See {{ chapters/intro.crv @level:2 }} here.\n"
        val scopes = scopesOf(src, "@level")
        assertFalse(
            "the directive's option was coloured as a mention: $scopes",
            scopes.contains("entity.name.mention.carve"),
        )
        assertTrue(
            "the option lost its own scope: $scopes",
            scopes.contains("variable.parameter.include.carve"),
        )
    }

    /** Inside a code span the directive is verbatim text, like every other construct. */
    @Test
    fun aDirectiveInsideACodeSpanStaysVerbatim() {
        val scopes = scopesOf("`{{ chapters/intro.crv #intro }}`\n", "#intro")
        assertFalse(
            "a directive inside a code span was scoped as a directive: $scopes",
            scopes.contains("meta.directive.include.carve"),
        )
    }
}
