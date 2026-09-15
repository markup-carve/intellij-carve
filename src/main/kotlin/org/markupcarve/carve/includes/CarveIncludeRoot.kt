package org.markupcarve.carve.includes

import java.nio.file.Path

/**
 * The containment root a document's includes are resolved against
 * (spec PART 9 section 19).
 *
 * ## What it resolves to, and why that and not something else
 *
 * In order:
 *
 * 1. `carve.includes.includeRoot` from Settings | Tools | Carve, when set. It
 *    must be ABSOLUTE. A relative value is refused rather than resolved,
 *    because resolving one means resolving it against the process working
 *    directory, and section 19 names that as the one root a host must never
 *    use. `"."` would root containment at the IDE's cwd and `".."` at its
 *    parent, by a route the never-the-cwd rule does not visibly cover.
 * 2. Otherwise the PROJECT BASE DIRECTORY that contains the document. IntelliJ
 *    projects can carry several (`BaseProjectDirectories`), so the containing
 *    one is chosen rather than an arbitrary first, and the deepest when they
 *    nest - a nested module is the tighter boundary and a security boundary
 *    takes the tighter reading.
 * 3. Otherwise the document's own parent directory, which is what a `.crv`
 *    opened with no project behind it gets.
 *
 * Never the process working directory, at any step.
 *
 * ## Why the plugin derives it rather than letting the server fall back
 *
 * carve-lsp derives the same three steps internally when the client sends no
 * `includeRoot`. Deriving it here and PASSING it removes the possibility of the
 * two disagreeing: the root the bundle lays its files out under is then the
 * same object the server enforces containment with, rather than two
 * computations that happen to agree today. Step 2 reproduces the server's
 * `workspaceRootFor(documentPath, workspaceRoots)` against the same folder list
 * lsp4ij sends as `workspaceFolders`, so the derivation is not a second policy
 * - it is the same policy, evaluated where its answer is also needed.
 */
object CarveIncludeRoot {

    /**
     * @param settingRoot `carve.includes.includeRoot`, blank when unset.
     * @param baseDirectories the project's base directories, as lsp4ij sends
     *   them to the server. Empty for a document with no project behind it.
     * @return the root, canonicalized, or null when even the document's own
     *   directory cannot be canonicalized (a deleted or unreadable file).
     */
    fun of(settingRoot: String, baseDirectories: List<Path>, documentPath: Path): Path? {
        val document = runCatching { documentPath.toAbsolutePath().normalize() }.getOrNull() ?: return null

        val configured = settingRoot.trim()
        if (configured.isNotEmpty()) {
            val candidate = runCatching { Path.of(configured) }.getOrNull() ?: return null
            if (!candidate.isAbsolute) return null
            val root = canonical(candidate) ?: return null
            // A configured root that does not HOLD the document is refused
            // rather than used. It resolves nothing anyway - the server
            // resolves a relative target against the document's own folder and
            // then contains it, so every include under such a root is denied -
            // and downstream it is worse than useless: the bundle lays its
            // files out relative to the root, so the document itself has no
            // place inside and drops out of its own export.
            if (canonical(document)?.startsWith(root) != true) return null
            return root
        }

        val containing = baseDirectories
            .mapNotNull { canonical(it) }
            .filter { root -> canonical(document)?.startsWith(root) == true }
            // Deepest wins: a nested base directory is the tighter boundary.
            .maxByOrNull { it.nameCount }
        if (containing != null) return containing

        return document.parent?.let { canonical(it) }
    }

    private fun canonical(path: Path): Path? = runCatching { path.toRealPath() }.getOrNull()
}
