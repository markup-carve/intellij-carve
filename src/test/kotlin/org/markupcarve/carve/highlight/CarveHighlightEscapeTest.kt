package org.markupcarve.carve.highlight

import org.junit.Assert.assertEquals
import org.junit.Test
import org.markupcarve.carve.corpus.CarveTextMateTokenizer

/**
 * The bare highlight rule, on the shape it read wrong (intellij-carve#116).
 *
 * An escaped `=` is not a delimiter at all: the escape makes the character
 * literal, so `x =\= y` renders `x == y` with no mark, and this rule closed on
 * it and coloured a run the engine does not. The fix (carve-grammars#385) is
 * two halves, because backslashes nest and neither half can look at one
 * character - the closer needs an EVEN backslash run in front of it, and the
 * body may step over an ODD one. Without the second half the body stops at the
 * escaped `=` and can never reach the real closer past it, so `a =b c\= d= e`,
 * which the engine DOES mark, would have gone from wrongly-coloured to
 * uncoloured.
 *
 * WHY THIS IS AN ASSERTION AND NOT A GOLDEN. [CarveGrammarFixtureTest] compares
 * the token stream against a file this repository generated from this grammar,
 * so green there means the grammar did not CHANGE, never that it is right - a
 * golden committed before the fix would have pinned the wrong answer. The rows
 * below are the engine's answer instead: each was rendered through
 * `@markup-carve/carve` and read off the `<mark>` it did or did not produce.
 *
 * The expectations are the SOURCE span this grammar should colour, which
 * differs from the engine's rendered text wherever an escape is consumed:
 * `a =b c\= d= e` renders the mark `b c= d` and the grammar colours `b c\= d`.
 *
 * BOTH DIRECTIONS. A no-mark-only check passes a rule that colours nothing, and
 * a mark-only check passes a rule that colours everything.
 *
 * THREE ROWS ARE KNOWN RESIDUALS and are deliberately not asserted, the same
 * three carve-grammars pins upstream: `x =<== y`, `x =!== y` and `x =a== y`,
 * all "a closer followed by another `=`", which the engine marks and the
 * `(?![\w=])` closer guard refuses. Widening that guard means letting the body
 * hold its own delimiter, which costs more shapes than it buys.
 */
class CarveHighlightEscapeTest {

    /** The source text this grammar colours as a highlight BODY, "" for no mark. */
    private fun markedRun(src: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { it.scope.contains(BODY) && !it.scope.contains(DELIMITER) }
            .joinToString("") { it.text }

    private fun check(src: String, expected: String) =
        assertEquals("highlight body for ${quote(src)}", expected, markedRun(src))

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\") + "\""

    @Test
    fun `an escaped equals does not close a highlight`() {
        // The engine renders every one of these with no mark at all.
        check("x =\\= y", "")
        check("x =<\\= y", "")
        check("x =!\\= y", "")
        check("x =a\\= y", "")
        check("x =\\=< y", "")
        check("x =\\=> y", "")
        check("x =\\=! y", "")
        check("x =\\=  y", "")
        check("x =\\=\\ y", "")
        check("x  =\\= y", "")
    }

    @Test
    fun `the body steps over an escaped equals to reach the real closer`() {
        // Engine: `a <mark>b c= d</mark> e`. Guarding the closer alone would
        // have turned this from wrongly-coloured into uncoloured.
        check("a =b c\\= d= e", "b c\\= d")
    }

    @Test
    fun `an escaped equals is literal content between a real pair`() {
        // Engine: `x <mark>=</mark> y` - it opens at the first `=` and closes on
        // the third, which the body can only reach by holding the escaped one.
        check("x =\\== y", "\\=")
    }

    @Test
    fun `an even backslash run escapes the backslash and not the closer`() {
        // Engine: `x <mark>\</mark> y` and `x <mark>\\</mark> y`. What decides is
        // the whole run, not the single character in front of the delimiter.
        check("x =\\\\= y", "\\\\")
        check("x =\\\\\\\\= y", "\\\\\\\\")
    }

    @Test
    fun `a longer odd run is still an escaped closer`() {
        // Engine: no mark on a run of three, and `a <mark>b c\= d</mark> e` when the
        // body has to carry one. These two rows are why the rule consumes escape
        // PAIRS rather than counting the run in a lookbehind: every lookbehind
        // spelling the IDE's engine accepts is bounded by the alternatives written
        // out, and each of the four measured got the second row wrong.
        check("x =\\\\\\= y", "")
        check("a =b c\\\\\\= d= e", "b c\\\\\\= d")
    }

    @Test
    fun `an escape is a pair whatever it escapes`() {
        // A backslash before an ordinary character is still an escape and still
        // ordinary content: engine `x <mark>a\bc</mark> y`. A backslash before a
        // SPACE is one too, so the `=` after it is preceded by whitespace and opens
        // nothing: engine `x =&nbsp;= y`, no mark.
        check("x =a\\bc= y", "a\\bc")
        check("x =\\ = y", "")
        check("x =\\=\\= y", "")
    }

    @Test
    fun `an escaped equals opens nothing either`() {
        // The escape rule takes `\=` before #emphasis is reached, so the opener
        // needs no guard of its own: engine `x =a= y`, no mark. An EVEN run in
        // front of it leaves an ordinary opener, which the engine does mark.
        check("x \\=a= y", "")
        check("a \\=b c= d", "")
        check("x \\\\=a= y", "a")
    }

    @Test
    fun `an ordinary highlight still colours`() {
        check("x =b= y", "b")
        check("a =b c= d", "b c")
    }

    @Test
    fun `a closer still beats smart typography once the highlight is open`() {
        // The asymmetry is the engine's: the guard belongs on the opener alone.
        check("x =y z<= w", "y z<")
        check("x =y z=> w", "y z")
    }

    @Test
    fun `an equals that begins an arrow opens nothing`() {
        // Already true before carve-grammars#385 - this rule's opener spells the
        // guard `(?=[^\s>])`. Kept as the control that the escape fix does not
        // cost it.
        check("x =>= y", "")
        check("x =>a= y", "")
        check("x =>\\= y", "")
    }

    private companion object {
        const val BODY = "markup.changed.carve"
        const val DELIMITER = "keyword.control.highlight.carve"
    }
}
