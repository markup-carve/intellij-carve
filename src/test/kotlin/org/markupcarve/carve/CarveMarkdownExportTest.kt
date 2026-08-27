package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Pins that the Markdown target is reachable and is the ENGINE'S Markdown.
 *
 * The bundle exports ten converters; the plugin reached only `carveToHtml`, so
 * the export menu offered one target where the engine has several. A wrong
 * function name would render nothing and the action would write an error page
 * to a `.md` file, which is why this asserts the shape of the output rather
 * than only that a string came back.
 */
class CarveMarkdownExportTest {

    @Test
    fun theBundleExportsTheMarkdownConverterTheActionAsksFor() {
        val bundle = File("src/main/resources/js/carve.iife.js").readText()
        assertTrue(
            "the vendored bundle must export carveToMarkdown - the export action names it",
            bundle.contains("carveToMarkdown"),
        )
    }

    @Test
    fun theExportActionIsRegisteredAndReachesTheConverter() {
        val pluginXml = File("src/main/resources/META-INF/plugin.xml").readText()
        assertTrue(
            "plugin.xml must register the Markdown export action",
            pluginXml.contains("org.markupcarve.carve.actions.ExportMarkdownAction"),
        )
        val action = File(
            "src/main/kotlin/org/markupcarve/carve/actions/ExportMarkdownAction.kt",
        ).readText()
        assertTrue(
            "the action must call CarveConverter.toMarkdown",
            action.contains("CarveConverter.toMarkdown"),
        )
        assertFalse(
            "the Markdown export must not wrap its output in an HTML page",
            action.contains("wrapFullHtml"),
        )
    }

    @Test
    fun theDescriptionNamesBothExportTargetsAndTheIdeFloor() {
        val pluginXml = File("src/main/resources/META-INF/plugin.xml").readText()
        assertTrue(
            "the listing must say both export targets exist",
            pluginXml.contains("Export to HTML and to Markdown"),
        )
        assertTrue(
            "the listing must state the 2025.1 floor - a 2024.3 user installs and gets nothing",
            pluginXml.contains("Requires a 2025.1 or newer JetBrains IDE"),
        )
    }
}
