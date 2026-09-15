package org.markupcarve.carve.preview

import com.intellij.ui.jcef.JBCefApp

/** Tests JCEF availability without letting a missing optional class escape. */
object CarveJcefSupport {
    @Volatile
    private var classMissing = false

    fun isSupported(): Boolean =
        if (classMissing) {
            false
        } else {
            try {
                JBCefApp.isSupported()
            } catch (_: LinkageError) {
                classMissing = true
                false
            }
        }
}
