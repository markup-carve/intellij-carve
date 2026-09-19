package org.markupcarve.carve.includes

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The host half of the engine's include pass (spec PART 9 section 19).
 *
 * `expandIncludes` performs no file I/O; the host supplies a resolver and owns
 * containment. carve-js ships one for Node in its `./node` subpath, which the
 * preview cannot reach - it runs the browser bundle on GraalJS, with no `fs` and
 * no host access. So the POLICY here is carve-js `fileSystemResolver`, rewritten
 * against `java.nio`, and nothing else is: expansion, cycle detection, the byte
 * budget, the depth bound and the warnings all stay in the engine.
 *
 * Canonicalize-then-contain, deliberately not a lexical ban on `..`. Too strict:
 * `../shared/glossary.crv` from `chapters/ch1.crv` is a normal book layout whose
 * target is inside the root. Too weak: a symlink inside the root pointing out of
 * it escapes without containing `..` at all.
 */
object CarveIncludeResolver {

    /** Largest target this resolver will read. Matches carve-js `DEFAULT_MAX_FILE_BYTES`. */
    const val DEFAULT_MAX_FILE_BYTES: Long = 4L * 1024 * 1024

    /** A URI scheme names no filesystem place, so it gets no "where it would be". */
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /**
     * One resolver answer, in the shape `expandIncludes` reads.
     *
     * @param source the target's text, or null when it could not be produced.
     * @param id canonical identity, which feeds the cycle guard and is what a
     *   host watches for invalidation. Present even on a refusal (I11).
     * @param denial refusal class, null on success.
     */
    data class Answer(val source: String?, val id: String, val denial: String?)

    /**
     * @param root containment root, already canonical and absolute.
     * @param path the directive's path, as written.
     * @param parentId canonical id of the including file, or null for the root
     *   document. A nested relative include resolves against its actual parent
     *   directory rather than against the root.
     */
    fun resolve(
        root: Path,
        path: String,
        parentId: String?,
        maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    ): Answer {
        val candidate = runCatching { Path.of(path) }.getOrNull()
            ?: return Answer(null, path, "denied")
        // Absolute targets are refused: carve-js defaults `allowAbsolute` to
        // false and the plugin exposes no switch for it.
        if (candidate.isAbsolute) return Answer(null, path, "denied")

        val base = parentId
            ?.let { runCatching { root.resolve(it).parent }.getOrNull() }
            ?: root
        val resolved = runCatching { base.resolve(path).normalize() }.getOrNull()
            ?: return Answer(null, path, "denied")

        val real = canonical(resolved)
        if (real == null) {
            if (SCHEME.containsMatchIn(path)) return Answer(null, path, "denied")
            val wouldBe = canonicalCandidate(resolved)
            return if (contains(root, wouldBe)) {
                Answer(null, wouldBe.toString(), "not-found")
            } else {
                Answer(null, path, "outside-root")
            }
        }
        if (!contains(root, real)) return Answer(null, path, "outside-root")

        val size = runCatching { Files.size(real) }.getOrNull()
            ?: return Answer(null, real.toString(), "denied")
        if (size > maxFileBytes) return Answer(null, real.toString(), "denied")

        val text = runCatching { Files.readString(real, StandardCharsets.UTF_8) }.getOrNull()
            ?: return Answer(null, real.toString(), "denied")
        return Answer(text, real.toString(), null)
    }

    /**
     * Segment-wise, so a directory legitimately named `..foo` is not read as an
     * escape the way a string prefix test would.
     */
    private fun contains(root: Path, candidate: Path): Boolean {
        val relative = runCatching { root.relativize(candidate) }.getOrNull() ?: return false
        if (relative.nameCount == 0 || relative.toString().isEmpty()) return true
        if (relative.isAbsolute) return false
        return relative.getName(0).toString() != ".."
    }

    /**
     * The candidate's canonical spelling whether or not it is there: the longest
     * prefix that exists is canonicalized, which resolves every symlink actually
     * on disk, and the remaining segments are re-appended. A symlink can only
     * live on the existing prefix, so the tail cannot hide one.
     */
    private fun canonicalCandidate(candidate: Path): Path {
        val remainder = ArrayDeque<String>()
        var prefix: Path = candidate.toAbsolutePath().normalize()
        while (true) {
            val real = canonical(prefix)
            if (real != null) return buildFrom(real, remainder)
            val parent = prefix.parent ?: return buildFrom(prefix, remainder)
            val name = prefix.fileName ?: return buildFrom(prefix, remainder)
            remainder.addFirst(name.toString())
            prefix = parent
        }
    }

    private fun buildFrom(prefix: Path, remainder: Collection<String>): Path {
        var out = prefix
        for (name in remainder) out = out.resolve(name)
        return out
    }

    private fun canonical(path: Path): Path? = runCatching { path.toRealPath() }.getOrNull()
}
