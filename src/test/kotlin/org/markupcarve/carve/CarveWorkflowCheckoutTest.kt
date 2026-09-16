package org.markupcarve.carve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A workflow job that runs the test task checks out the `spec` submodule.
 *
 * The corpus tests reach `spec/tests/corpus` by path, so without the submodule
 * they fail on an empty directory rather than on anything about the grammar.
 * `grammar-drift.yml` checked out with the default `submodules: false` while
 * running `checkGrammarConstructs`, which depends on `test`, so every run died
 * in the corpus tests and the grammar arms' verdict was never visible (#149).
 */
class CarveWorkflowCheckoutTest {

    /** Gradle tasks that run the test task, directly or through a dependency. */
    private val corpusTasks = listOf("test", "checkGrammarConstructs")

    private val workflowDir = File(System.getProperty("user.dir"), ".github/workflows")

    /** Each job of each workflow, as id to its own text. */
    private fun jobs(): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, StringBuilder>>()
        for (file in workflowDir.listFiles { f: File -> f.name.endsWith(".yml") }.orEmpty().sortedBy { it.name }) {
            var inJobs = false
            var current: StringBuilder? = null
            for (line in file.readLines()) {
                if (line.startsWith("jobs:")) {
                    inJobs = true
                    continue
                }
                if (!inJobs) continue
                if (line.isNotEmpty() && !line.first().isWhitespace()) break
                val start = Regex("^ {2}([A-Za-z0-9_-]+):\\s*$").find(line)
                if (start != null) {
                    current = StringBuilder()
                    found += (file.name + ":" + start.groupValues[1]) to current
                    continue
                }
                current?.append(line)?.append('\n')
            }
        }
        return found.map { (id, text) -> id to text.toString() }
    }

    /**
     * Jobs that run one of the corpus tasks through gradle.
     *
     * Anchored to the start of the line, so the remediation advice
     * `spec-drift.yml` echoes - which quotes `./gradlew test` inside a message -
     * does not read as a job that runs it.
     */
    private fun corpusJobs(): List<Pair<String, String>> = jobs().filter { (_, text) ->
        corpusTasks.any {
            Regex("^\\s*(run:\\s*)?(if\\s+)?\\./gradlew[^\\n]*\\b" + it + "\\b", RegexOption.MULTILINE)
                .containsMatchIn(text)
        }
    }

    @Test
    fun theSweepFindsJobsThatRunTheCorpusTests() {
        assertTrue(
            "No workflow job runs the test task, so the check below could not fail. " +
                "Looked in ${workflowDir.path} for ./gradlew invoking one of $corpusTasks.",
            corpusJobs().isNotEmpty(),
        )
    }

    @Test
    fun everyJobThatRunsTheCorpusTestsChecksOutTheSubmodule() {
        val offenders = corpusJobs()
            .filter { (_, text) -> !text.contains("submodules:") }
            .map { (id, _) -> id }
        assertEquals(
            "These jobs run the corpus tests without checking out `spec`, so the tests would " +
                "fail on an absent corpus rather than on what the job is measuring. Add " +
                "`submodules: recursive` to the checkout.",
            emptyList<String>(),
            offenders,
        )
    }
}
