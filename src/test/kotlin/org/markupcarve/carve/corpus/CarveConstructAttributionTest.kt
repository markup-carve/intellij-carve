package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
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

    /** Every character the grammar gave [scope], concatenated in document order. */
    private fun textCarrying(src: String, scope: String): String =
        buildString {
            for (token in CarveTextMateTokenizer.tokenize(src)) {
                if (token.scope.contains(scope)) append(token.text)
            }
        }

    private fun optionValueIn(src: String): String = textCarrying(src, "constant.other.include.carve")

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

    /**
     * An option VALUE is an `attribute_value`, which the spec admits in a quoted form,
     * and a quoted value carries spaces (`spec/resources/grammar.ebnf`:
     * `quoted_value = '"', { escaped_char | (character - '"' - '\\' - newline) }, '"'`,
     * normative clause CARVE-P4-006, "A QUOTED VALUE STOPS AT THE NEWLINE").
     *
     * Read as a bare run of non-space characters the value stopped at the first space,
     * so `@label:"two words"` scoped `"two` and left `words"` out of the option. An
     * assertion rather than the golden alone: a golden agrees with whatever the grammar
     * does, and the truncated reading snapshotted just as cleanly.
     */
    @Test
    fun aDoubleQuotedOptionValueHoldingASpaceIsOneValue() {
        val src = "See {{ chapters/intro.crv @label:\"two words\" }} here.\n"
        assertEquals("the quoted option value was cut at the space", "\"two words\"", optionValueIn(src))
    }

    @Test
    fun aSingleQuotedOptionValueHoldingASpaceIsOneValue() {
        val src = "See {{ chapters/intro.crv @label:'two words' }} here.\n"
        assertEquals("the quoted option value was cut at the space", "'two words'", optionValueIn(src))
    }

    @Test
    fun aDoubleQuotedOptionValueMayHoldAnApostrophe() {
        assertEquals(
            "a double-quoted value lost the apostrophe it holds",
            "\"it's here\"",
            optionValueIn("See {{ chapters/intro.crv @label:\"it's here\" }} here.\n"),
        )
    }

    @Test
    fun aSingleQuotedOptionValueMayHoldDoubleQuotes() {
        assertEquals(
            "a single-quoted value lost the double quotes it holds",
            "'say \"hi\"'",
            optionValueIn("See {{ chapters/intro.crv @label:'say \"hi\"' }} here.\n"),
        )
    }

    /**
     * The option's colon is capture 2 of a match whose capture 1 is the option NAME, so
     * it can only ever be the option's own colon. Admitting quoted values is what made
     * that reachable at all - an unanchored colon would split this value at `a:b` - so
     * it is asserted now rather than argued from the shape.
     */
    @Test
    fun aColonInsideAQuotedOptionValueDoesNotSplitIt() {
        val src = "See {{ chapters/intro.crv @label:\"a:b c\" }} here.\n"
        assertEquals("a colon inside the quoted value split it", "\"a:b c\"", optionValueIn(src))
    }

    /** The separator scope is the option's OWN colon and never a colon inside its value. */
    @Test
    fun theSeparatorScopeIsTheOptionsOwnColonOnly() {
        val src = "See {{ chapters/intro.crv @label:\"a:b c\" }} here.\n"
        assertEquals(
            "the separator scope reached a colon inside the value",
            ":",
            textCarrying(src, "keyword.control.separator.include.carve"),
        )
    }

    /**
     * Both quoted alternatives REQUIRE their closing quote, so an unterminated one falls
     * back to the unquoted run and still stops at the space instead of pairing with a
     * quote further along. This is the row that rules out the obvious wider spelling,
     * and it stays green under a revert to the unwidened rule for that reason.
     */
    @Test
    fun anUnterminatedQuoteFallsBackToTheUnquotedRun() {
        val src = "See {{ chapters/intro.crv @label:\"two words }} here.\n"
        assertEquals("an unterminated quote paired with something further along", "\"two", optionValueIn(src))
    }

    /** A backslash-escaped quote was already covered by the unquoted run - it holds no space. */
    @Test
    fun aBackslashEscapedQuoteStaysInsideTheValue() {
        val src = "See {{ chapters/intro.crv @label:\"a\\\"b\" }} here.\n"
        assertEquals("the escaped quote closed the value early", "\"a\\\"b\"", optionValueIn(src))
    }

    /**
     * The widening sits in capture 3's own patterns, which re-scan text the outer match
     * has already delimited, so the directive's EXTENT is untouched: a `}}` inside what
     * looks like a quoted value still closes the directive there rather than letting an
     * unbounded quoted run reach the second one (markup-carve/carve-grammars#412).
     */
    @Test
    fun aDirectiveStillClosesOnItsFirstCloser() {
        val src = "See {{ chapters/intro.crv @label:\"a }} more\" }} end\n"
        assertEquals(
            "the directive ran past its own closer",
            "{{ chapters/intro.crv @label:\"a }}",
            textCarrying(src, "meta.directive.include.carve"),
        )
    }

    /**
     * The quoted PATH alternative does not exclude the newline, and does not need to:
     * a TextMate `match` is applied to one line at a time, so an opening quote can never
     * pair with one further down the document. Measured rather than argued from the
     * property - this is the shape that broke the scanning grammars in
     * markup-carve/carve-grammars#411.
     */
    @Test
    fun aQuotedIncludePathCannotReachPastItsLine() {
        val src = "See {{ \"a\nnot a directive *bold* here\nb\" }} end\n"
        assertEquals(
            "a directive scope crossed a line boundary",
            "",
            textCarrying(src, "meta.directive.include.carve"),
        )
        assertTrue(
            "emphasis on the middle line stopped highlighting",
            textCarrying(src, "markup.bold.carve").contains("bold"),
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
