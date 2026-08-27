package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins that the preview IS REACHABLE, and that every door into JCEF asks
 * whether JCEF exists.
 *
 * This file used to pin the opposite - that the extension points lived in
 * `carve-jcef.xml`, behind
 * `<depends optional="true">com.intellij.modules.jcef</depends>`. That module
 * is declared by no IDE: JCEF is platform code in `lib/app-client.jar`, not a
 * plugin with its own class loader. So the dependency never resolved, the file
 * never loaded, neither extension point was ever registered in any IDE, and
 * these tests passed the whole time - they asserted the MECHANISM and never
 * asked whether the preview could be reached (#104).
 *
 * They ask that now. `JBCefApp.isSupported()` is what keeps the #88 failure
 * away: the provider hides the default editor, so it must refuse a file it
 * cannot preview rather than leave it unopenable.
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
    fun noDescriptorDependsOnAModuleNoIdeDeclares() {
        val fictional = Regex("""<depends[^>]*>com\.intellij\.modules\.jcef</depends>""")
        assertTrue(
            "com.intellij.modules.jcef is declared by no IDE; a dependency on it silently drops everything it gates",
            !fictional.containsMatchIn(pluginXmlDeclarations),
        )
        assertTrue(
            "carve-jcef.xml is gone - its contents moved into plugin.xml",
            !File(metaInf, "carve-jcef.xml").exists(),
        )
    }

    @Test
    fun everyDoorIntoThePreviewAsksWhetherJcefExists() {
        val guard = "JBCefApp.isSupported()"
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
