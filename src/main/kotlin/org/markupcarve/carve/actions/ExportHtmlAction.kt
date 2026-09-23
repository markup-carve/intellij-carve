package org.markupcarve.carve.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.CarveFileType

class ExportHtmlAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val content = document.text

        val descriptor = FileSaverDescriptor(
            "Export to HTML",
            "Choose location to save HTML file",
            "html",
        )
        val defaultName = file.nameWithoutExtension + ".html"
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = saveDialog.save(file.parent, defaultName) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val html = CarveConverter.toHtml(content, project)
            val fullHtml = CarveHtmlExport.page(file.nameWithoutExtension, html)
            ApplicationManager.getApplication().invokeLater {
                try {
                    wrapper.file.writeText(fullHtml)
                    Messages.showInfoMessage(
                        project,
                        "Exported to ${wrapper.file.absolutePath}",
                        "Export Successful",
                    )
                } catch (ex: Exception) {
                    Messages.showErrorDialog(project, "Failed to export: ${ex.message}", "Export Error")
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
