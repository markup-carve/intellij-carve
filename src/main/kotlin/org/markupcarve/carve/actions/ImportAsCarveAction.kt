package org.markupcarve.carve.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.CarveImportFormat
import java.io.IOException

/** Convert a Markdown or HTML file to a sibling `.crv` file and open it. */
class ImportAsCarveAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val format = CarveImportFormat.forExtension(file.extension) ?: return
        val targetName = CarveImportFormat.targetName(file.name) ?: return
        val parent = file.parent ?: return

        val source = try {
            readSource(file)
        } catch (ex: IOException) {
            Messages.showErrorDialog(project, "Failed to read ${file.name}: ${ex.message}", "Import Error")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = CarveConverter.importToCarve(source, format)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val carve = result.getOrElse {
                    Messages.showErrorDialog(project, "Failed to import: ${it.message}", "Import Error")
                    return@invokeLater
                }
                if (parent.findChild(targetName) != null) {
                    val answer = Messages.showYesNoDialog(
                        project,
                        "$targetName already exists. Overwrite it?",
                        "Import ${format.label} as Carve",
                        Messages.getWarningIcon(),
                    )
                    if (answer != Messages.YES) return@invokeLater
                }
                try {
                    val target = WriteCommandAction.writeCommandAction(project)
                        .withName("Import ${format.label} as Carve")
                        .compute<VirtualFile, Exception> { write(parent, targetName, carve) }
                    FileEditorManager.getInstance(project).openFile(target, true)
                } catch (ex: Exception) {
                    Messages.showErrorDialog(project, "Failed to import: ${ex.message}", "Import Error")
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val format = file?.takeUnless { it.isDirectory }?.let { CarveImportFormat.forExtension(it.extension) }
        e.presentation.isEnabledAndVisible = format != null
        if (format != null) {
            e.presentation.text = "Import ${format.label} as Carve"
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /** An open target is replaced through its document, so a stale editor buffer cannot win. */
    private fun write(parent: VirtualFile, name: String, carve: String): VirtualFile {
        val existing = parent.findChild(name)
        val document = existing?.let { FileDocumentManager.getInstance().getDocument(it) }
        if (existing != null && document != null) {
            document.setText(carve)
            FileDocumentManager.getInstance().saveDocument(document)
            return existing
        }
        val out = existing ?: parent.createChildData(this, name)
        VfsUtil.saveText(out, carve)
        return out
    }

    private fun readSource(file: VirtualFile): String =
        FileDocumentManager.getInstance().getDocument(file)?.text ?: VfsUtilCore.loadText(file)
}
