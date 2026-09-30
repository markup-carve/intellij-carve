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
     *
     * Empty is the state to defend, and it held from carve-js 0.1.7 until the
     * bundle moved to 0.1.9. Every entry below is the same shape as the ones
     * 0.1.7 cleared: a rule carve `main` ruled on AFTER the corpus this repo
     * pins was cut, which the engine implements and the golden predates. The
     * `spec` submodule still pins carve 0.1.6 (5863d1d), so the twelve goldens
     * naming them are eleven days older than the engine rendering them.
     *
     * They go away by bumping the pin, not by touching a golden: at carve 0.1.7
     * (551f224) this bundle renders all 2134 documents byte-identically. That
     * bump is its own review - it leaves 59 corpus categories unclassified in
     * [CarveCorpusCategories] and one token-stream golden to regenerate - so it
     * is tracked separately. Delete every entry here when the pin moves; the
     * exact-set assertion will insist on it.
     */
    private val expectedDivergences: Map<String, String> = linkedMapOf(
        // An empty code payload renders as `<code></code>`, where the 0.1.6
        // golden carries a lone newline inside it.
        // markup-carve/carve#2616, "Preserve code payload line endings in AST JSON".
        "276-a-fence-opened-on-a-list-marker-line-body-below-the-content-column" to
            "carve#2616 dropped the lone newline an empty code payload used to render; golden predates it",
        "276-a-fence-opened-on-a-list-marker-line-body-below-the-content-column-2" to
            "carve#2616, same empty-payload newline",
        "276-a-fence-opened-on-a-list-marker-line-body-below-the-content-column-4" to
            "carve#2616, same empty-payload newline",
        "276-a-fence-opened-on-a-list-marker-line-body-below-the-content-column-5" to
            "carve#2616, same empty-payload newline",
        "69-opaque-spans-inside-a-container-6" to
            "carve#2616, same empty-payload newline",
        "85-blockquote-lazy-continuation-stops-at-a-fenced-block-3" to
            "carve#2616, same empty-payload newline",
        // The item's fence becomes a real `<pre><code>` block inside the item
        // instead of an inline `<code>` spilling past it.
        "276-a-fence-opened-on-a-list-marker-line-body-below-the-content-column-7" to
            "carve#2141 reads an item's fence by one I4 answer, not two; golden predates it",
        // An empty container body keeps a blank line: `<div>\n\n</div>`.
        "116-fence-opener-with-a-nested-list-body-inside-a-list-item-6" to
            "carve#2184 consolidated the empty-container body; golden predates it",
        "271-the-flush-left-line-after-a-container-a-quoted-line-opened-4" to
            "carve#2184, same empty-container body",
        // A header row crossed by a rowspan leaves `<thead>` for the body group.
        "101-table-header-cell-rowspan" to
            "carve#2224 keeps crossing table rowspans in one body group; golden predates it",
        // A retained marker below the content column stays text instead of
        // opening a list.
        "277-a-below-column-marker-after-a-comment-where-no-paragraph-is-open" to
            "carve#2619 keeps retained markers below the content column as text; golden predates it",
        "277-a-below-column-marker-after-a-comment-where-no-paragraph-is-open-2" to
            "carve#2619, same retained marker",
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
        //
        // EQUALITY, not a floor. This was `pairs.size >= 500` against a corpus
        // of 1131, so it passed with more than half the documents absent, which
        // is the condition it exists to reject. The reference is the corpus's
        // own source rather than a number recorded here; see
        // CarveCorpus.declaredSize.
        val declared = CarveCorpus.declaredSize()
        assertTrue(
            "No ::: compare blocks found under spec/resources/examples. " +
                "spec/tests/corpus is generated from those pages, so without them there is no " +
                "independent statement of how big the corpus should be and this check would be " +
                "comparing the corpus against itself. " + CarveCorpus.MISSING_MESSAGE,
            declared != null,
        )
        assertEquals(
            "${pairs.size} corpus pair(s) found under ${corpus?.path}, but the spec's example " +
                "pages declare $declared. Every ::: compare block in " +
                "spec/resources/examples/{core,extensions,edge-cases}.md becomes one corpus pair, " +
                "so a difference means the corpus checked out here is not the one those pages " +
                "describe - a truncated submodule, or a corpus that needs regenerating upstream. " +
                "It does not mean this run was clean.",
            declared,
            pairs.size,
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
