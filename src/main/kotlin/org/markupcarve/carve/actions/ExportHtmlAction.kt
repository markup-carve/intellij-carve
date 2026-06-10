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
                    blockquote { border-left: 4px solid #3498db; margin: 1em 0; padding: 0.5em 0 0.5em 20px; color: #666; }
                    table { border-collapse: collapse; width: 100%; margin: 1em 0; }
                    th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
                    th { background: #f8f9fa; }
                    table caption { caption-side: bottom; font-size: 0.9em; color: #666; padding-top: 0.5em; }
                    mark { background: #fff3cd; }
                    del { color: #dc3545; }
                    ins { color: #28a745; text-decoration: none; border-bottom: 1px solid #28a745; }
                    img { max-width: 100%; height: auto; }
                    figure { margin: 1em 0; text-align: center; }
                    figcaption { font-size: 0.9em; color: #666; margin-top: 0.5em; }
                    dt { font-weight: bold; margin-top: 0.75em; }
                    dd { margin: 0 0 0 2em; }
                    abbr[title] { text-decoration: underline dotted; cursor: help; }
                    .mention, .tag { display: inline-block; padding: 0 4px; border-radius: 4px; font-size: 0.95em; }
                    .mention { background: #e7f0fb; color: #1c5fb4; }
                    .tag { background: #eef3e7; color: #4a7a18; }
                    [role="doc-endnotes"] { margin-top: 2em; font-size: 0.9em; color: #555; }
                    [role="doc-noteref"], [role="doc-backlink"] { text-decoration: none; }
                    [role="doc-backlink"] { margin-left: 0.4em; }
                    .admonition { margin: 1em 0; padding: 0.75em 1em; border-left: 4px solid #3498db;
                        border-radius: 4px; background: #f4f8fd; }
                    .admonition > :first-child { margin-top: 0; }
                    .admonition > :last-child { margin-bottom: 0; }
                    .admonition-title { font-weight: 700; margin: 0 0 0.4em; }
                    .admonition.tip, .admonition.success { border-color: #2ecc71; background: #f2fbf5; }
                    .admonition.warning { border-color: #f39c12; background: #fef8ee; }
                    .admonition.danger { border-color: #e74c3c; background: #fdf3f2; }
                    .admonition.example { border-color: #9b59b6; background: #f9f4fb; }
                    .admonition.quote { border-color: #95a5a6; background: #f7f9f9; }
                    .math.display { display: block; text-align: center; margin: 1em 0; }
                </style>
                <script>
                    window.MathJax = {
                        tex: { inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']] },
                        options: { skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'] }
                    };
                </script>
                <script async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
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
