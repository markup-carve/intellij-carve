package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

class CarveDeclaredPairCountTest {

    @Test
    fun countsEveryPairInABlockButNotAFenceInsideAnExample() {
        val page = """
            ::: compare
            ```carve
            a
            ```
            ```html
            <p>a</p>
            ```
            ```carve
            b
            ```
            ```html
            <p>b</p>
            ```
            ````carve
            ```carve
            not a pair
            ```
            ````
            ```html
            <pre></pre>
            ```
            :::
            ```carve
            outside any block
            ```
        """.trimIndent().lines()

        assertEquals(3, CarveCorpus.countDeclaredPairs(page))
    }
}
