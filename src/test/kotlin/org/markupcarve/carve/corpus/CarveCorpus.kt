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

    /** A clear message pointing at the submodule when the corpus is missing. */
    val MISSING_MESSAGE: String =
        "Shared corpus not found at $CORPUS_REL. Check out the submodule with " +
            "`git submodule update --init spec` (CI passes submodules: recursive to actions/checkout)."
}
