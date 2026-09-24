package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A fenced code body is highlighted in the fence's own language (markup-carve/intellij-carve#207).
 *
 * TWO THINGS HAD TO BE MEASURED BEFORE THIS COULD BE PORTED from vscode-carve, because the
 * IDE's TextMate engine is not vscode-textmate and neither is documented:
 *
 *  * an `include` naming another grammar's scope resolves, as long as that grammar sits in
 *    the same syntax table - which is what the IDE has at runtime, one table holding every
 *    enabled bundle;
 *  * `while` regions run, so a body can be held open by a guard that stops at a bare fence
 *    line rather than by an `end` the embedded language could swallow.
 *
 * Both hold, and the tests below are what keeps them true. The stand-in grammars under
 * `src/test/resources/grammars` stand for the IDE's own bundles so the goldens do not move
 * with a platform version.
 *
 * WHERE THE IDE DIVERGES FROM VS CODE: when no grammar answers the include, vscode-textmate
 * drops the rule and the generic fence rule matches, so nothing changes. Here the language
 * rule still matches and still marks the body `meta.embedded.block.<id>`; only the language
 * scopes are missing. That is why the corpus goldens move for every tagged fence even though
 * the corpus runner registers no language grammar at all.
 */
class CarveFenceEmbedTest {

    private val stubs =
        arrayOf(
            File("src/test/resources/grammars/source.js.tmLanguage.json"),
            File("src/test/resources/grammars/source.python.tmLanguage.json"),
        )

    private val withStubs by lazy { CarveTextMateTokenizer.grammarsTogether(*stubs) }

    private fun scopesOf(grammar: CarveTextMateTokenizer.Grammar, src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("test input has no $needle", at >= 0)
        val end = at + needle.length
        var offset = 0
        return buildString {
            for (token in grammar.tokenize(src)) {
                val start = offset
                offset += token.text.length
                if (start < end && offset > at) append(token.scope).append(' ')
            }
        }
    }

    private val atColumnZero = "```js\nconst x = \"hi\"\n```\n"
    private val onAMarkerLine = "- ```js\n  const x = \"hi\"\n  ```\n"
    private val atABodyColumn = "- item\n\n  ```js\n  const x = \"hi\"\n  ```\n"

    @Test
    fun `a fence body carries its language's scopes at column 0`() {
        val scopes = scopesOf(withStubs, atColumnZero, "const")
        assertTrue("the body is not embedded javascript: $scopes", scopes.contains("meta.embedded.block.javascript"))
        assertTrue("the body took no javascript scope: $scopes", scopes.contains("keyword.control.stub.js"))
    }

    @Test
    fun `a fence body carries its language's scopes on a list marker line`() {
        val scopes = scopesOf(withStubs, onAMarkerLine, "const")
        assertTrue("the body is not embedded javascript: $scopes", scopes.contains("meta.embedded.block.javascript"))
        assertTrue("the body took no javascript scope: $scopes", scopes.contains("keyword.control.stub.js"))
    }

    @Test
    fun `a fence body carries its language's scopes at an item's body column`() {
        val scopes = scopesOf(withStubs, atABodyColumn, "const")
        assertTrue("the body is not embedded javascript: $scopes", scopes.contains("meta.embedded.block.javascript"))
        assertTrue("the body took no javascript scope: $scopes", scopes.contains("keyword.control.stub.js"))
    }

    @Test
    fun `the embedded language follows the info string`() {
        val python = scopesOf(withStubs, "```py\ndef f():\n    return 1\n```\n", "def")
        assertTrue("a py fence is not embedded python: $python", python.contains("meta.embedded.block.python"))
        assertTrue("a py fence took no python scope: $python", python.contains("keyword.control.stub.python"))
        assertFalse("a py fence took javascript scopes: $python", python.contains("stub.js"))
    }

    @Test
    fun `an info string alias is the same language`() {
        for (spelling in listOf("js", "javascript", "mjs", "cjs", "JS")) {
            val scopes = scopesOf(withStubs, "```$spelling\nconst x = 1\n```\n", "const")
            assertTrue("`$spelling` is not embedded javascript: $scopes", scopes.contains("keyword.control.stub.js"))
        }
    }

    @Test
    fun `the fence keeps its own markers around an embedded body`() {
        val opener = scopesOf(withStubs, atColumnZero, "```js")
        assertTrue("the opener lost its begin marker: $opener", opener.contains("keyword.control.raw.begin.carve"))
        assertTrue("the info string is not a language: $opener", opener.contains("entity.name.type.language.carve"))
        val closer = scopesOf(withStubs, atColumnZero, "```\n")
        assertTrue("the closer lost its end marker: $closer", closer.contains("keyword.control.raw.end.carve"))
        assertFalse("the closer was swallowed by the embedded body: $closer", closer.contains("stub.js"))
    }

    @Test
    fun `an unterminated construct in the embedded language cannot swallow the closer`() {
        // The `while` guard, in the one shape that shows why it is there: an open string
        // in the body. An `end`-bounded embedded region would run past the closer.
        val src = "```js\nconst x = \"unterminated\n```\n\nafter\n"
        val after = scopesOf(withStubs, src, "after")
        assertFalse("the document after the fence is still inside it: $after", after.contains("meta.embedded.block"))
        assertFalse("the document after the fence is still raw: $after", after.contains("markup.raw.block"))
    }

    @Test
    fun `a bare fence line inside a wider fence ends the highlighting but not the block`() {
        // THE KNOWN LIMIT of the column-0 guard, pinned so it is a documented behavior
        // rather than a surprise. The guard stops at any bare fence line because it cannot
        // know the opener's width: that would need the begin rule's captures, which a
        // `while` pattern does not resolve in this engine. The BLOCK is unaffected - its
        // `end` does resolve the backref - so only the highlighting stops early.
        val src = "````js\nconst a = 1\n```\nconst b = 2\n````\n\nafter\n"
        val before = scopesOf(withStubs, src, "const a")
        val after = scopesOf(withStubs, src, "const b")
        assertTrue("the body before the inner fence is not highlighted: $before", before.contains("stub.js"))
        assertFalse("the body after the inner fence is still highlighted: $after", after.contains("stub.js"))
        assertTrue("the block closed at the inner fence: $after", after.contains("markup.raw.block.fenced.code.carve"))
        val outside = scopesOf(withStubs, src, "after")
        assertFalse("the block did not close at its own closer: $outside", outside.contains("markup.raw.block"))
    }

    @Test
    fun `a carve fence embeds carve itself and keeps its exact closer`() {
        val src = "````carve\n*bold*\n\n```js\nconst x = 1\n```\n````\n"
        val bold = scopesOf(withStubs, src, "*bold*")
        assertTrue("a carve fence body is not embedded carve: $bold", bold.contains("meta.embedded.block.carve"))
        assertTrue("a carve fence body took no carve scope: $bold", bold.contains("markup.bold.carve"))
        val after = scopesOf(withStubs, "$src\nafter\n", "after")
        assertFalse("the inner 3-backtick fence closed the 4-backtick fence: $after", after.contains("markup.raw.block"))
    }

    @Test
    fun `a language with no grammar registered falls back to a flat body`() {
        // `haskell` is in no LANGUAGES entry, so only the generic rule can match it.
        val scopes = scopesOf(withStubs, "```haskell\nmain = pure ()\n```\n", "main")
        assertTrue("an unknown language lost its raw body: $scopes", scopes.contains("markup.raw.block.fenced.code.carve"))
        assertFalse("an unknown language took an embedded scope: $scopes", scopes.contains("meta.embedded.block"))
    }

    @Test
    fun `a known language with its grammar absent keeps the body flat but marked`() {
        // The plugin's own grammar alone, which is what a stock IDE has for a language it
        // ships no bundle for - `toml` today.
        val alone = CarveTextMateTokenizer.grammarsTogether()
        val scopes = scopesOf(alone, atColumnZero, "const")
        assertTrue("the body lost its embedded marker: $scopes", scopes.contains("meta.embedded.block.javascript"))
        assertFalse("a javascript scope appeared with no javascript grammar: $scopes", scopes.contains("stub.js"))
    }

    @Test
    fun `every generated language rule is reachable`() {
        // A rule whose begin can never match is a rule that cannot be seen to be wrong, and
        // this family is generated, so a generator bug would produce 38 of them in one go.
        // The check drives each language's FIRST info-string word through the grammar and
        // asks for the embedded scope the rule alone can set.
        val generated = CarveFenceLanguages.read()
        assertEquals("the generated family is not 38 languages", 38, generated.size)
        val unreachable =
            generated.mapNotNull { (id, word) ->
                val scopes = scopesOf(withStubs, "```$word\nbody\n```\n", "body")
                if (scopes.contains("meta.embedded.block.$id")) null else "$word -> $id: $scopes"
            }
        assertEquals(unreachable.joinToString("\n", prefix = "\n"), 0, unreachable.size)
    }
}
