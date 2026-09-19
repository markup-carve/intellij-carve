package org.markupcarve.carve.actions

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.includes.BundleInput
import org.markupcarve.carve.includes.CarveBundle
import org.markupcarve.carve.includes.CarveIncludeRoot
import org.markupcarve.carve.includes.CarveIncludeWalk
import org.markupcarve.carve.lsp.CarveLspBundle
import org.markupcarve.carve.lsp.CarveLspInitializationOptions
import org.markupcarve.carve.lsp.NodeLocator
import org.markupcarve.carve.settings.CarveSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * Write the document and every file it includes into a folder beside it.
 *
 * The right answer for "send it to a colleague who will keep editing it", where
 * flattening is the wrong shape: the files are COPIED, keeping the directives
 * and each child's own formatting. [ExportFlattenedCarveAction] is the other
 * half, merging children into the parent through the engine's expansion pass.
 *
 * This one needs only the include WALK, which the language server already
 * performs, so it never loads the engine.
 */
class ExportBundleAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val source = document.text
        val documentPath = runCatching { Path.of(file.path) }.getOrNull() ?: return

        val settings = CarveSettings.getInstance(project)
        val trusted = project.isTrusted()
        if (!settings.includeMode.resolvesWhen(trusted)) {
            Messages.showWarningDialog(
                project,
                "Include resolution is off for this document, so there is nothing to bundle. " +
                    "Trust the project, or set the include mode in Settings | Tools | Carve.",
                "Carve Bundle",
            )
            return
        }

        val node = NodeLocator.find(settings.nodePath)
        if (node == null) {
            Messages.showWarningDialog(
                project,
                "Node.js was not found on your PATH, so the Carve language server cannot resolve " +
                    "this document's includes. Install Node.js, or set its path in " +
                    "Settings | Tools | Carve.",
                "Carve Bundle",
            )
            return
        }
        val server = CarveLspBundle.extractServer()
        if (server == null) {
            Messages.showErrorDialog(
                project,
                "The bundled Carve language server is missing from the plugin, so includes " +
                    "cannot be resolved.",
                "Carve Bundle",
            )
            return
        }

        // The project base directory holding this file. lsp4ij starts the
        // editor's server with the same set (LSPIJUtils.getRoots reads
        // BaseProjectDirectories), and the server then picks the one containing
        // the document, so asking the platform which that is lands on the same
        // folder from the other end.
        val baseDirectories = listOfNotNull(
            BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file),
        ).mapNotNull { runCatching { Path.of(it.path) }.getOrNull() }
        val root = CarveIncludeRoot.of(settings.includeRoot, baseDirectories, documentPath)
        if (root == null) {
            Messages.showWarningDialog(
                project,
                "No usable include root for this document. An include root set in " +
                    "Settings | Tools | Carve has to be an absolute path.",
                "Carve Bundle",
            )
            return
        }

        run(project, node, server, documentPath, source, root, settings, trusted)
    }

    private fun run(
        project: Project,
        node: String,
        server: Path,
        documentPath: Path,
        source: String,
        root: Path,
        settings: CarveSettings,
        trusted: Boolean,
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Bundling Carve document", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val result = runCatching {
                        val walk = CarveIncludeWalk.walk(
                            nodePath = node,
                            serverPath = server,
                            documentPath = documentPath,
                            source = source,
                            workspaceRoot = root,
                            // The root is passed as the CONFIGURED one so the
                            // server enforces containment with the same object
                            // the bundle lays its files out under. Letting the
                            // server fall back would leave two derivations of a
                            // security boundary free to disagree.
                            initializationOptions = CarveLspInitializationOptions.build(
                                mode = settings.includeMode,
                                includeRoot = root.toString(),
                                workspaceTrusted = trusted,
                            ),
                        )
                        val bundle = CarveBundle.build(
                            BundleInput(
                                documentPath = documentPath,
                                source = source,
                                includeRoot = root,
                                walk = walk,
                            ),
                        )
                        val directory = CarveBundle.directoryFor(documentPath) { candidate ->
                            runCatching { Files.createDirectory(candidate) }.isSuccess
                        } ?: throw CarveIncludeWalk.WalkFailed(
                            "No free name left for a bundle of this document.",
                        )
                        for (entry in bundle.files) {
                            val target = directory.resolve(entry.path)
                            Files.createDirectories(target.parent)
                            Files.writeString(target, entry.source)
                        }
                        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
                        CarveBundle.summary(bundle, directory.fileName.toString())
                    }
                    ApplicationManager.getApplication().invokeLater {
                        result.fold(
                            onSuccess = { Messages.showInfoMessage(project, it, "Carve Bundle") },
                            onFailure = {
                                Messages.showErrorDialog(
                                    project,
                                    it.message ?: it.toString(),
                                    "Carve Bundle",
                                )
                            },
                        )
                    }
                }
            },
        )
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
