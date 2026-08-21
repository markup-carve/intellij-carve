package org.markupcarve.carve

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins WHERE the preview extension points are registered.
 *
 * JCEF is not part of `com.intellij.modules.platform`: it is its own plugin,
 * `com.intellij.modules.jcef`, whose content module carries
 * `com.intellij.ui.jcef.JBCefBrowser`. A plugin that does not depend on it does
 * not get the class, and registering `CarvePreviewEditorProvider` in the main
 * descriptor therefore threw `NoClassDefFoundError` on the first `.crv` file
 * opened - and because the provider hides the default editor, that took the
 * whole editor with it, not just the preview (#88).
 *
 * The registration lives in the optional `carve-jcef.xml` for that reason, so
 * an IDE without JCEF simply opens the text editor. The failure mode this test
 * exists for is a preview extension point being added back to `plugin.xml`,
 * where it looks identical and works on every machine that happens to have
 * JCEF loaded.
 */
class CarvePluginDescriptorTest {

    private val metaInf = File("src/main/resources/META-INF")
    private val pluginXml = File(metaInf, "plugin.xml").readText()
    private val jcefXml = File(metaInf, "carve-jcef.xml").readText()

    @Test
    fun previewIsRegisteredBehindTheJcefDependency() {
        assertTrue(
            "plugin.xml must declare the optional JCEF dependency that loads carve-jcef.xml",
            Regex("""<depends\s+optional="true"\s+config-file="carve-jcef\.xml">com\.intellij\.modules\.jcef</depends>""")
                .containsMatchIn(pluginXml),
        )
        assertTrue(
            "carve-jcef.xml must register the preview editor provider",
            jcefXml.contains("org.markupcarve.carve.preview.CarvePreviewEditorProvider"),
        )
        assertTrue(
            "carve-jcef.xml must register the preview tool window",
            jcefXml.contains("org.markupcarve.carve.preview.CarvePreviewToolWindowFactory"),
        )
    }

    @Test
    fun theMainDescriptorRegistersNothingFromThePreviewPackage() {
        val offenders = Regex("""org\.markupcarve\.carve\.preview\.\w+""")
            .findAll(pluginXml)
            .map { it.value }
            .toList()
        assertTrue(
            "plugin.xml must not register a preview class - it needs JCEF, which the platform module does not provide: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun onlyThePreviewPanelTouchesJcef() {
        val users = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("com.intellij.ui.jcef") || it.readText().contains("import org.cef.") }
            .map { it.name }
            .toSortedSet()
        assertTrue(
            "JCEF may only be reached from CarvePreviewPanel, which carve-jcef.xml gates; found $users",
            users == sortedSetOf("CarvePreviewPanel.kt"),
        )
        assertFalse(
            "the toggle action must not reach JCEF - it looks the tool window up by id and tolerates its absence",
            File("src/main/kotlin/org/markupcarve/carve/actions/TogglePreviewAction.kt")
                .readText()
                .contains("jcef"),
        )
    }
}
