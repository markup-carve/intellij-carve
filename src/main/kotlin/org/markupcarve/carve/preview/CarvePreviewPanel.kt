package org.markupcarve.carve.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.messages.MessageBusConnection
import org.markupcarve.carve.CarveConverter
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Live HTML preview for a Carve file.
 *
 * Rendering always happens server-side (GraalJS carve-js or the carve-php CLI,
 * per [org.markupcarve.carve.settings.CarveSettings]); the resulting HTML is
 * injected into a JCEF browser. The browser only displays HTML and runs
 * highlight.js for code blocks, so no Carve renderer ships in the WebView.
 */
class CarvePreviewPanel(
    private val project: Project,
    private val file: VirtualFile,
) : Disposable {

    private val panel = JPanel(BorderLayout())
    private val browser = JBCefBrowser()
    private val updatePending = AtomicBoolean(false)
    private val updateTimer: Timer
    private var initialized = false
    private val messageBusConnection: MessageBusConnection

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) = scheduleUpdate()
    }

    init {
        panel.add(browser.component, BorderLayout.CENTER)

        updateTimer = Timer(300) {
            if (updatePending.getAndSet(false)) {
                updatePreview()
            }
        }
        updateTimer.isRepeats = false

        FileDocumentManager.getInstance().getDocument(file)
            ?.addDocumentListener(documentListener, this)

        messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        messageBusConnection.subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { updateTheme() },
        )

        loadPreviewShell()
    }

    val component: JComponent get() = panel

    private fun isDarkTheme(): Boolean {
        val background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        val luminance = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255
        return luminance < 0.5
    }

    private fun scheduleUpdate() {
        updatePending.set(true)
        updateTimer.restart()
    }

    private fun updateTheme() {
        if (!initialized) return
        val isDark = isDarkTheme()
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(
                """
                document.body.classList.toggle('dark', $isDark);
                document.body.classList.toggle('light', ${!isDark});
                document.getElementById('hljs-light').disabled = $isDark;
                document.getElementById('hljs-dark').disabled = ${!isDark};
                """.trimIndent(),
                browser.cefBrowser.url,
                0,
            )
        }
    }

    private fun loadPreviewShell() {
        val content = FileDocumentManager.getInstance().getDocument(file)?.text ?: ""
        val isDark = isDarkTheme()
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = CarveConverter.toHtml(content, project)
            ApplicationManager.getApplication().invokeLater {
                browser.loadHTML(createPreviewHtml(html, isDark))
                initialized = true
            }
        }
    }

    private fun updatePreview() {
        if (!initialized) {
            loadPreviewShell()
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val content = FileDocumentManager.getInstance().getDocument(file)?.text
                ?: return@executeOnPooledThread
            val html = CarveConverter.toHtml(content, project)
            val escaped = escapeForJs(html)
            ApplicationManager.getApplication().invokeLater {
                browser.cefBrowser.executeJavaScript(
                    "updateContentHtml(`$escaped`);",
                    browser.cefBrowser.url,
                    0,
                )
            }
        }
    }

    private fun escapeForJs(content: String): String =
        content
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

    private fun createPreviewHtml(initialHtml: String, isDark: Boolean): String {
        val themeClass = if (isDark) "dark" else "light"
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            line-height: 1.6;
            padding: 20px;
            max-width: 820px;
            margin: 0 auto;
            color: #333;
            background: #fff;
        }
        body.dark { background: #1e1e1e; color: #d4d4d4; }
        body.dark a { color: #6db3f2; }
        body.dark code, body.dark pre { background: #2d2d2d; }
        body.dark blockquote { border-color: #444; color: #aaa; }
        body.dark table, body.dark th, body.dark td { border-color: #444; }
        body.dark th { background: #2d2d2d; }
        body.dark hr { border-color: #444; }
        body.dark h1 { color: #e0e0e0; border-bottom-color: #6db3f2; }
        body.dark h2 { color: #c0c0c0; border-bottom-color: #444; }
        body.dark h3, body.dark h4, body.dark h5, body.dark h6 { color: #a0a0a0; }
        body.dark mark { background: #5a5000; color: #fff; }
        h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; margin-top: 0; }
        h2 { color: #34495e; border-bottom: 1px solid #bdc3c7; padding-bottom: 5px; }
        h3, h4, h5, h6 { color: #7f8c8d; }
        code {
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
            font-size: 0.9em;
        }
        pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
        pre code { background: none; padding: 0; }
        blockquote {
            border-left: 4px solid #3498db;
            margin: 1em 0;
            padding: 0.5em 0 0.5em 20px;
            color: #666;
        }
        blockquote p { margin: 0; }
        table { border-collapse: collapse; width: 100%; margin: 1em 0; }
        th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
        th { background: #f8f9fa; font-weight: 600; }
        mark { background: #fff3cd; padding: 2px 4px; border-radius: 2px; }
        del { color: #dc3545; text-decoration: line-through; }
        ins { color: #28a745; text-decoration: none; border-bottom: 1px solid #28a745; }
        a { color: #3498db; text-decoration: none; }
        a:hover { text-decoration: underline; }
        img { max-width: 100%; height: auto; }
        hr { border: none; border-top: 1px solid #ddd; margin: 2em 0; }
        ul, ol { padding-left: 2em; }
        li { margin: 0.25em 0; }
        li:has(> input[type="checkbox"]) { list-style: none; margin-left: -1.5em; }
        li > input[type="checkbox"] { margin-right: 0.5em; width: 1em; height: 1em; vertical-align: middle; }
        sup, sub { font-size: 0.75em; }
        dt { font-weight: bold; margin-top: 1em; }
        dd { margin-left: 2em; }
        #content { min-height: 100px; }
    </style>
    <link id="hljs-light" rel="stylesheet"
        href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/styles/github.min.css"${if (isDark) " disabled" else ""}>
    <link id="hljs-dark" rel="stylesheet"
        href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/styles/github-dark.min.css"${if (!isDark) " disabled" else ""}>
    <style>
        body.light pre code.hljs { background: #f6f8fa; }
        body.dark pre code.hljs { background: #161b22; }
    </style>
    <script src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/highlight.min.js"></script>
</head>
<body class="$themeClass">
    <div id="content">$initialHtml</div>
    <script>
        function updateContentHtml(html) {
            document.getElementById('content').innerHTML = html;
            highlightCode();
        }
        function highlightCode() {
            if (typeof hljs !== 'undefined') {
                document.querySelectorAll('pre code').forEach(function (block) {
                    hljs.highlightElement(block);
                });
            }
        }
        document.addEventListener('DOMContentLoaded', highlightCode);
        if (document.readyState !== 'loading') { highlightCode(); }
    </script>
</body>
</html>
        """.trimIndent()
    }

    override fun dispose() {
        updateTimer.stop()
        browser.dispose()
    }
}
