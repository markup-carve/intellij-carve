package org.markupcarve.carve.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CarvePreviewPanel parents its own listeners, which puts it in the Disposer tree, so it
 * has to leave the tree through Disposer: a direct dispose() call leaves it registered
 * under the root and IntelliJ reports a leak. The panel builds a JCEF browser, which the
 * headless test IDE cannot, so this pins the wiring in the source instead.
 */
class CarvePreviewDisposalTest {

    private val preview = File("src/main/kotlin/org/markupcarve/carve/preview")

    private fun source(name: String): String =
        File(preview, name).readText().replace(Regex("""//.*"""), "")

    @Test
    fun `the preview file editor owns its panel in the Disposer tree`() {
        val editor = source("CarvePreviewEditorProvider.kt")
        assertTrue(editor.contains("Disposer.register(this, previewPanel)"))
        assertFalse("dispose the panel through Disposer, not directly", editor.contains("previewPanel.dispose()"))
    }

    @Test
    fun `the preview tool window content disposes its panel`() {
        assertTrue(source("CarvePreviewToolWindowFactory.kt").contains("setDisposer(previewPanel)"))
    }

    @Test
    fun `nothing disposes a preview panel directly`() {
        for (file in preview.walk().filter { it.extension == "kt" && it.name != "CarvePreviewPanel.kt" }) {
            assertFalse(file.name, file.readText().contains(Regex("""[pP]anel\.dispose\(\)""")))
        }
    }
}
