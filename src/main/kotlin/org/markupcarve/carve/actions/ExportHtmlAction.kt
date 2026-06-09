package org.markupcarve.carve.actions

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
            val fullHtml = wrapFullHtml(file.nameWithoutExtension, html)
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

    private fun wrapFullHtml(title: String, content: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        line-height: 1.6;
                        max-width: 820px;
                        margin: 0 auto;
                        padding: 20px;
                        color: #333;
                    }
                    h1 { border-bottom: 2px solid #3498db; padding-bottom: 10px; }
                    h2 { border-bottom: 1px solid #ddd; padding-bottom: 5px; }
                    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px;
                        font-family: 'JetBrains Mono', Consolas, monospace; }
                    pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
                    pre code { background: none; padding: 0; }
                    blockquote { border-left: 4px solid #3498db; margin: 0; padding-left: 20px; color: #666; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid #ddd; padding: 8px 12px; }
                    th { background: #f8f9fa; }
                    mark { background: #fff3cd; }
                </style>
            </head>
            <body>
                $content
            </body>
            </html>
        """.trimIndent()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }
}
