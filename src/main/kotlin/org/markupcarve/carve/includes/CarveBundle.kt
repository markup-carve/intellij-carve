package org.markupcarve.carve.includes

import java.nio.file.Path

/** One file of a bundle. [path] is relative to the bundle root, always `/`-separated. */
data class BundleFile(val path: String, val source: String)

/**
 * A document and every file it includes, ready to be written out.
 *
 * [missing] holds the targets the walk could not read, named as the author
 * wrote them. They have no bytes to copy, so the bundle reports them instead of
 * shipping a document whose includes silently do not resolve.
 */
data class Bundle(val files: List<BundleFile>, val missing: List<String>)

data class BundleInput(
    val documentPath: Path,
    val source: String,
    /**
     * The containment root the walk ran under, as the SERVER'S gate decided it,
     * never a root derived a second time here. Two answers to what a document
     * may read is one answer too many.
     */
    val includeRoot: Path,
    val walk: IncludeWalk,
)

/**
 * Export a document together with every file it includes.
 *
 * Deliberately NOT flattening. Flattening merges children into the parent,
 * which is the engine's expansion pass, and no PUBLISHED carve-js release
 * carries one - measured through the artifact rather than the tag: nothing in
 * `@markup-carve/carve@0.1.6`'s `dist` mentions `expandIncludes`. A bundle
 * needs none of it. The include WALK the language server already performs
 * reports every target it touched, so the file list is a value handed over, and
 * the files are COPIED rather than merged. No section 19 merge semantics are
 * reimplemented here, which is the whole reason this half is reachable.
 *
 * It is also the right shape for "send it to a colleague who will keep editing
 * it": the directives survive, so the recipient gets a document that is still a
 * document rather than one long file.
 */
object CarveBundle {

    /** Names listed before a miss report stops enumerating. */
    const val MISSING_SHOWN = 3

    /** Names tried before giving up on finding a free directory. */
    const val MAX_BUNDLE_CANDIDATES = 100

    /**
     * [target] relative to [root], or null when it is not inside it.
     *
     * The walk cannot hand back a target outside the root - containment is the
     * server's job and it does it by canonical path. This is here so that if
     * one ever did, the file is DROPPED rather than written outside the bundle
     * directory by a `..` in its relative path.
     */
    fun bundlePath(root: Path, target: Path): String? {
        val relative = runCatching { root.relativize(target) }.getOrNull() ?: return null
        if (relative.toString().isEmpty()) return null
        if (relative.isAbsolute) return null
        if (relative.any { it.toString() == ".." }) return null
        return relative.joinToString("/") { it.toString() }
    }

    /** The document and every file it reached, ready to be written out. */
    fun build(input: BundleInput): Bundle {
        val files = mutableListOf<BundleFile>()
        val seen = mutableSetOf<String>()
        val root = input.includeRoot.toAbsolutePath().normalize()

        fun add(id: Path, source: String) {
            val where = bundlePath(root, id.toAbsolutePath().normalize()) ?: return
            if (!seen.add(where)) return
            files += BundleFile(where, source)
        }

        add(input.documentPath, input.source)
        for (document in input.walk.documents) add(Path.of(document.id), document.source)

        return Bundle(
            files = files,
            missing = input.walk.dependencies.filterNot { it.resolved }.map { it.id },
        )
    }

    /**
     * A directory reserved for the bundle of [documentPath], or null when the
     * names are all taken.
     *
     * Derived from the document's own path rather than typed, so the
     * destination cannot be steered somewhere else and there is no empty string
     * to resolve against the process working directory.
     *
     * [reserve] CLAIMS a candidate and says whether it got it - it does not
     * merely report whether one exists. Testing for absence and then creating
     * is two steps with a gap: two exports started together, or anything else
     * creating the path in between, both pick the same "free" name and write
     * their files into one directory. `Files.createDirectory` fails on an
     * existing path, so claiming and testing in one call closes the gap.
     */
    fun directoryFor(documentPath: Path, reserve: (Path) -> Boolean): Path? {
        val directory = documentPath.toAbsolutePath().normalize().parent ?: return null
        val stem = documentPath.fileName.toString().substringBeforeLast('.')
        for (n in 1..MAX_BUNDLE_CANDIDATES) {
            val candidate = directory.resolve(if (n == 1) "$stem.bundle" else "$stem.bundle-$n")
            if (reserve(candidate)) return candidate
        }
        return null
    }

    /** One line naming what was written and what could not be. */
    fun summary(bundle: Bundle, directoryName: String): String {
        val files = if (bundle.files.size == 1) "1 file" else "${bundle.files.size} files"
        if (bundle.missing.isEmpty()) return "Bundled $files into $directoryName."
        val shown = bundle.missing.take(MISSING_SHOWN).joinToString(", ")
        val rest = bundle.missing.size - MISSING_SHOWN
        val tail = if (rest > 0) "$shown and $rest more" else shown
        return "Bundled $files into $directoryName. Could not read: $tail."
    }
}
