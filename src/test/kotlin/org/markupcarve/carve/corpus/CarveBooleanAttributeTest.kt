package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A BARE boolean attribute may not start with an underscore.
 *
 * `identifier` admits a leading `_`, so `{_x_}` parsed both as the boolean
 * attribute `_x_` and as a forced underline; the bare form gives the collision
 * up (spec PART 9 §14, markup-carve/carve#1450). The cost is `{_foo}`, which
 * was an attribute block and is now text - measured through the engine bundled
 * in this plugin, `{_foo}` renders `<p>{_foo}</p>` and `{_x_}` renders
 * `<p><u>x</u></p>`.
 *
 * ONLY THE BARE FORM IS NARROWED. `{_k=1}`, `{_="on click"}`, `{#_id}` and
 * `{._c}` all keep their leading underscore, and none of them can be read as an
 * underline because none of them ends `_}`. The one underscore attribute in the
 * wild, hyperscript's `_="on click …"`, is a key/value and is unaffected.
 *
 * THE RULE IS SPELLED AT SEVEN SITES, which is why the assertions run over all
 * of them: the grammar's attribute rule, its bullet and ordered marker rules
 * (each spelling the item alternation twice, for the first item and the
 * repetition), and `CarveMarkerScanner`, which drives the annotator. A fix to
 * one of them leaves `-{_k} x` coloured as a list item on a line the engine
 * renders as a paragraph, so the fixture carries the marker forms too.
 *
 * Assertions rather than a golden alone: a golden agrees with whatever the
 * grammar does, and this defect shipped with three goldens pinning it.
 */
class CarveBooleanAttributeTest {

    private val attributes = "meta.attributes.carve"
    private val underline = "markup.underline.text.carve"
    private val list = "markup.list."

    /** Every scope carried by the tokens covering [needle], joined into one string. */
    private fun scopesOf(src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("Test input has no ${needle.replace("\n", "\\n")}", at >= 0)
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
    fun aBareUnderscoreBooleanIsNotAnAttributeBlock() {
        val src = "{_foo}\npara\n"
        assertFalse(
            "`{_foo}` renders as text, so it must carry no attribute scope: ${scopesOf(src, "{_foo}")}",
            scopesOf(src, "{_foo}").contains(attributes),
        )
    }

    @Test
    fun anOrdinaryBooleanStillIs() {
        val src = "{disabled}\npara\n"
        assertTrue(
            "`{disabled}` is an attribute block: ${scopesOf(src, "{disabled}")}",
            scopesOf(src, "{disabled}").contains(attributes),
        )
    }

    @Test
    fun onlyTheBareFormIsNarrowed() {
        val kept = listOf(
            "a key" to "{_k=1}\npara\n",
            "a quoted key" to "{_=\"on click\"}\npara\n",
            "an id" to "{#_id}\npara\n",
            "a class" to "{._c}\npara\n",
        )
        for ((what, src) in kept) {
            val block = src.substringBefore('\n')
            assertTrue(
                "$what keeps its leading underscore and stays an attribute block: ${scopesOf(src, block)}",
                scopesOf(src, block).contains(attributes),
            )
        }
    }

    @Test
    fun theBracedUnderlineIsReachable() {
        val src = "{_x_}\n"
        val scopes = scopesOf(src, "{_x_}")
        assertTrue("`{_x_}` is a forced underline: $scopes", scopes.contains(underline))
        assertFalse("`{_x_}` is not an attribute block: $scopes", scopes.contains(attributes))
    }

    @Test
    fun aGluedMarkerFollowsTheSameRule() {
        val prose = listOf(
            "a bullet" to "-{_k} x\n",
            "an ordered marker" to "1.{_k} x\n",
        )
        for ((what, src) in prose) {
            val scopes = scopesOf(src, src.substringBefore(' '))
            assertFalse(
                "$what glued to `{_k}` is a paragraph, so nothing on the line is a marker: $scopes",
                scopes.contains(list),
            )
            assertFalse("$what glued to `{_k}` carries no attribute block either: $scopes", scopes.contains(attributes))
        }

        val items = listOf(
            "a bullet with an ordinary boolean" to "-{k} x\n",
            "a bullet with an underscore key" to "-{_k=1} x\n",
            "an ordered marker with an underscore key" to "1.{_k=1} x\n",
        )
        for ((what, src) in items) {
            val scopes = scopesOf(src, src.substringBefore('{'))
            assertTrue("$what is a list item: $scopes", scopes.contains(list))
        }
    }
}
