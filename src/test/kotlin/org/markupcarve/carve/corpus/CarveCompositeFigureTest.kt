package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bare `::: figure` opener is a composite figure, and an opener carrying a
 * quoted title or a `[label]` is not (PART 9 §4c, markup-carve/carve#1215;
 * tracked as markup-carve/carve-grammars#222).
 *
 * The fixture golden next door pins the whole token stream, which is what
 * catches an accidental change. These are the assertions that say what the
 * grammar is *for*: a golden agrees with whatever the grammar currently does,
 * so on its own it cannot tell a rule that distinguishes the two readings from
 * one that never reaches the second.
 */
class CarveCompositeFigureTest {

    private val groupScope = "markup.other.figure-group.carve"
    private val groupKind = "entity.name.type.figure-group.carve"
    private val divScope = "markup.other.div.carve"
    private val divKind = "entity.name.type.div.carve"

    /**
     * Every scope carried by the tokens covering occurrence [nth] of [needle],
     * joined into one string. Reading the scopes over a span rather than a
     * single token keeps the assertions independent of where the engine happens
     * to split a line.
     */
    private fun scopesOf(src: String, needle: String, nth: Int = 0): String {
        var at = -1
        repeat(nth + 1) { at = src.indexOf(needle, at + 1) }
        assertTrue("Test input has no occurrence ${nth + 1} of ${needle.replace("\n", "\\n")}", at >= 0)
        val end = at + needle.length
        val sb = StringBuilder()
        var offset = 0
        for (token in CarveTextMateTokenizer.tokenize(src)) {
            val start = offset
            offset += token.text.length
            if (start < end && offset > at) sb.append(token.scope).append(' ')
        }
        return sb.toString()
    }

    @Test
    fun bareOpenerIsACompositeFigureAndNotAGenericContainer() {
        val src = "::: figure\n![a](a.png)\n^ (a) One\n:::\n"
        val opener = scopesOf(src, "::: figure")

        assertTrue("A bare `::: figure` opener must scope as a composite figure: $opener", opener.contains(groupScope))
        assertTrue("Its kind word must carry the figure-group scope: $opener", opener.contains(groupKind))
        assertFalse("A bare opener is not the generic container: $opener", opener.contains(divKind))
    }

    @Test
    fun theBodyAndPanelCaptionsSitInsideTheGroup() {
        val src = "::: figure\n![a](a.png)\n^ (a) One\n:::\n"

        assertTrue("A panel image belongs to the group", scopesOf(src, "a.png").contains(groupScope))
        val panel = scopesOf(src, "(a) One")
        assertTrue("A panel caption belongs to the group: $panel", panel.contains(groupScope))
        assertTrue("A panel caption is still a caption: $panel", panel.contains("caption"))
    }

    @Test
    fun aQuotedTitleMakesItTheGenericContainer() {
        val src = "::: figure \"A titled figure div\"\n![a](a.png)\n:::\n"
        val opener = scopesOf(src, "::: figure")

        assertFalse("An opener carrying a title is not a composite figure: $opener", opener.contains(groupScope))
        assertFalse("...and its kind word keeps the generic scope: $opener", opener.contains(groupKind))
        assertTrue("It stays the generic container: $opener", opener.contains(divScope))
        assertTrue("...with the generic kind scope: $opener", opener.contains(divKind))
        assertTrue(
            "The title is preserved as a quoted string",
            scopesOf(src, "\"A titled figure div\"").contains("string.quoted.double.carve"),
        )
    }

    @Test
    fun aLabelMakesItTheGenericContainer() {
        val src = "::: figure [g]\n![a](a.png)\n:::\n"
        val opener = scopesOf(src, "::: figure")

        assertFalse("An opener carrying a label is not a composite figure: $opener", opener.contains(groupScope))
        assertFalse("...and its kind word keeps the generic scope: $opener", opener.contains(groupKind))
        assertTrue("It stays the generic container: $opener", opener.contains(divScope))
        assertTrue("...with the generic kind scope: $opener", opener.contains(divKind))
        assertTrue("The label is preserved", scopesOf(src, "[g]").contains("entity.name.label.carve"))
    }

    /**
     * The separator is a SPACE run, never a tab (grammar.ebnf PART 7, MARKER
     * SEPARATORS; corpus 254 renders `:::<TAB>note` as a paragraph). Spelling
     * the new rule `[ \t]+` - the spelling the generic rules beside it use -
     * would give this line the group scope.
     */
    @Test
    fun aTabSeparatedOpenerIsNotACompositeFigure() {
        val src = ":::\tfigure\nbody\n:::\n"
        val opener = scopesOf(src, ":::\tfigure")

        assertFalse("A tab does not separate a marker: $opener", opener.contains(groupScope))
        assertFalse("...so the kind word keeps the generic scope: $opener", opener.contains(groupKind))
        assertTrue("The line reads exactly as it did before this rule existed: $opener", opener.contains(divKind))
    }

    /**
     * GROUPS DO NOT NEST (PART 9 §4c): a bare `::: figure` at any depth inside an
     * open group is a generic container. The outer fence is longer so the inner
     * opener is reached at all - a `:::` line inside a `:::` container closes it
     * (PART 9 §12).
     */
    @Test
    fun aBareOpenerInsideAnOpenGroupIsAGenericContainer() {
        val src = ":::: figure\n![o](o.png)\n::: figure\n![i](i.png)\n:::\n::::\n"
        val inner = scopesOf(src, "::: figure", nth = 1)

        assertTrue("The inner opener is still inside the group: $inner", inner.contains(groupScope))
        assertTrue("...but it is the generic container: $inner", inner.contains(divScope))
        assertTrue("...with the generic kind scope: $inner", inner.contains(divKind))
        assertFalse("A group does not nest: $inner", inner.contains(groupKind))
    }

    /** A `:::` line inside a `:::` group closes it rather than opening a nested one. */
    @Test
    fun anEqualLengthFenceClosesTheGroup() {
        val src = "::: figure\n![a](a.png)\n:::\nafter\n"

        assertFalse(
            "Text below an equal-length closer is outside the group",
            scopesOf(src, "after").contains(groupScope),
        )
    }

    /**
     * The group caption is an ordinary `^ ` line one line below the CLOSING
     * fence, so it lands outside the group's span and the document-level caption
     * rule claims it. That claim is an over-approximation and stays one: a `^ `
     * line after any other `:::` closer is ordinary paragraph content in the
     * language, but it sits outside every container's begin/end span, so no
     * amount of container state can tell the two apart in a TextMate grammar.
     */
    @Test
    fun theGroupCaptionAfterTheCloserScopesAsACaptionOutsideTheGroup() {
        val afterAGroup = scopesOf("::: figure\n![a](a.png)\n:::\n^ A caption line\n", "A caption line")

        assertTrue("The caption position works: $afterAGroup", afterAGroup.contains("caption"))
        assertFalse("The group has already closed at the fence: $afterAGroup", afterAGroup.contains(groupScope))

        val afterANote = scopesOf("::: note\nbody\n:::\n^ A caption line\n", "A caption line")
        assertEquals(
            "The over-approximation is symmetric: the same `^ ` line after a non-figure closer " +
                "gets the same scopes, which is this grammar's documented limit rather than a " +
                "regression introduced here.",
            afterAGroup,
            afterANote,
        )
    }
}
