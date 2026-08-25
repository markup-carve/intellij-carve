package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A COLON FENCE'S MARKER SEPARATOR IS A RUN OF SPACES.
 *
 * The div rule used `\s*` at every slot - before the type token, before a
 * quoted title, before a `[label]` - so it opened a container for input the
 * engines read as an ordinary paragraph. Measured against carve-js `8432165e`,
 * with carve-php and carve-rs agreeing byte for byte: `:::note`, `:::<TAB>note`,
 * `:::|`, `:::<TAB>[l]` and `::: note<TAB>"T"` all render as a paragraph, and
 * corpus 254 and 255 exist to pin exactly that. A tab belongs at the START of a
 * line and nowhere else on one.
 *
 * The one slot that takes NO separator is the bare `[label]`: `:::[l]` does
 * open a div, so that branch alone is zero-or-more.
 *
 * `::: >` is here because it is what surfaced the rule: the fenced block quote
 * (markup-carve/carve#1718) reaches this rule the way `::: |` does, so the
 * glued and tab-separated forms of the newest colon-fence token would have been
 * wrong on arrival.
 *
 * The fixture goldens next door pin whole token streams, which is what catches
 * an accidental change. These are the assertions that say what the grammar is
 * FOR: a golden agrees with whatever the grammar currently does, and the
 * `:::<TAB>figure` line had one for as long as it read as a container.
 */
class CarveColonFenceSeparatorTest {

    private val divScope = "markup.other.div.carve"
    private val figureScope = "markup.other.figure-group.carve"

    /** Whether the opener line of [src] carries either container scope. */
    private fun opensAContainer(src: String): Boolean {
        val firstLine = src.substringBefore('\n')
        var offset = 0
        for (token in CarveTextMateTokenizer.tokenize(src)) {
            val start = offset
            offset += token.text.length
            if (start >= firstLine.length) break
            if (!token.text.contains(":::")) continue

            return token.scope.contains(divScope) || token.scope.contains(figureScope)
        }
        return false
    }

    /** `opener line to whether the engines open a container for it`. */
    private val openers = listOf(
        "::: >" to true,
        ":::  >" to true,
        ":::>" to false,
        ":::\t>" to false,
        ":::\t >" to false,
        "::: \t>" to false,
        "::: |" to true,
        ":::|" to false,
        ":::\t|" to false,
        "::: note" to true,
        ":::note" to false,
        ":::\tnote" to false,
        "::: figure" to true,
        ":::\tfigure" to false,
        "::: [l]" to true,
        ":::[l]" to true,
        ":::\t[l]" to false,
        "::: note \"T\"" to true,
        "::: note\t\"T\"" to false,
        "::: note \"T\" [l]" to true,
        "::: note \"T\"\t[l]" to false,
    )

    @Test
    fun everySlotTakesASpaceRun() {
        for ((opener, opens) in openers) {
            val shown = opener.replace("\t", "<TAB>")
            assertEquals(
                "$shown should ${if (opens) "open a container" else "stay paragraph text"}",
                opens,
                opensAContainer("$opener\nx\n:::\n"),
            )
        }
    }

    @Test
    fun theFencedQuoteOpenerIsScoped() {
        // Not which scope: `>` is a type token here like any other, and the
        // grammar has no separate node for the quote. That it opens a container
        // at all is the property the engines have and this grammar had to gain.
        assertTrue(opensAContainer("::: >\nNotes:\n:::\n"))
    }
}
