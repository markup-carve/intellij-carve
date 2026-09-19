package org.markupcarve.carve.includes

/**
 * What a flatten has to tell the user afterwards.
 *
 * Two effects ambush people otherwise, and both are stated rather than left to
 * be found in a published page: the output is canonical Carve, so formatting is
 * normalized rather than preserved, and colliding explicit ids and footnote
 * labels are renamed (I5), so a flattened document can carry `intro-2`.
 */
object CarveFlattenReport {

    /** The two rules the engine raises when it renames past a collision (I5). */
    private val RENAME_RULES = setOf("include-heading-id-rename", "include-footnote-rename")

    fun renameCount(warnings: List<CarveIncludeExpansion.Warning>): Int =
        warnings.count { it.rule in RENAME_RULES }

    /** Targets the expansion reached but could not read. */
    fun unreadable(dependencies: List<CarveIncludeExpansion.Dependency>): List<String> =
        dependencies.filterNot { it.resolved }.map { it.id }

    fun summary(
        warnings: List<CarveIncludeExpansion.Warning>,
        dependencies: List<CarveIncludeExpansion.Dependency>,
    ): String {
        val merged = dependencies.count { it.resolved }
        val lines = mutableListOf(
            when (merged) {
                0 -> "No includes to merge; the output is this document as canonical Carve."
                1 -> "Merged 1 included file. Formatting is normalized, not preserved."
                else -> "Merged $merged included files. Formatting is normalized, not preserved."
            },
        )
        val renames = renameCount(warnings)
        if (renames > 0) {
            lines += "$renames colliding id or footnote label renamed, so the flattened " +
                "document renders like the expanded original."
        }
        val missing = unreadable(dependencies)
        if (missing.isNotEmpty()) {
            lines += "Could not read ${missing.size} target(s), left as written:"
            lines += missing.joinToString("\n")
        }
        return lines.joinToString("\n")
    }
}
