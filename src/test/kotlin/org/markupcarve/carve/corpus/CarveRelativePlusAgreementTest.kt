package org.markupcarve.carve.corpus

import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.highlight.CarveColors
import org.markupcarve.carve.highlight.CarveMarkerScanner

/**
 * Corpus rows 626-634 of markup-carve/carve at f619b320 (carve#3445), each candidate `+`
 * with the spec model's verdict. The floors are the measured counts; a change that loses
 * agreement fails here.
 */
class CarveRelativePlusAgreementTest {
    private data class Plus(val id: String, val source: String, val offset: Int, val marker: Boolean)

    private val samples: List<Plus> by lazy {
        val text = javaClass.getResourceAsStream("/relative-plus/samples.json")!!.reader().readText()
        JsonParser.parseString(text).asJsonArray.flatMap { row ->
            val o = row.asJsonObject
            val source = o["source"].asString
            val starts = ArrayList<Int>().apply { add(0); source.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
            o["plus"].asJsonArray.map {
                val p = it.asJsonObject
                Plus(o["id"].asString, source, starts[p["line"].asInt - 1] + p["col"].asInt, p["verdict"].asString == "marker")
            }
        }
    }

    private fun scannerMarks(p: Plus) = CarveMarkerScanner.scan(p.source)
        .any { it.key == CarveColors.CONTINUATION_MARKER && it.range.startOffset == p.offset }

    private fun grammarMarks(p: Plus): Boolean {
        var offset = 0
        for (token in CarveTextMateTokenizer.tokenize(p.source)) {
            val end = offset + token.text.length
            if (p.offset in offset until end) return token.scope.contains("keyword.control.list.continuation.carve")
            offset = end
        }
        return false
    }

    private fun measure(label: String, marks: (Plus) -> Boolean): Int {
        val misses = samples.filter { marks(it) != it.marker }
        println("$label: agree ${samples.size - misses.size} disagree ${misses.size}")
        misses.forEach { println("  $label ${if (it.marker) "missed marker" else "false marker"}: ${it.id}") }
        return samples.size - misses.size
    }

    @Test
    fun scannerAgreesWithTheSpecModel() {
        val agree = measure("scanner", ::scannerMarks)
        assertTrue("scanner agreement $agree fell below $SCANNER_FLOOR", agree >= SCANNER_FLOOR)
    }

    @Test
    fun grammarAgreesWithTheSpecModel() {
        val agree = measure("grammar", ::grammarMarks)
        assertTrue("grammar agreement $agree fell below $GRAMMAR_FLOOR", agree >= GRAMMAR_FLOOR)
    }

    /** The grammar scopes a marker only where one line proves it, so it never marks text. */
    @Test
    fun grammarNeverScopesTextAsMarker() {
        val wrong = samples.filter { !it.marker && grammarMarks(it) }.map { it.id }
        assertTrue("text scoped as marker: $wrong", wrong.isEmpty())
    }

    private companion object {
        const val SCANNER_FLOOR = 163
        const val GRAMMAR_FLOOR = 117
    }
}
