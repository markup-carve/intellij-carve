package org.markupcarve.carve.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The vendored carve-css layers are on the classpath and still say what they are.
 *
 * The preview injects `css/tokens.css` and `css/recipes.css` so that a construct
 * the engine has no handler for - `::: tree`, `::: cards`, `::: columns` - looks
 * the same here as it does for every consumer that installs carve-css. Nothing
 * fails loudly if a resource goes missing: `getResourceAsStream` returns null,
 * the loader skips it, and the preview silently renders those constructs
 * unstyled. That is the failure this guards, and it is the shape the plugin has
 * already been bitten by once with the bundles (#62).
 *
 * Read from the source tree rather than the classpath, because the point is that
 * the FILE is present and stamped, not that some copy of it loaded.
 */
class CarveCssResourcesTest {

    private fun resource(name: String): File =
        File("src/main/resources/css/$name.css")

    @Test
    fun `both vendored layers are present`() {
        for (name in listOf("tokens", "recipes")) {
            val file = resource(name)
            assertTrue("missing vendored stylesheet: ${file.path}", file.isFile)
            assertTrue("vendored stylesheet is empty: ${file.path}", file.length() > 0)
        }
    }

    @Test
    fun `each layer names where it came from`() {
        for (name in listOf("tokens", "recipes")) {
            val head = resource(name).readText().lineSequence().take(6).joinToString("\n")
            assertTrue(
                "no provenance header in $name.css - re-copy it with the header intact",
                head.contains("VENDORED from markup-carve/carve-css"),
            )
            assertTrue(
                "no commit recorded in $name.css",
                Regex("commit [0-9a-f]{7,40}").containsMatchIn(head),
            )
        }
    }

    /**
     * A spot check on the two things the preview actually depends on: the
     * recipes are scoped under `.carve`, which is why the content element
     * carries that class, and the tokens define the dark palette behind
     * `data-theme`, which is why the document element carries that attribute.
     * Either one changing upstream would leave the preview rendering a light
     * palette in a dark IDE, or nothing at all.
     */
    @Test
    fun `the layers still assume the hooks the preview provides`() {
        assertTrue(
            "recipes.css no longer scopes under .carve",
            resource("recipes").readText().contains(".carve .tree"),
        )
        assertTrue(
            "tokens.css no longer keys its dark palette off data-theme",
            resource("tokens").readText().contains("[data-theme=\"dark\"]"),
        )
    }
}
