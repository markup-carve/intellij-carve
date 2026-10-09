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
     * `spec/resources/examples/{core,extensions,edge-cases}.md`, one pair per
     * `carve` fence inside a block, and the generator upstream refuses to write a corpus where the two
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
            declared += countDeclaredPairs(file.readLines())
        }
        return declared.takeIf { it > 0 }
    }

    /**
     * Same census as the spec's `scripts/lib/example-pair-census.mjs`: one pair per
     * `carve` fence inside a `::: compare` block, and nothing inside a fence is markup.
     */
    fun countDeclaredPairs(lines: List<String>): Int {
        var declared = 0
        var marker: String? = null
        var fence: String? = null
        for (line in lines) {
            val openFence = fence
            if (openFence != null) {
                if (line.startsWith(openFence) && line.substring(openFence.length).isBlank()) fence = null
                continue
            }
            val ticks = line.takeWhile { it == '`' }
            if (ticks.length >= 3) {
                fence = ticks
                if (marker != null && line.substring(ticks.length).trim() == "carve") declared++
                continue
            }
            val trimmed = line.trim()
            val colons = trimmed.takeWhile { it == ':' }
            if (colons.length < 3) continue
            val openMarker = marker
            if (openMarker == null) {
                if (COMPARE_OPENER.containsMatchIn(trimmed.substring(colons.length))) marker = colons
            } else if (trimmed == openMarker) {
                marker = null
            }
        }
        return declared
    }

    private val COMPARE_OPENER = Regex("""^[ \t]+compare(?:[ \t]|$)""")

    /** A clear message pointing at the submodule when the corpus is missing. */
    val MISSING_MESSAGE: String =
        "Shared corpus not found at $CORPUS_REL. Check out the submodule with " +
            "`git submodule update --init spec` (CI passes submodules: recursive to actions/checkout)."
}
