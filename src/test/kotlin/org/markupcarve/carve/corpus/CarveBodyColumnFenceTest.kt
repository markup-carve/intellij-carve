package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A code fence at a list item's BODY column is a fenced block; an indented fence at
 * document level is not (markup-carve/intellij-carve#133).
 *
 * Both directions, because the fix is only a fix if it keeps the second one. carve-js
 * renders an indented fence at document level as a paragraph holding an inline code span,
 * so a top-level indent-anchored rule would have traded a correct answer for the wrong
 * one. `code-fence-in-list-item.crv` pins both as goldens; these assert the identity,
 * which a golden cannot - it agrees with whatever the grammar does, and the wrong reading
 * snapshotted just as cleanly for as long as it stood.
 */
class CarveBodyColumnFenceTest {

    private fun scopesOf(src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("test input has no $needle", at >= 0)
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

    private val inItem = "- item\n\n  ```php\n  *x*\n  ```\n"

    @Test
    fun aFenceAtAnItemsBodyColumnOpensAFencedBlock() {
        val scopes = scopesOf(inItem, "```php")
        assertTrue(
            "the opener is not a fenced block: $scopes",
            scopes.contains("markup.raw.block.fenced.code.carve"),
        )
        assertTrue(
            "the opener kept no begin marker: $scopes",
            scopes.contains("keyword.control.raw.begin.carve"),
        )
        assertFalse(
            "the opener is still the inline code span: $scopes",
            scopes.contains("markup.raw.inline.carve"),
        )
    }

    @Test
    fun theInfoStringIsALanguageRatherThanContent() {
        val scopes = scopesOf(inItem, "php")
        assertTrue("the info string is not a language: $scopes", scopes.contains("entity.name.type.language.carve"))
        assertFalse(
            "the info string is still inline code content: $scopes",
            scopes.contains("markup.raw.inline.content.carve"),
        )
    }

    @Test
    fun theCloserEndsTheBlock() {
        val scopes = scopesOf(inItem, "\n  ```\n")
        assertTrue("the closer kept no end marker: $scopes", scopes.contains("keyword.control.raw.end.carve"))
    }

    @Test
    fun theBlockBodyIsNotLiveMarkup() {
        val scopes = scopesOf(inItem, "*x*")
        assertTrue("the body left the block: $scopes", scopes.contains("markup.raw.block.fenced.code.carve"))
        assertFalse("the body was read as emphasis: $scopes", scopes.contains("markup.bold.carve"))
    }

    /** The item's own column, not column 0: a fence on a NESTED item's body reaches it too. */
    @Test
    fun aFenceAtANestedItemsBodyColumnOpensAFencedBlock() {
        val scopes = scopesOf("- outer\n  - inner\n\n    ```php\n    *x*\n    ```\n", "```php")
        assertTrue("a nested item's body column reaches nothing: $scopes", scopes.contains("markup.raw.block.fenced.code.carve"))
    }

    /**
     * The direction the rule must NOT reach. Anchored on the item's region, so at document
     * level the inline code span still wins - which is what carve-js renders.
     */
    @Test
    fun aDocumentLevelIndentedFenceIsStillAnInlineCodeSpan() {
        val scopes = scopesOf("  ```php\n  *x*\n  ```\n", "```php")
        assertTrue("the indented fence lost its inline reading: $scopes", scopes.contains("markup.raw.inline.carve"))
        assertFalse(
            "an indented fence at document level became a fenced block: $scopes",
            scopes.contains("markup.raw.block.fenced.code.carve"),
        )
    }

    /**
     * The control for the region itself. An item's body is tokenized through `$self` - the
     * document's own pattern list - so everything but the new fence rule reads as it did
     * when `#lists` was a line match.
     */
    @Test
    fun anItemsBodyStillTokenizesAsOrdinaryMarkup() {
        val scopes = scopesOf("- item\n\n  plain *bold* here\n", "*bold*")
        assertTrue("emphasis in an item's body stopped highlighting: $scopes", scopes.contains("markup.bold.carve"))
    }
}
