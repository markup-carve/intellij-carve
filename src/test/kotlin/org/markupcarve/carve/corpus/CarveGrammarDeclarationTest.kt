package org.markupcarve.carve.corpus

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The grammar declarations in `build.gradle.kts` are claims about the CONSTRUCT, and this
 * is what measures them.
 *
 * `checkGrammarDeclarations` can only ask whether a rule of the same NAME still exists on
 * the other side. That is blind to the one error `pluginOnlyGrammarRules` is most likely
 * to accumulate - upstream highlighting the construct under a different rule name - because
 * a factoring difference is exactly where the names diverge (markup-carve/intellij-carve#135,
 * and markup-carve/vscode-carve#200 from the other side). Four declarations were false for
 * a while for that reason, and the fifth, `cross-reference`, was false when this check
 * first ran: vscode-carve highlights `</#id>` inside its `autolink` rule.
 *
 * So each declaration names a fixture, and the claim is measured over that fixture with
 * BOTH grammars driven through the same engine:
 *
 *   plugin-only      - upstream highlights NOTHING the local rule owns.
 *   grouped upstream - upstream highlights EVERYTHING the local rule owns, elsewhere.
 *   covered locally  - this grammar highlights everything the UPSTREAM rule owns.
 *
 * The three differ only in which grammar owns the rule and which way the assertion points,
 * which is why they are one helper used three times.
 *
 * The upstream half needs `build/grammar-drift/upstream.tmLanguage.json`, which
 * `fetchUpstreamGrammar` writes; run `./gradlew checkGrammarConstructs`. Without it those
 * arms are SKIPPED rather than passed - a declarations check that reports "still true"
 * because it could not reach the network is worse than none.
 */
class CarveGrammarDeclarationTest {

    @Test
    fun everyLocalDeclarationNamesAFixtureThatExercisesItsRule() {
        val declared = pluginOnly + groupedUpstream
        assertTrue("no local grammar declarations to check", declared.isNotEmpty())
        for ((rule, fixture) in declared) {
            val spans = ownedSpans(localGrammar, rule, fixtureText(rule, fixture))
            assertTrue(
                "$rule is declared against $fixture, but the local rule owns nothing in it. " +
                    "The fixture must contain the construct, not merely some text another rule scopes.",
                spans.isNotEmpty(),
            )
        }
    }

    @Test
    fun aPluginOnlyConstructIsHighlightedByNothingUpstream() {
        val upstream = requireUpstream()
        val upstreamGrammar = CarveTextMateTokenizer.grammarFrom(upstream)
        for ((rule, fixture) in pluginOnly) {
            val text = fixtureText(rule, fixture)
            for (span in ownedSpans(localGrammar, rule, text)) {
                assertTrue(
                    "$rule is declared plugin-only, but vscode-carve highlights ${span.text.trim()} " +
                        "in $fixture. It is not a construct upstream lacks - find the upstream rule " +
                        "that scopes it and move the entry to localRulesGroupedUpstream.",
                    !upstreamGrammar.highlightsAnythingIn(text, span),
                )
            }
        }
    }

    @Test
    fun aGroupedConstructIsHighlightedUpstreamUnderAnotherRule() {
        val upstream = requireUpstream()
        assertTrue("no grouped declarations to check", groupedUpstream.isNotEmpty())
        val upstreamGrammar = CarveTextMateTokenizer.grammarFrom(upstream)
        for ((rule, fixture) in groupedUpstream) {
            val text = fixtureText(rule, fixture)
            for (span in ownedSpans(localGrammar, rule, text)) {
                assertTrue(
                    "$rule is declared as grouped into an upstream rule, but vscode-carve leaves " +
                        "${span.text.trim()} in $fixture unhighlighted. That is a construct upstream " +
                        "does not have, which is pluginOnlyGrammarRules, not a grouping delta.",
                    upstreamGrammar.highlightsAnythingIn(text, span),
                )
            }
        }
    }

    @Test
    fun anUpstreamConstructDeclaredCoveredIsHighlightedLocally() {
        val upstream = requireUpstream()
        assertTrue("no covered-locally declarations to check", coveredLocally.isNotEmpty())
        val local = CarveTextMateTokenizer.grammarFrom(localGrammar)
        for ((rule, fixture) in coveredLocally) {
            val text = fixtureText(rule, fixture)
            val spans = ownedSpans(upstream, rule, text)
            assertTrue(
                "$rule is declared covered by a local rule, but the upstream rule owns nothing in " +
                    "$fixture - so the fixture does not contain the construct being declared away.",
                spans.isNotEmpty(),
            )
            for (span in spans) {
                assertTrue(
                    "$rule is declared as already covered locally, but this grammar leaves " +
                        "${span.text.trim()} in $fixture unhighlighted. The declaration is silencing " +
                        "a real gap - port the rule instead.",
                    local.highlightsAnythingIn(text, span),
                )
            }
        }
    }

    companion object {
        private const val PLUGIN_ONLY = "carve.declarations.pluginOnly"
        private const val GROUPED = "carve.declarations.groupedUpstream"
        private const val COVERED = "carve.declarations.coveredLocally"

        private val repoRoot: File by lazy {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
            dir ?: File(System.getProperty("user.dir"))
        }

        private val localGrammar: File by lazy {
            File(repoRoot, "src/main/resources/textmate/carve.tmLanguage.json")
        }

        private val fixturesDir: File by lazy { File(repoRoot, "src/test/resources/fixtures") }

        private fun rawProperty(key: String): String =
            System.getProperty(key)
                ?: error("$key was not set. Run the tests through Gradle: build.gradle.kts owns the declarations.")

        /** `rule=fixture.crv;rule=fixture.crv`, the one encoding a system property can carry. */
        private fun declarations(key: String): List<Pair<String, String>> =
            rawProperty(key)
                .split(';')
                .filter { it.isNotBlank() }
                .map { it.substringBefore('=') to it.substringAfter('=') }

        private val pluginOnly: List<Pair<String, String>> by lazy { declarations(PLUGIN_ONLY) }
        private val groupedUpstream: List<Pair<String, String>> by lazy { declarations(GROUPED) }
        private val coveredLocally: List<Pair<String, String>> by lazy { declarations(COVERED) }

        private fun fixtureText(rule: String, fixture: String): String {
            val file = File(fixturesDir, fixture)
            assertTrue("$rule names a fixture that does not exist: ${file.path}", file.isFile)
            return file.readText()
        }

        /**
         * The non-blank ranges [rule] owns in [text]. Blank ones are dropped because a
         * newline inside a block is text no grammar needs a rule for, and requiring the
         * other side to scope it would fail every block-level declaration.
         */
        private fun ownedSpans(grammar: File, rule: String, text: String): List<CarveTextMateTokenizer.Span> =
            CarveTextMateTokenizer.grammarProbing(grammar, rule)
                .probedSpans(text)
                .filter { it.text.isNotBlank() }

        /** The upstream grammar, or a skip - never a silent pass. */
        private fun requireUpstream(): File {
            val path = System.getProperty("carve.upstreamGrammar")
            val file = path?.let { File(it) }
            assumeTrue(
                "vscode-carve's grammar has not been fetched, so the declarations were NOT measured " +
                    "against upstream. Run ./gradlew checkGrammarConstructs.",
                file != null && file.isFile && file.length() > 0,
            )
            return file!!
        }
    }
}
