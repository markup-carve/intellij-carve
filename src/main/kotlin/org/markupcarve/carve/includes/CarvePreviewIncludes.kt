package org.markupcarve.carve.includes

import org.markupcarve.carve.settings.CarveIncludeMode
import java.nio.file.Path

/**
 * The decisions the preview makes around the engine's include pass, kept out of
 * the panel so each one can be measured without a browser.
 *
 * Section 19 makes resolution opt-in for the HOST, so the preview asks the same
 * setting the language server is sent ([CarveIncludeMode]) rather than growing a
 * second switch. Two switches over one capability can disagree, and the pair the
 * user would then be looking at is a preview that expanded and an editor that
 * refused to navigate, with nothing on screen saying which is authoritative.
 */
object CarvePreviewIncludes {

    /** What [org.markupcarve.carve.CarveConverter.toHtmlWithIncludes] needs. */
    data class Setup(val root: Path, val documentId: String)

    /**
     * @param baseDirectories the project's base directories, as lsp4ij sends
     *   them to the server. Empty for a document with no project behind it.
     * @return null when the preview must render the document as written.
     */
    fun setupFor(
        mode: CarveIncludeMode,
        workspaceTrusted: Boolean,
        settingRoot: String,
        baseDirectories: List<Path>,
        documentPath: Path,
    ): Setup? {
        if (!mode.resolvesWhen(workspaceTrusted)) return null
        val root = CarveIncludeRoot.of(settingRoot, baseDirectories, documentPath) ?: return null
        val document = runCatching { documentPath.toRealPath() }.getOrNull() ?: return null
        return Setup(root, document.toString())
    }

    /**
     * Whether a changed file is one this render depended on.
     *
     * Compares against the ATTEMPTED set as well as the resolved one: a target
     * that was not there is reported by where it would be (I11), and creating
     * that file has to invalidate the preview or a document stays broken on
     * screen after the user fixes it.
     */
    fun dependsOn(dependencies: List<CarveIncludeExpansion.Dependency>, changed: Path): Boolean {
        val canonical = runCatching { changed.toRealPath() }.getOrNull() ?: changed.normalize()
        return dependencies.any { dependency ->
            val target = runCatching { Path.of(dependency.id) }.getOrNull() ?: return@any false
            target == canonical || target == changed.normalize()
        }
    }

    /**
     * The warnings, as a block the preview shows above the document.
     *
     * An unresolved target, a cycle or a containment refusal otherwise reaches
     * the reader as ordinary prose - the directive renders as the literal text
     * it is, which looks like a document that says `{{ ... }}` rather than a
     * document whose include failed.
     */
    fun warningsHtml(warnings: List<CarveIncludeExpansion.Warning>, suppressed: Int): String {
        if (warnings.isEmpty()) return ""
        val rows = warnings.joinToString("") { warning ->
            "<li><code>${escape(warning.rule)}</code> " +
                "line ${warning.line}, column ${warning.column}: ${escape(warning.message)}</li>"
        }
        val tail = if (suppressed > 0) "<p>$suppressed further include warning(s) not shown.</p>" else ""
        return """<div class="carve-include-warnings"><strong>Includes</strong><ul>$rows</ul>$tail</div>"""
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
