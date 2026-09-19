package org.markupcarve.carve

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the wire shape of the `substitution` node in the VENDORED engine.
 *
 * The ruling on markup-carve/carve#2083 replaced the `oldText` and `newText`
 * strings with `old` and `new` inline arrays, the shape `insert` and `delete`
 * already use.
 *
 * `CarveBundleCorpusTest` compares HTML, so it sees a half-applied rename and
 * not a consistent one: renaming the two fields in the producer, all 45 read
 * sites and both wire-schema tables leaves every one of the 1738 corpus
 * documents byte-identical, and that run stays green. The field names are what
 * anything reading the AST consumes, so they are asserted here directly.
 */
class CarveSubstitutionShapeTest {

    /** The ruling's own example, so the expectation is quoted rather than derived. */
    private val source = "{~/old/~>/new/~}\n"

    @Test
    fun theHalvesAreInlineArraysNamedOldAndNew() {
        assertEquals(
            """{"type":"substitution","old":[{"type":"emphasis","children":""" +
                """[{"type":"text","value":"old"}]}],"new":[{"type":"emphasis","children":""" +
                """[{"type":"text","value":"new"}]}]}""",
            substitutionJson(),
        )
    }

    /**
     * The field order above would also pass with `oldText` and `newText` present
     * beside `old` and `new`, which is what a half-applied rename looks like.
     */
    @Test
    fun theSupersededStringFieldsAreGone() {
        val json = substitutionJson()
        assertFalse("substitution still carries oldText: $json", json.contains("oldText"))
        assertFalse("substitution still carries newText: $json", json.contains("newText"))
    }

    /** The one substitution node in [source], as JSON with `pos` dropped. */
    private fun substitutionJson(): String =
        newContext().use { context ->
            context.eval(Source.newBuilder("js", bundleSource(), BUNDLE_RESOURCE).build())
            context.getBindings("js").putMember("carveSource", source)
            context.eval("js", PROBE).asString()
        }

    private fun bundleSource(): String =
        CarveSubstitutionShapeTest::class.java.getResourceAsStream(BUNDLE_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw AssertionError("Bundled $BUNDLE_RESOURCE is missing from the plugin resources")

    private fun newContext(): Context =
        Context.newBuilder("js")
            .allowAllAccess(false)
            .option("engine.WarnInterpreterOnly", "false")
            .build()

    private companion object {
        const val BUNDLE_RESOURCE = "/js/carve.iife.js"

        /**
         * Walks the whole tree rather than indexing into it, so a substitution
         * that moved under a different parent is found instead of missed.
         */
        val PROBE = """
            (function () {
              var found = [];
              (function walk(node) {
                if (!node || typeof node !== 'object') return;
                if (node.type === 'substitution') found.push(node);
                for (var key of Object.keys(node)) walk(node[key]);
              })(carve.carveToAstJson(carveSource));
              if (found.length !== 1) throw new Error('expected one substitution, found ' + found.length);
              return JSON.stringify(found[0], function (key, value) {
                return key === 'pos' ? undefined : value;
              });
            })()
        """.trimIndent()
    }
}
