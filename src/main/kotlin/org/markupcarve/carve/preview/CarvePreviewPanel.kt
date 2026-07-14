package org.markupcarve.carve.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.messages.MessageBusConnection
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.settings.CarveSettings
import java.awt.BorderLayout
import java.awt.Point
import java.io.File
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
    private val browser = JBCefBrowser.createBuilder().build()
    private val updatePending = AtomicBoolean(false)
    private val updateTimer: Timer
    private var initialized = false
    private val messageBusConnection: MessageBusConnection

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) = scheduleUpdate()
    }

    /** Last line pushed to the preview, so an unchanged scroll does not re-run JS. */
    private var lastSyncedLine = -1

    /**
     * `loadHTML` is asynchronous, so `initialized` only means "we asked the browser to load" -
     * `window.carveScrollToLine` may not exist yet. Scrolling is therefore gated on the CEF
     * load-end callback instead; otherwise the first sync silently no-ops while still being
     * cached, and the preview sits unsynced until the editor moves to a different line.
     */
    @Volatile
    private var pageReady = false

    /**
     * This file's document, resolved once under a read action.
     *
     * [VisibleAreaListener] fires on the EDT outside any read action, and
     * `FileDocumentManager.getDocument()` is model access - calling it there throws
     * "Read access is allowed from inside read-action only". Resolving the document once
     * here keeps the listener free of any model lookup: comparing `editor.document` to a
     * cached reference needs no read action.
     */
    private val document = ReadAction.compute<com.intellij.openapi.editor.Document?, RuntimeException> {
        FileDocumentManager.getInstance().getDocument(file)
    }

    /**
     * Scrolls the preview in step with the editor.
     *
     * The multicaster fires for every editor, so only events for *this* file's document are
     * acted on. The editor's top visible line maps onto the nearest preceding
     * `data-source-line` anchor in the rendered HTML (stamped by carve-js when
     * `sourceLine` is on). One-way, editor -> preview: syncing back would need a scroll
     * listener on the browser and risks a feedback loop for no real gain.
     */
    private val visibleAreaListener = VisibleAreaListener { event ->
        val editor = event.editor
        if (editor.document != document) return@VisibleAreaListener
        scrollPreviewToLine(topVisibleLine(editor))
    }

    /** Editor's top visible line, 1-based to match `data-source-line`. */
    private fun topVisibleLine(editor: com.intellij.openapi.editor.Editor): Int =
        editor.xyToLogicalPosition(Point(0, editor.scrollingModel.visibleArea.y)).line + 1

    init {
        panel.add(browser.component, BorderLayout.CENTER)

        // The page is only scrollable once its script has actually run.
        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == false) return
                    pageReady = true
                    lastSyncedLine = -1 // fresh page: nothing has been synced to it yet
                    ApplicationManager.getApplication().invokeLater { syncFromEditor() }
                }
            },
            browser.cefBrowser,
        )

        updateTimer = Timer(300) {
            if (updatePending.getAndSet(false)) {
                updatePreview()
            }
        }
        updateTimer.isRepeats = false

        document?.addDocumentListener(documentListener, this)

        messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        messageBusConnection.subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener { updateTheme() },
        )

        EditorFactory.getInstance().eventMulticaster
            .addVisibleAreaListener(visibleAreaListener, this)

        loadPreviewShell()
    }

    /**
     * Deliberately caches [lastSyncedLine] only *after* the call actually dispatches. Caching
     * it while the shell is still loading would swallow the first real sync: the JS never ran,
     * yet a later event for the same line would be deduped away and the preview would sit
     * unsynced until the editor happened to move to a different line.
     */
    private fun scrollPreviewToLine(line: Int) {
        if (!pageReady) return
        if (line == lastSyncedLine) return
        lastSyncedLine = line
        browser.cefBrowser.executeJavaScript(
            "window.carveScrollToLine && window.carveScrollToLine($line);",
            browser.cefBrowser.url,
            0,
        )
    }

    /**
     * Aligns the preview with the editor once the shell is up - the preview can be opened on a
     * document that is already scrolled, and no visible-area event fires for a viewport that
     * never moves.
     */
    private fun syncFromEditor() {
        val doc = document ?: return
        val editor = ReadAction.compute<com.intellij.openapi.editor.Editor?, RuntimeException> {
            EditorFactory.getInstance().getEditors(doc, project).firstOrNull()
        } ?: return
        scrollPreviewToLine(topVisibleLine(editor))
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
        // Mermaid bakes the theme into the rendered SVG (and its source <pre> is
        // consumed on render), so a body-class swap alone leaves diagrams in the
        // old theme. Re-run the conversion + hydrate so diagrams repaint.
        updatePreview()
    }

    /**
     * Reads the current document text under a [ReadAction].
     *
     * Reading the PSI/document model is only allowed from inside a read action;
     * a bare read on the EDT (or any thread) trips the platform
     * `ThreadingAssertions` ("Read access is allowed from inside read-action
     * only"). We snapshot just the text here and hand the plain string to the
     * GraalJS conversion, which needs no IDE model - so no further model access
     * happens off this read action.
     */
    private fun readDocumentText(): String? =
        ReadAction.compute<String?, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(file)?.text
        }

    private fun loadPreviewShell() {
        val content = readDocumentText() ?: ""
        val isDark = isDarkTheme()
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = CarveConverter.toHtml(content, project, sourceLine = true)
            val css = userCss()
            ApplicationManager.getApplication().invokeLater {
                // Load with the file's directory as the document URL so relative image
                // paths (e.g. `![](logo.svg)`) resolve against the .crv file's folder.
                pageReady = false
                val baseUrl = file.parent?.let { "file://${it.path}/preview.html" }
                if (baseUrl != null) {
                    browser.loadHTML(createPreviewHtml(html, isDark, css), baseUrl)
                } else {
                    browser.loadHTML(createPreviewHtml(html, isDark, css))
                }
                initialized = true
            }
        }
    }

    /**
     * User-supplied CSS, concatenated so later sources override earlier ones:
     * project `carve-preview.css` (file folder, then project root, then
     * `.carve/preview.css`), then the settings "Custom CSS file". Injected after
     * the built-in styles, so user rules of equal specificity win.
     */
    private fun userCss(): String {
        val candidates = mutableListOf<File>()
        file.parent?.path?.let { candidates += File(it, "carve-preview.css") }
        project.basePath?.let { base ->
            candidates += File(base, "carve-preview.css")
            candidates += File(base, ".carve/preview.css")
        }
        CarveSettings.getInstance(project).customCssPath
            .takeIf { it.isNotBlank() }
            ?.let { candidates += File(it) }

        val seen = HashSet<String>()
        return candidates
            .filter { it.isFile && seen.add(it.absolutePath) }
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
            .joinToString("\n")
    }

    private fun updatePreview() {
        if (!initialized) {
            loadPreviewShell()
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val content = readDocumentText()
                ?: return@executeOnPooledThread
            val html = CarveConverter.toHtml(content, project, sourceLine = true)
            val escaped = escapeForJs(html)
            ApplicationManager.getApplication().invokeLater {
                browser.cefBrowser.executeJavaScript(
                    "updateContentHtml(`$escaped`);",
                    browser.cefBrowser.url,
                    0,
                )
                // The swap replaces every anchor and can change the height of content above the
                // viewport, so the browser's old pixel offset no longer means the same line.
                // Drop the dedupe and re-align, otherwise an edit that leaves the editor's top
                // line unchanged would leave the preview stale until the user scrolls elsewhere.
                lastSyncedLine = -1
                syncFromEditor()
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

    private fun createPreviewHtml(initialHtml: String, isDark: Boolean, userCss: String): String {
        val themeClass = if (isDark) "dark" else "light"
        val userStyle = if (userCss.isBlank()) "" else "<style id=\"carve-user-css\">\n$userCss\n</style>"
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
        /* Featured: emphasized block (e.g. a heading carrying {.featured}) */
        .featured {
            background: linear-gradient(90deg, #eaf4ff, transparent);
            border-left: 4px solid #3498db;
            padding: 0.3em 0.6em;
            border-radius: 4px;
        }
        body.dark .featured { background: linear-gradient(90deg, #1f3147, transparent); }
        /* Status classes (carve [text]{.class} inline spans) */
        .error { color: #c0392b; font-weight: 600; }
        .success { color: #27ae60; font-weight: 600; }
        .warn { color: #b9770e; font-weight: 600; }
        li:has(> .error), li:has(> input + .error) { background: #fdf3f2; border-radius: 4px; }
        li:has(> .success), li:has(> input + .success) { background: #f2fbf5; border-radius: 4px; }
        body.dark .error { color: #ff8a7a; }
        body.dark .success { color: #7fd99a; }
        body.dark li:has(.error) { background: #2c1d1b; }
        body.dark li:has(.success) { background: #1c2a20; }

        /* ---- Code-block chrome: language badge + #201 quoted header caption ----
           carve-js emits `<pre title="HEADER"><code class="language-x">`. The
           language and the optional header are attribute-only, so nothing shows
           without these rules. We surface the language as a badge in the top-right
           corner and, when a `title` is present, a filename/caption bar on top. */
        pre { position: relative; }
        /* Language badge: read from the code element's language-* class. The badge
           text comes from a data-lang attribute the hydrate JS copies onto the pre. */
        pre[data-lang]::after {
            content: attr(data-lang);
            position: absolute;
            top: 6px;
            right: 8px;
            padding: 1px 7px;
            font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
            font-size: 0.7em;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.03em;
            color: #6a737d;
            background: rgba(255, 255, 255, 0.7);
            border: 1px solid #d0d7de;
            border-radius: 5px;
            pointer-events: none;
        }
        body.dark pre[data-lang]::after {
            color: #8b949e;
            background: rgba(0, 0, 0, 0.35);
            border-color: #30363d;
        }
        /* #201 header caption bar (a code-block filename/title). The hydrate JS
           promotes `pre[title]` into a wrapper carrying a visible caption. */
        .code-with-header { margin: 1em 0; }
        .code-with-header > .code-header {
            font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
            font-size: 0.8em;
            font-weight: 600;
            color: #57606a;
            background: #eaeef2;
            border: 1px solid #d0d7de;
            border-bottom: none;
            border-radius: 5px 5px 0 0;
            padding: 6px 12px;
        }
        .code-with-header > pre { margin: 0; border-top-left-radius: 0; border-top-right-radius: 0; }
        body.dark .code-with-header > .code-header {
            color: #adbac7;
            background: #2d333b;
            border-color: #444c56;
        }

        /* ---- Code group / tabs (codeGroup + tabs extensions) ----
           Both emit CSS-only radio-tab widgets: a run of
           `input.{code-group,tabs}-radio` + `label.*-label`, then the panels.
           `:checked ~ nth-of-type` wires each radio to its label + panel, so tab
           switching needs no JS. Mirrors the docs custom.css. */
        .code-group, .tabs { margin: 12px 0; position: relative; --tab-active-bg: #f6f8fa; --tab-active-border: #d0d7de; }
        body.dark .code-group, body.dark .tabs { --tab-active-bg: #2d333b; --tab-active-border: #30363d; }
        .code-group-radio, .tabs-radio {
            position: absolute; top: 0; left: 0; width: 1px; height: 1px;
            opacity: 0; pointer-events: none;
        }
        .code-group-label, .tabs-label {
            display: inline-block; cursor: pointer; padding: 4px 12px; font-size: 0.85em;
            color: #6a737d; border: 1px solid transparent; border-bottom: none;
            border-radius: 6px 6px 0 0;
        }
        .code-group-panel, .tabs-panel {
            display: none; border: 1px solid #d0d7de; border-radius: 0 6px 6px 6px;
            padding: 0 12px;
        }
        .code-group-panel > pre, .tabs-panel > pre { margin: 0.6em 0; }
        body.dark .code-group-label, body.dark .tabs-label { color: #8b949e; }
        body.dark .code-group-panel, body.dark .tabs-panel { border-color: #30363d; }
        /* Up to 8 tabs per widget. */
        .code-group-radio:nth-of-type(1):checked ~ .code-group-label:nth-of-type(1),
        .tabs-radio:nth-of-type(1):checked ~ .tabs-label:nth-of-type(1) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(1):checked ~ .code-group-panel:nth-of-type(1),
        .tabs-radio:nth-of-type(1):checked ~ .tabs-panel:nth-of-type(1) { display: block; }
        .code-group-radio:nth-of-type(2):checked ~ .code-group-label:nth-of-type(2),
        .tabs-radio:nth-of-type(2):checked ~ .tabs-label:nth-of-type(2) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(2):checked ~ .code-group-panel:nth-of-type(2),
        .tabs-radio:nth-of-type(2):checked ~ .tabs-panel:nth-of-type(2) { display: block; }
        .code-group-radio:nth-of-type(3):checked ~ .code-group-label:nth-of-type(3),
        .tabs-radio:nth-of-type(3):checked ~ .tabs-label:nth-of-type(3) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(3):checked ~ .code-group-panel:nth-of-type(3),
        .tabs-radio:nth-of-type(3):checked ~ .tabs-panel:nth-of-type(3) { display: block; }
        .code-group-radio:nth-of-type(4):checked ~ .code-group-label:nth-of-type(4),
        .tabs-radio:nth-of-type(4):checked ~ .tabs-label:nth-of-type(4) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(4):checked ~ .code-group-panel:nth-of-type(4),
        .tabs-radio:nth-of-type(4):checked ~ .tabs-panel:nth-of-type(4) { display: block; }
        .code-group-radio:nth-of-type(5):checked ~ .code-group-label:nth-of-type(5),
        .tabs-radio:nth-of-type(5):checked ~ .tabs-label:nth-of-type(5) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(5):checked ~ .code-group-panel:nth-of-type(5),
        .tabs-radio:nth-of-type(5):checked ~ .tabs-panel:nth-of-type(5) { display: block; }
        .code-group-radio:nth-of-type(6):checked ~ .code-group-label:nth-of-type(6),
        .tabs-radio:nth-of-type(6):checked ~ .tabs-label:nth-of-type(6) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(6):checked ~ .code-group-panel:nth-of-type(6),
        .tabs-radio:nth-of-type(6):checked ~ .tabs-panel:nth-of-type(6) { display: block; }
        .code-group-radio:nth-of-type(7):checked ~ .code-group-label:nth-of-type(7),
        .tabs-radio:nth-of-type(7):checked ~ .tabs-label:nth-of-type(7) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(7):checked ~ .code-group-panel:nth-of-type(7),
        .tabs-radio:nth-of-type(7):checked ~ .tabs-panel:nth-of-type(7) { display: block; }
        .code-group-radio:nth-of-type(8):checked ~ .code-group-label:nth-of-type(8),
        .tabs-radio:nth-of-type(8):checked ~ .tabs-label:nth-of-type(8) { color: inherit; background: var(--tab-active-bg); border-color: var(--tab-active-border); }
        .code-group-radio:nth-of-type(8):checked ~ .code-group-panel:nth-of-type(8),
        .tabs-radio:nth-of-type(8):checked ~ .tabs-panel:nth-of-type(8) { display: block; }

        /* ---- Details / spoiler (details + spoiler extensions) ----
           details: native `<details><summary>`. spoiler: inline
           `<span class="spoiler">` (blur, JS toggle) and block
           `<details class="spoiler">` (native). Mirrors the docs custom.css. */
        details { margin: 1em 0; border: 1px solid #d0d7de; border-radius: 8px; padding: 2px 14px; }
        details > summary { cursor: pointer; font-weight: 600; padding: 6px 0; }
        details[open] > summary { margin-bottom: 4px; }
        details > :last-child { margin-bottom: 8px; }
        body.dark details { border-color: #30363d; }

        span.spoiler {
            filter: blur(0.3em); cursor: pointer; border-radius: 3px; padding: 0 0.15em;
            background: rgba(127, 127, 127, 0.14);
            -webkit-user-select: none; user-select: none; transition: filter 0.2s;
        }
        span.spoiler.revealed { filter: none; background: transparent; user-select: text; }
        details.spoiler { border-left: 4px solid #e0af68; }
        details.spoiler > summary { color: #e0af68; list-style: none; }
        details.spoiler > summary::-webkit-details-marker { display: none; }
        details.spoiler > summary::before { content: '\01F441 '; }
        details.spoiler > summary::after {
            content: ' (click to reveal)'; color: #999; font-weight: 400; font-size: 0.85em;
        }
        details.spoiler[open] > summary::after { content: ''; }

        /* ---- Mermaid: emitted as `<pre class="mermaid">DEF</pre>`; the hydrate
           JS replaces it with the rendered SVG inside `.mermaid-rendered`. ---- */
        pre.mermaid { background: none; padding: 0; text-align: center; }
        .mermaid-rendered { margin: 1em 0; text-align: center; }
        .mermaid-rendered svg { max-width: 100%; height: auto; }

        /* ---- Chart: emitted as `<div class="chart"><script type=application/json>`;
           the hydrate JS swaps in a <canvas>. ---- */
        div.chart { max-width: 560px; margin: 1em 0; }
        div.chart > script { display: none; }

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
    <!-- Mermaid 11 (matches the docs site `mermaid@^11.15.0`) - manual start, theme-aware. -->
    <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
    <!-- Chart.js 4 (matches the docs site `chart.js@^4.5.1`). -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
    $userStyle
</head>
<body class="$themeClass">
    <div id="content">$initialHtml</div>
    <script>
        var chartInstances = [];

        // A single hydrate() pass, run BOTH on initial load AND after every
        // updateContentHtml() innerHTML swap. Anything done only once at page
        // load would not apply to edited content, so every extension's client
        // runtime is re-run here against the freshly-swapped markup.
        function updateContentHtml(html) {
            document.getElementById('content').innerHTML = html;
            hydrate();
        }

        function root() { return document.getElementById('content'); }

        // Scroll sync: carve-js stamps every top-level block with data-source-line (1-based),
        // so we scroll to the last block that starts at or before the editor's top visible
        // line. Called from the editor's VisibleAreaListener. A no-op when the markup has no
        // anchors (e.g. the carve-php renderer, which cannot emit them).
        window.carveScrollToLine = function (line) {
            var nodes = root().querySelectorAll('[data-source-line]');
            if (!nodes.length) return;
            var target = null;
            for (var i = 0; i < nodes.length; i++) {
                if (parseInt(nodes[i].getAttribute('data-source-line'), 10) <= line) {
                    target = nodes[i];
                } else {
                    break;
                }
            }
            if (target) {
                target.scrollIntoView({ block: 'start' });
            } else {
                window.scrollTo(0, 0);
            }
        };

        function hydrate() {
            tagCodeBlocks();
            promoteCodeHeaders();
            wireSpoilers();
            highlightCode();
            renderMermaid();
            renderCharts();
            typesetMath();
        }

        // Copy the code element's `language-x` onto the <pre> as data-lang so the
        // CSS badge (pre[data-lang]::after) shows the language.
        function tagCodeBlocks() {
            root().querySelectorAll('pre > code[class*="language-"]').forEach(function (code) {
                var pre = code.parentElement;
                if (!pre || pre.dataset.lang) return;
                var cls = Array.prototype.find.call(code.classList, function (c) {
                    return c.indexOf('language-') === 0;
                });
                if (cls) pre.dataset.lang = cls.slice('language-'.length);
            });
        }

        // #201 quoted header: carve-js puts it on `pre[title]`. Wrap the <pre> in
        // a `.code-with-header` div carrying a visible caption bar.
        function promoteCodeHeaders() {
            root().querySelectorAll('pre[title]').forEach(function (pre) {
                if (pre.parentElement && pre.parentElement.classList.contains('code-with-header')) return;
                var title = pre.getAttribute('title');
                if (!title) return;
                var wrap = document.createElement('div');
                wrap.className = 'code-with-header';
                var bar = document.createElement('div');
                bar.className = 'code-header';
                bar.textContent = title;
                pre.parentNode.insertBefore(wrap, pre);
                wrap.appendChild(bar);
                wrap.appendChild(pre);
            });
        }

        // Inline `<span class="spoiler">` reveals on click/keyboard. The block
        // form `<details class="spoiler">` is native and needs no JS.
        function wireSpoilers() {
            root().querySelectorAll('span.spoiler').forEach(function (el) {
                if (el.dataset.spoilerWired) return;
                el.dataset.spoilerWired = '1';
                el.tabIndex = 0;
                el.setAttribute('role', 'button');
                el.title = 'Click to reveal';
                function toggle() { el.classList.toggle('revealed'); }
                el.addEventListener('click', toggle);
                el.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
                });
            });
        }

        function highlightCode() {
            if (typeof hljs === 'undefined') return;
            root().querySelectorAll('pre > code[class*="language-"]').forEach(function (block) {
                if (block.classList.contains('language-mermaid')) return;
                hljs.highlightElement(block);
            });
        }

        // Mermaid: `<pre class="mermaid">DEF</pre>` -> rendered SVG. Theme-aware.
        var mermaidSeq = 0;
        function renderMermaid() {
            if (typeof mermaid === 'undefined') return;
            var blocks = root().querySelectorAll('pre.mermaid, pre > code.language-mermaid');
            if (!blocks.length) return;
            var dark = document.body.classList.contains('dark');
            mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', theme: dark ? 'dark' : 'default' });
            Array.prototype.forEach.call(blocks, function (el) {
                var pre = el.tagName === 'PRE' ? el : el.parentElement;
                if (!pre) return;
                var def = el.textContent || '';
                try {
                    mermaid.render('carve-mermaid-' + (mermaidSeq++), def).then(function (res) {
                        var fig = document.createElement('div');
                        fig.className = 'mermaid-rendered';
                        fig.innerHTML = res.svg;
                        if (pre.parentNode) pre.replaceWith(fig);
                    }).catch(function () {});
                } catch (e) { /* leave the raw block on a parse error */ }
            });
        }

        // Chart.js: `<div class="chart"><script type=application/json>CONFIG</` +
        // `script></div>` -> a <canvas>. Old instances are destroyed first to
        // avoid leaks / duplicate canvases on re-hydrate.
        var chartSeq = 0;
        function renderCharts() {
            if (typeof Chart === 'undefined') return;
            while (chartInstances.length) {
                var inst = chartInstances.pop();
                try { inst.destroy(); } catch (e) {}
            }
            root().querySelectorAll('div.chart').forEach(function (el) {
                var script = el.querySelector('script[type="application/json"]');
                if (!script) return;
                var config;
                try { config = JSON.parse(script.textContent || ''); } catch (e) { return; }
                var canvas = document.createElement('canvas');
                canvas.id = 'carve-chart-' + (chartSeq++);
                el.replaceChildren(canvas);
                try { chartInstances.push(new Chart(canvas, config)); } catch (e) {}
            });
        }

        function typesetMath() {
            if (window.MathJax && MathJax.typesetPromise) {
                if (MathJax.typesetClear) { MathJax.typesetClear(); }
                MathJax.typesetPromise([root()]);
            }
        }

        document.addEventListener('DOMContentLoaded', hydrate);
        if (document.readyState !== 'loading') { hydrate(); }
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
