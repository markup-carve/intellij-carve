package org.markupcarve.carve.corpus

import java.io.File

/**
 * Locates the shared-corpus inputs that live in the `spec` git submodule
 * (markup-carve/carve) under `spec/tests/corpus` as `.crv` files.
 *
 * The corpus is deliberately not copied onto the test classpath: it stays a
 * submodule so the pinned commit is visible in git and bumping it is a one-line
 * submodule update. Tests resolve the directory from the project root, walking
 * up from the working directory so the lookup works both from a Gradle run
 * (working dir = project root) and from an IDE run.
 */
object CarveCorpus {

    private const val CORPUS_REL = "spec/tests/corpus"

    /**
     * The corpus directory, or null when the submodule has not been checked out.
     * Tests treat null as a hard failure with an actionable message rather than
     * silently passing on an empty corpus.
     */
    val directory: File? by lazy { locate() }

    private fun locate(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, CORPUS_REL)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }

    /** All `.crv` corpus inputs, sorted by file name for deterministic ordering. */
    fun crvFiles(): List<File> {
        val root = directory ?: return emptyList()
        return root.listFiles { f -> f.isFile && f.name.endsWith(".crv") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /** The distinct category slugs present in the live corpus, sorted. */
    fun categories(): List<String> =
        crvFiles().map { CarveCorpusCategories.categoryOf(it.name) }.distinct().sorted()

    /**
     * How many documents the corpus is SUPPOSED to hold, derived from something
     * other than the corpus directory.
     *
     * A test that counts the corpus to decide how big the corpus should be
     * moves both sides of the comparison together and guards nothing, and a
     * floor is the same defect with a number in front of it: `>= 500` against a
     * corpus of 1131 passes with more than half of it missing, which is exactly
     * the state such a guard exists to reject.
     *
     * So the reference is the corpus's SOURCE. `spec/tests/corpus` is generated
     * from the `::: compare` blocks in
     * `spec/resources/examples/{core,extensions,edge-cases}.md`, one block per
     * pair, and the generator upstream refuses to write a corpus where the two
     * disagree. Both live in the same submodule, so this costs nothing to read.
     * Counting the source also leaves no literal here to go stale: adding an
     * example upstream moves the expectation on the next bump by itself.
     *
     * Returns null when the source pages are not there, which is a wiring
     * problem for the caller to report rather than a corpus of size zero.
     */
    fun declaredSize(): Int? {
        val root = directory?.parentFile?.parentFile ?: return null
        val examples = File(root, "resources/examples")
        var declared = 0
        for (page in listOf("core.md", "extensions.md", "edge-cases.md")) {
            val file = File(examples, page)
            if (!file.isFile) return null
            // Mirrors the generator's state machine rather than grepping: a
            // `::: compare` line inside an already-open block is content, not a
            // second pair, and a block closes on a bare marker line.
            var marker: String? = null
            for (raw in file.readLines()) {
                val line = raw.trim()
                val open = marker
                if (open != null) {
                    if (line == open) marker = null
                    continue
                }
                val match = COMPARE_OPENER.matchEntire(line) ?: continue
                declared++
                marker = match.groupValues[1]
            }
        }
        return declared.takeIf { it > 0 }
    }

    private val COMPARE_OPENER = Regex("""^(:{3,})\s+compare(\s+\S.*)?$""")

    /** A clear message pointing at the submodule when the corpus is missing. */
    val MISSING_MESSAGE: String =
        "Shared corpus not found at $CORPUS_REL. Check out the submodule with " +
            "`git submodule update --init spec` (CI passes submodules: recursive to actions/checkout)."
}
