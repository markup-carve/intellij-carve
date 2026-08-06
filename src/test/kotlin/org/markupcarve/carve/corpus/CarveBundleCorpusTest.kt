package org.markupcarve.carve.corpus

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders the whole shared corpus through the VENDORED `carve.iife.js`, on
 * GraalJS, and compares each result byte-for-byte with the corpus golden.
 *
 * This is the check that was missing. The bundle carries a provenance header
 * naming the carve-js commit it was built from, and comparing that string
 * against upstream is cheap - but a pin string only ever says which commit was
 * bundled, never what the bundled code now does. The bundle sat 363 commits
 * behind for three weeks while rendering 110 of these 610 documents
 * differently, and none of them threw, so there was no error to see anywhere
 * (#62). Only driving documents through the artifact catches that, and it would
 * have caught it at the first divergent document rather than the 110th.
 *
 * Deliberately offline: the inputs and the goldens both come from the `spec`
 * submodule this repo already pins, so this runs in `./gradlew test` with no
 * network and no sibling checkout. Measuring against carve `main` is a separate
 * question about how stale the pin is, and that lives in `engine-drift.yml`.
 *
 * The engine is invoked as bare `carveToHtml(source)` with no options, which is
 * the corpus contract every Carve engine is measured against upstream. The
 * plugin's own preview adds a showcase extension set on top (see
 * `CarveConverter.CARVE_OPTIONS_JS`); those extensions change the output by
 * design and are covered by `CarveConverterTest`, not here.
 *
 * When this fails after a deliberate engine bump, rebuild the bundle:
 *
 *     tools/build-carve-bundle.sh ../carve-js
 */
class CarveBundleCorpusTest {

    /**
     * Documents the vendored engine renders differently from the golden, each
     * with the reason it is allowed to.
     *
     * Read this as a pinned observation, not an allowlist: the test asserts the
     * observed set is EXACTLY this set. An entry that stops diverging fails the
     * test just as loudly as a new divergence, so a stale excuse cannot survive
     * here quietly - which is the failure mode an open-ended allowlist has and
     * the reason this repo grew a 363-commit gap in the first place.
     */
    private val expectedDivergences: Map<String, String> = linkedMapOf(
        // The `spec` pin (afd6cc5c) predates the rule change that moved this
        // golden: upstream now keeps the trailing `tail` line inside the list
        // item instead of closing the item and emitting `<p>tail</p>`. The
        // engine implements the newer rule, so the ENGINE is right and the
        // PIN is old. It clears itself when the spec submodule is next bumped,
        // which is a deliberate review here (see spec-drift.yml).
        "86-list-lazy-continuation-9" to
            "spec pin predates the upstream golden change for the lazy-continuation tail",
    )

    /**
     * Documents whose outcome depends on the HOST, not on the bundle.
     *
     * These are neither required to diverge nor allowed to diverge silently for
     * a new reason: they are excluded from the exact comparison and reported, so
     * a JVM with a different stack budget does not turn this test red for
     * something the bundle did not cause. Keep the list at the length of its
     * documented reasons - anything not explainable in one belongs above, or is
     * a real failure.
     */
    private val hostDependent: Map<String, String> = linkedMapOf(
        // 100 nested `:::: note` containers, at the spec's nesting cap. The
        // renderer recurses once per level, and GraalJS runs on the host
        // thread's stack, which is smaller than the one Node gives V8: the same
        // bundle renders this document correctly under Node and overflows here.
        // That is a limit of the plugin's JS host, not engine drift, so it is
        // not what this test is measuring. Tracked separately.
        "182-openers-past-the-nesting-cap-are-one-paragraph" to
            "100 nested containers overflow the GraalJS host stack; Node renders the same bundle fine",
    )

    @Test
    fun everyCorpusDocumentRendersLikeItsGolden() {
        val corpus = CarveCorpus.directory
        assertTrue(CarveCorpus.MISSING_MESSAGE, corpus != null)

        val pairs = CarveCorpus.crvFiles()
            .map { it to File(corpus, it.name.removeSuffix(".crv") + ".html") }
            .filter { (_, html) -> html.isFile }

        // A truncated or half-checked-out corpus would otherwise let this pass
        // by having nothing to compare - the check has to be able to fail.
        assertTrue(
            "Only ${pairs.size} corpus pair(s) found under ${corpus?.path}. " +
                CarveCorpus.MISSING_MESSAGE,
            pairs.size >= 500,
        )

        val diverging = linkedMapOf<String, String>()
        newContext().use { context ->
            context.eval(Source.newBuilder("js", bundleSource(), BUNDLE_RESOURCE).build())
            val carveToHtml = context.getBindings("js").getMember("carve")
                ?.getMember("carveToHtml")
                ?: throw AssertionError("Bundled $BUNDLE_RESOURCE exposes no carve.carveToHtml")

            for ((crv, html) in pairs) {
                val name = crv.name.removeSuffix(".crv")
                val actual = try {
                    carveToHtml.execute(crv.readText()).asString()
                } catch (e: Exception) {
                    diverging[name] = "threw ${e.javaClass.simpleName}: ${e.message}"
                    continue
                }
                if (actual.trim() != html.readText().trim()) {
                    diverging[name] = "rendered HTML differs from the golden"
                }
            }
        }

        // A host-dependent document is excused only for the outcome its reason
        // describes - the host running out of stack. If one of them starts
        // producing WRONG HTML instead, that is the bundle's doing and it is
        // reported like any other divergence, so the excuse cannot quietly
        // cover a second, unrelated failure.
        val attributable = diverging.filterKeys { it !in hostDependent || !diverging.getValue(it).startsWith("threw") }
        assertEquals(
            "The vendored $BUNDLE_RESOURCE renders ${attributable.size} of ${pairs.size} corpus " +
                "document(s) differently from their goldens, against the expected " +
                "${expectedDivergences.size}. Rebuild it with " +
                "`tools/build-carve-bundle.sh ../carve-js` if the bundle is stale, or record " +
                "the new divergence in expectedDivergences with its reason if it is deliberate.\n" +
                "  observed: ${attributable.entries.joinToString("\n            ") { "${it.key} - ${it.value}" }}",
            expectedDivergences.keys.toList().sorted(),
            attributable.keys.toList().sorted(),
        )
    }

    private fun bundleSource(): String =
        CarveBundleCorpusTest::class.java.getResourceAsStream(BUNDLE_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw AssertionError("Bundled $BUNDLE_RESOURCE is missing from the plugin resources")

    /**
     * One context for all 610 documents. `CarveConverter` builds a fresh one per
     * render because a preview render is one document and isolation is cheap
     * there; re-parsing a 566 KB bundle 610 times is not.
     */
    private fun newContext(): Context =
        Context.newBuilder("js")
            .allowAllAccess(false)
            .option("engine.WarnInterpreterOnly", "false")
            .build()

    private companion object {
        const val BUNDLE_RESOURCE = "/js/carve.iife.js"
    }
}
