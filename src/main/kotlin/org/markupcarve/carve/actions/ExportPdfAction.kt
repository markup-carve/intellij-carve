package org.markupcarve.carve.actions

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.settings.CarveSettings
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ExportPdfAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val content = document.text
        val settings = CarveSettings.getInstance(project)
        val pdfPath = settings.pdfToolPath
        val browserPath = settings.pdfBrowserPath
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(
            FileSaverDescriptor("Export to PDF", "Choose location to save PDF file", "pdf"), project,
        )
        val target = dialog.save(file.parent, file.nameWithoutExtension + ".pdf")?.file ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val tool = PdfExportTools.find(pdfPath, browserPath)
            if (tool == null) {
                notify(project, "Install crv2pdf or a Chromium browser (Chrome, Chromium, or Edge), or set a path in Settings | Tools | Carve.", NotificationType.WARNING)
                return@executeOnPooledThread
            }
            val sourceDir = file.parent?.path?.let { java.io.File(it).toPath() }
            var temporary: java.nio.file.Path? = null
            var output: java.nio.file.Path? = null
            try {
                val inputDir = sourceDir ?: target.parentFile.toPath()
                temporary = Files.createTempFile(inputDir, ".carve-pdf-", if (tool.kind == PdfExportTools.Kind.CRV2PDF) ".crv" else ".html")
                output = Files.createTempFile(target.parentFile.toPath(), ".carve-pdf-", ".pdf")
                Files.delete(output)
                val input = temporary.toFile()
                if (tool.kind == PdfExportTools.Kind.CRV2PDF) {
                    input.writeText(content)
                } else {
                    val html = CarveConverter.toHtml(content, project)
                    input.writeText(CarveHtmlExport.page(file.nameWithoutExtension, html))
                }
                val result = CapturingProcessHandler(PdfExportTools.command(tool, input, output.toFile())).runProcess(120_000)
                if (result.isTimeout || result.exitCode != 0 || !Files.isRegularFile(output) || Files.size(output) == 0L) {
                    val detail = result.stderr.trim().ifEmpty { result.stdout.trim() }.ifEmpty {
                        if (result.isTimeout) "Timed out after 120 seconds" else "No PDF was produced"
                    }
                    notify(project, "PDF export failed: $detail", NotificationType.ERROR)
                } else {
                    Files.move(output, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    notify(project, "Exported to ${target.absolutePath}", NotificationType.INFORMATION)
                }
            } catch (ex: Exception) {
                notify(project, "PDF export failed: ${ex.message}", NotificationType.ERROR)
            } finally {
                temporary?.let { Files.deleteIfExists(it) }
                output?.let { Files.deleteIfExists(it) }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = CarveFileType.matches(e.getData(CommonDataKeys.VIRTUAL_FILE)?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private fun notify(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("Carve")
            .createNotification("Export to PDF", message, type).notify(project)
    }
}
