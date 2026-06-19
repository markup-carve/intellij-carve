package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Lexer/highlighter snapshot tests (item #3).
 *
 * Runs every COVERED shared-corpus `.crv` file through the IDE's TextMate engine
 * over the committed Carve grammar and compares the resulting token-scope stream
 * against a golden resource under `src/test/resources/corpus-tokens/`.
 *
 * The goldens are checked-in, human-reviewable snapshots: one line per token,
 * `escaped-text -> full.scope.chain`. Reviewing them catches grammar regressions
 * (a heading losing `markup.heading.carve`, an indented `#` wrongly highlighted,
 * emphasis bleeding across a delimiter, and so on).
 *
 * Regenerate after a deliberate grammar change with:
 *   ./gradlew test --tests "*CarveCorpusSnapshotTest" -Dcarve.updateGoldens=true
 * then review the diff before committing.
 */
@RunWith(Parameterized::class)
class CarveCorpusSnapshotTest(
    private val categoryAndFile: String,
    private val crv: File,
) {

    @Test
    fun tokenStreamMatchesGolden() {
        assumeTrue(CarveCorpus.MISSING_MESSAGE, CarveCorpus.directory != null)

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
            "Token stream changed for ${crv.name}. If intentional, regenerate goldens with " +
                "`-Dcarve.updateGoldens=true` and review the diff.",
            golden.readText(),
            actual,
        )
    }

    companion object {
        private val updateGoldens: Boolean =
            System.getProperty("carve.updateGoldens", "false").toBoolean()

        /** Where goldens are written and read. Located next to the test source tree. */
        private val goldensDir: File by lazy {
            // Walk up to the project root, then into the test resources directory.
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
                dir = dir.parentFile
            }
            val root = dir ?: File(System.getProperty("user.dir"))
            File(root, "src/test/resources/corpus-tokens")
        }

        private fun goldenFile(crvName: String): File =
            File(goldensDir, crvName.removeSuffix(".crv") + ".tokens")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val covered = CarveCorpusCategories.COVERED
            return CarveCorpus.crvFiles()
                .filter { CarveCorpusCategories.categoryOf(it.name) in covered }
                .map { arrayOf<Any>(it.name, it) }
        }
    }
}
