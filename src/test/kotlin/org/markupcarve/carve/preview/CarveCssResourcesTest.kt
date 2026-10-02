package org.markupcarve.carve.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The vendored carve-css layers are on the classpath and still say what they are.
 *
 * The preview injects all four vendored layers so that a construct
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
        for (name in listOf("tokens", "recipes", "contrast", "extensions")) {
            val file = resource(name)
            assertTrue("missing vendored stylesheet: ${file.path}", file.isFile)
            assertTrue("vendored stylesheet is empty: ${file.path}", file.length() > 0)
        }
    }

    @Test
    fun `each layer names where it came from`() {
        for (name in listOf("tokens", "recipes", "contrast", "extensions")) {
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

    /**
     * `UPSTREAM` is the record the drift guard reads, so it has to agree with
     * the headers it describes. A refresh that updates one and not the other
     * leaves the guard comparing against the wrong release and reporting a
     * clean match while the copy is stale.
     */
    @Test
    fun `the record and the headers name the same release`() {
        val record = upstream()
        val version = requireNotNull(record["version"]) { "no version line in css/UPSTREAM" }
        val commit = requireNotNull(record["commit"]) { "no commit line in css/UPSTREAM" }
        for (name in listOf("tokens", "recipes", "contrast", "extensions")) {
            val head = resource(name).readText().lineSequence().take(7).joinToString("\n")
            assertTrue(
                "$name.css says it is not $version, but css/UPSTREAM records $version",
                head.contains("version $version,"),
            )
            assertTrue(
                "$name.css does not record commit $commit, which css/UPSTREAM does",
                head.contains("commit $commit"),
            )
        }
    }

    /**
     * The offline half of the drift guard. `tools/check-carve-css-drift.sh`
     * answers the same question against npm, but it needs network and so runs
     * outside `test`; this one catches the case that bites locally, which is a
     * vendored layer edited in place to patch something the preview needed. The
     * header says not to, and nothing enforced it.
     */
    @Test
    fun `neither layer has been edited since it was vendored`() {
        val recorded = upstreamDigests()
        assertEquals(
            "css/UPSTREAM does not list all four layers",
            setOf("tokens.css", "recipes.css", "contrast.css", "extensions.css"),
            recorded.keys,
        )
        for ((name, expected) in recorded) {
            val file = File("src/main/resources/css/$name")
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(
                "$name does not match the SHA-256 in css/UPSTREAM. Do not edit a vendored " +
                    "layer: change it in carve-css, release, then re-vendor and refresh the record.",
                expected,
                actual,
            )
        }
    }

    /**
     * Every `--carve-*` the inline stylesheet reads has to be defined by
     * something the page also loads, or the declaration falls back to the
     * initial value and the rule looks present while doing nothing. The
     * vendored tokens are one of those definers, which is the reason this test
     * lives next to them: a refresh that drops a token breaks the page here and
     * nowhere else.
     */
    @Test
    fun `every token the preview reads is defined`() {
        val page = CarvePreviewHtml.create(
            initialHtml = "",
            isDark = false,
            assetBase = "file:///nowhere/",
            carveCss = listOf("tokens", "recipes", "contrast", "extensions")
                .joinToString("\n") { resource(it).readText() },
        )
        val defined = Regex("(--carve-[a-z0-9-]+)\\s*:").findAll(page)
            .map { it.groupValues[1] }.toSet()
        // Only a reference with no fallback. `var(--carve-tree-indent, 0.95em)`
        // is a per-instance override hook that recipes.css deliberately leaves
        // undefined, so a definition is not what makes it work.
        val read = Regex("var\\(\\s*(--carve-[a-z0-9-]+)\\s*\\)").findAll(page)
            .map { it.groupValues[1] }.toSet()
        val undefined = (read - defined).sorted()
        assertTrue(
            "the preview reads ${undefined.size} token(s) nothing defines: $undefined - either " +
                "a vendored layer dropped them or the inline stylesheet invented them",
            undefined.isEmpty(),
        )
    }

    /**
     * The one thing about `contrast.css` this host can check.
     *
     * Headless Chromium does not emulate the Windows high-contrast palette, so
     * no probe here can reach `forced-colors: active` and read what the badge
     * actually resolves to. The layer is therefore taken on upstream's
     * measurement, and what is testable is the declaration that measurement is
     * about: `--carve-ink-inverse` has to be remapped alongside `--carve-accent`
     * inside the forced-colors block. 0.1.1 remapped only the accent and left
     * the inverse a hex the user agent repaints `CanvasText`, which put a
     * code-callout badge - ink-inverse ON accent - near 1.50:1. That is why
     * 0.1.1's layer was deliberately NOT vendored (#226).
     *
     * So this is not a render check. It is a check that a refresh cannot walk
     * the pair back to the shape that made vendoring the layer worse than
     * skipping it, in a repo that cannot see the consequence.
     */
    @Test
    fun `the forced-colors block remaps both halves of the accent pair`() {
        val forced = Regex(
            "@media \\(forced-colors: active\\) \\{.*?\\n\\}",
            RegexOption.DOT_MATCHES_ALL,
        ).find(resource("contrast").readText())
        assertTrue("contrast.css has no forced-colors block at all", forced != null)
        val block = forced!!.value
        for (token in listOf("--carve-accent", "--carve-ink-inverse", "--carve-accent-soft")) {
            val declared = Regex("$token:\\s*([A-Za-z]+)\\s*;").find(block)
            assertTrue(
                "$token is not remapped to a system color inside forced-colors - a hex here " +
                    "is repainted by the user agent and the declared pair stops meaning anything",
                declared != null,
            )
        }
    }

    private fun upstream(): Map<String, String> =
        File("src/main/resources/css/UPSTREAM").readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()

    private fun upstreamDigests(): Map<String, String> =
        File("src/main/resources/css/UPSTREAM").readLines()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == 4 && it[0] == "sha256" }
            .associate { it[3] to it[1] }
}
