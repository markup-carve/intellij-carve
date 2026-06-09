package org.markupcarve.carve

import com.intellij.lang.Language

object CarveLanguage : Language("Carve") {
    override fun getDisplayName(): String = "Carve"

    private fun readResolve(): Any = CarveLanguage
}
