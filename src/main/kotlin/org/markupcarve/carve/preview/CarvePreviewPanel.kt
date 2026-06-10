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
                // Load with the file's directory as the document URL so relative image
                // paths (e.g. `![](logo.svg)`) resolve against the .crv file's folder.
                val baseUrl = file.parent?.let { "file://${it.path}/preview.html" }
                if (baseUrl != null) {
                    browser.loadHTML(createPreviewHtml(html, isDark), baseUrl)
                } else {
                    browser.loadHTML(createPreviewHtml(html, isDark))
                }
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
        dl { margin: 1em 0; }
        dt { font-weight: bold; margin-top: 0.75em; }
        dd { margin: 0 0 0 2em; }
        figure { margin: 1em 0; text-align: center; }
        figure img { display: block; margin: 0 auto; }
        figcaption { font-size: 0.9em; color: #666; margin-top: 0.5em; }
        table caption { caption-side: bottom; font-size: 0.9em; color: #666; padding-top: 0.5em; }
        body.dark figcaption, body.dark table caption { color: #aaa; }
        abbr[title] { text-decoration: underline dotted; cursor: help; }
        .mention strong, .tag strong { font-weight: 600; }
        .mention, .tag {
            display: inline-block;
            padding: 0 4px;
            border-radius: 4px;
            font-size: 0.95em;
        }
        .mention { background: #e7f0fb; color: #1c5fb4; }
        .tag { background: #eef3e7; color: #4a7a18; }
        body.dark .mention { background: #1f3147; color: #8fc0f6; }
        body.dark .tag { background: #28331f; color: #a9d36a; }
        /* Footnotes (djot doc-* roles) */
        [role="doc-endnotes"] { margin-top: 2em; font-size: 0.9em; color: #555; }
        [role="doc-endnotes"] hr { margin-bottom: 1em; }
        [role="doc-noteref"] { text-decoration: none; }
        [role="doc-backlink"] { text-decoration: none; margin-left: 0.4em; }
        body.dark [role="doc-endnotes"] { color: #aaa; }
        /* Admonitions: aside.admonition.{type}; generic custom types render as div */
        .admonition {
            margin: 1em 0;
            padding: 0.75em 1em;
            border-left: 4px solid #3498db;
            border-radius: 4px;
            background: #f4f8fd;
        }
        .admonition > :first-child { margin-top: 0; }
        .admonition > :last-child { margin-bottom: 0; }
        .admonition-title { font-weight: 700; margin: 0 0 0.4em; }
        .admonition.note,    .admonition.info    { border-color: #3498db; background: #f4f8fd; }
        .admonition.tip,     .admonition.success { border-color: #2ecc71; background: #f2fbf5; }
        .admonition.warning                       { border-color: #f39c12; background: #fef8ee; }
        .admonition.danger                        { border-color: #e74c3c; background: #fdf3f2; }
        .admonition.example                       { border-color: #9b59b6; background: #f9f4fb; }
        .admonition.quote                         { border-color: #95a5a6; background: #f7f9f9; }
        body.dark .admonition { background: #20262e; }
        body.dark .admonition.tip, body.dark .admonition.success { background: #1c2a20; }
        body.dark .admonition.warning { background: #2c2718; }
        body.dark .admonition.danger { background: #2c1d1b; }
        body.dark .admonition.example { background: #261d2b; }
        body.dark .admonition.quote { background: #23282a; }
        /* Math spans rendered by MathJax */
        .math.display { display: block; text-align: center; margin: 1em 0; }
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
    <script>
        window.MathJax = {
            tex: { inlineMath: [['\\(', '\\)']], displayMath: [['\\[', '\\]']] },
            options: { skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'] }
        };
    </script>
    <script async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
</head>
<body class="$themeClass">
    <div id="content">$initialHtml</div>
    <script>
        function updateContentHtml(html) {
            document.getElementById('content').innerHTML = html;
            highlightCode();
            typesetMath();
        }
        function highlightCode() {
            if (typeof hljs !== 'undefined') {
                document.querySelectorAll('pre code').forEach(function (block) {
                    hljs.highlightElement(block);
                });
            }
        }
        function typesetMath() {
            if (window.MathJax && MathJax.typesetPromise) {
                if (MathJax.typesetClear) { MathJax.typesetClear(); }
                MathJax.typesetPromise([document.getElementById('content')]);
            }
        }
        function renderAll() { highlightCode(); typesetMath(); }
        document.addEventListener('DOMContentLoaded', renderAll);
        if (document.readyState !== 'loading') { renderAll(); }
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
