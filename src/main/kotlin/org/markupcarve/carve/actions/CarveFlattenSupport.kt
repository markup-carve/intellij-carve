package org.markupcarve.carve.actions

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.includes.CarveIncludeExpansion
import org.markupcarve.carve.includes.CarveIncludeRoot
import org.markupcarve.carve.settings.CarveSettings
import java.nio.file.Path

/**
 * What the two flatten actions share: the gate, the root, and the run.
 *
 * Both write the SAME bytes `carve flatten` writes - one is saved and one is
 * copied - so the primitive is reached once here rather than twice.
 */
internal object CarveFlattenSupport {

    /** Runs the flatten, or explains on screen why it cannot, and returns null. */
    fun flatten(e: AnActionEvent, title: String): Pair<VirtualFile, CarveIncludeExpansion.Flattened>? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val documentPath = runCatching { Path.of(file.path) }.getOrNull() ?: return null

        val settings = CarveSettings.getInstance(project)
        if (!settings.includeMode.resolvesWhen(project.isTrusted())) {
            warn(
                project,
                title,
                "Include resolution is off for this document, so there is nothing to merge. " +
                    "Trust the project, or set the include mode in Settings | Tools | Carve.",
            )
            return null
        }

        // The project base directory holding this file, which is the same set
        // lsp4ij sends the server as workspaceFolders.
        val baseDirectories = listOfNotNull(
            BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file),
        ).mapNotNull { runCatching { Path.of(it.path) }.getOrNull() }
        val root = CarveIncludeRoot.of(settings.includeRoot, baseDirectories, documentPath)
        if (root == null) {
            warn(
                project,
                title,
                "No usable include root for this document. An include root set in " +
                    "Settings | Tools | Carve must be an absolute path that holds this file.",
            )
            return null
        }

        val documentId = runCatching { documentPath.toRealPath().toString() }.getOrNull() ?: return null
        val flattened = CarveConverter.toFlattenedCarve(document.text, root, documentId)
        if (flattened == null) {
            warn(project, title, "The bundled Carve engine could not flatten this document.")
            return null
        }
        return file to flattened
    }

    private fun warn(project: Project, title: String, message: String) =
        Messages.showWarningDialog(project, message, title)
}
