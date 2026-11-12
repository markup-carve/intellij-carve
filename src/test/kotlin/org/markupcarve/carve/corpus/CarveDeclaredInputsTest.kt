package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

class CarveDeclaredInputsTest {

    @Test
    fun aRepeatedInputIsOneFixtureUnlessItsModifiersDiffer() {
        val page = """
            ::: compare
            ```carve
            same
            ```
            ```html
            <p>same</p>
            ```
            ```carve
            same
            ```
            ```html
            <p>same</p>
            ```
            :::
            ::: compare no-final-newline
            ```carve
            same
            ```
            ```html
            <p>same</p>
            ```
            :::
        """.trimIndent().lines()

        assertEquals(3, CarveCorpus.declaredInputs(page).size)
        assertEquals(2, CarveCorpus.declaredInputs(page).toSet().size)
    }
}
