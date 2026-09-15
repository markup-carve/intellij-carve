package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins that the preview IS REACHABLE, and that every door into JCEF asks
 * whether JCEF exists.
 *
 * These tests require both preview registrations to remain reachable and every
 * entry point to call `CarveJcefSupport.isSupported()`. The provider hides the
 * default editor, so it must refuse a file it cannot preview (#88).
 */
class CarvePluginDescriptorTest {

    private val metaInf = File("src/main/resources/META-INF")
    private val pluginXml = File(metaInf, "plugin.xml").readText()

    /** Declarations only. A comment explaining a dependency is not one. */
    private val pluginXmlDeclarations =
        pluginXml.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
    private val mainKotlin = File("src/main/kotlin")

    @Test
    fun thePreviewExtensionPointsAreRegistered() {
        for (fqn in listOf(
            "org.markupcarve.carve.preview.CarvePreviewEditorProvider",
            "org.markupcarve.carve.preview.CarvePreviewToolWindowFactory",
        )) {
            assertTrue(
                "plugin.xml must register $fqn - registered nowhere, the preview does not exist",
                pluginXmlDeclarations.contains(fqn),
            )
        }
    }

    @Test
    fun jcefDependencyGrantsClassLoaderAccessWithoutGatingThePreview() {
        val dependencies =
            Regex("""<depends[^>]*>com\.intellij\.modules\.jcef</depends>""")
                .findAll(pluginXmlDeclarations)
                .map { it.value }
                .toList()
        assertTrue(
            "2026.2 puts JCEF behind a plugin class loader, so Carve needs one exact optional dependency",
            dependencies == listOf(
                "<depends optional=\"true\" config-file=\"carve-jcef-classloader.xml\">" +
                    "com.intellij.modules.jcef</depends>",
            ),
        )
        assertTrue(
            "preview registrations must not be gated; 2025.x does not declare the module",
            !File(metaInf, "carve-jcef.xml").exists(),
        )
        val optionalDeclarations = File(metaInf, "carve-jcef-classloader.xml")
            .readText()
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<\?xml.*?\?>"""), "")
            .trim()
        assertTrue(
            "the optional descriptor must be empty except for its idea-plugin root",
            Regex("""<idea-plugin>\s*</idea-plugin>""").matches(optionalDeclarations),
        )
    }

    @Test
    fun everyDoorIntoThePreviewAsksWhetherJcefExists() {
        val guard = "CarveJcefSupport.isSupported()"
        for (path in listOf(
            "org/markupcarve/carve/preview/CarvePreviewEditorProvider.kt",
            "org/markupcarve/carve/preview/CarvePreviewToolWindowFactory.kt",
            "org/markupcarve/carve/actions/TogglePreviewAction.kt",
        )) {
            assertTrue(
                "$path must gate on $guard - the provider hides the default editor (#88), " +
                    "and an action that cannot act must not be offered",
                File(mainKotlin, path).readText().contains(guard),
            )
        }
    }

}
