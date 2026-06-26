package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Grammar snapshot tests for hand-authored fixture inputs.
 *
 * These cover constructs (citations, code callouts) that are not yet in the
 * shared markup-carve/carve corpus. Each `.crv` file in
 * `src/test/resources/fixtures/` is tokenized with the Carve grammar and
 * compared against a golden `.tokens` file in
 * `src/test/resources/fixture-tokens/`.
 *
 * Regenerate goldens after a deliberate grammar change with:
 *   ./gradlew test --tests "*CarveGrammarFixtureTest" -Dcarve.updateGoldens=true
 */
@RunWith(Parameterized::class)
class CarveGrammarFixtureTest(
    private val name: String,
    private val crv: File,
) {

    @Test
    fun tokenStreamMatchesGolden() {
        val input = crv.readText()
        val actual = CarveTextMateTokenizer.snapshot(input)
        val golden = goldenFile(crv.name)

        if (updateGoldens) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            return
        }

        assertTrue(
            "Missing golden for ${crv.name}. Generate it with " +
                "`./gradlew test -Dcarve.updateGoldens=true` and review before committing. " +
                "Expected at: ${golden.path}",
            golden.isFile,
        )
        assertEquals(
            "Token stream changed for ${crv.name}. If intentional, regenerate with " +
                "`-Dcarve.updateGoldens=true` and review the diff.",
            golden.readText(),
            actual,
        )
    }

    companion object {
        private val updateGoldens: Boolean =
            System.getProperty("carve.updateGoldens", "false").toBoolean()

        private val fixturesDir: File by lazy {
            val url = CarveGrammarFixtureTest::class.java.getResource("/fixtures")
                ?: error("Fixture directory /fixtures not found on test classpath")
            File(url.toURI())
        }

        private val goldensDir: File by lazy {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
                dir = dir.parentFile
            }
            val root = dir ?: File(System.getProperty("user.dir"))
            File(root, "src/test/resources/fixture-tokens")
        }

        private fun goldenFile(crvName: String): File =
            File(goldensDir, crvName.removeSuffix(".crv") + ".tokens")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> =
            fixturesDir
                .listFiles { f -> f.isFile && f.name.endsWith(".crv") }
                ?.sortedBy { it.name }
                ?.map { arrayOf<Any>(it.name, it) }
                ?: emptyList()
    }
}
