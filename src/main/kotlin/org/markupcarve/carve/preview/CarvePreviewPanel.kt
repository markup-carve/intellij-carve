package org.markupcarve.carve.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.messages.MessageBusConnection
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.markupcarve.carve.CarveConverter
import org.markupcarve.carve.settings.CarveSettings
import java.awt.BorderLayout
import java.awt.Point
import java.awt.datatransfer.StringSelection
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
 *
 * The page itself is built by [CarvePreviewHtml] and loads only assets unpacked
 * from the plugin by [CarvePreviewAssets], so a preview makes no network request
 * of any kind.
 */
class CarvePreviewPanel(
    private val project: Project,
    private val file: VirtualFile,
) : Disposable {

    private val panel = JPanel(BorderLayout())
    private val browser = JBCefBrowser.createBuilder().build()

    /**
     * The copy button's route to the clipboard.
     *
     * A page-side `navigator.clipboard.writeText` is the obvious choice and the
     * wrong one to rely on: it is gated on a permission that CEF's default
     * handler may refuse, and a refusal is silent. Going through the IDE has no
     * permission in the path at all, and it puts the text on the clipboard the
     * rest of the IDE reads - which is the one the user is reaching for. The
     * page keeps `navigator.clipboard` and `execCommand` behind it, for the
     * exported-HTML case where no bridge exists.
     */
    private val copyQuery: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

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

        copyQuery.addHandler { text ->
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            JBCefJSQuery.Response("ok")
        }


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
                document.documentElement.setAttribute('data-theme', '${if (isDark) "dark" else "light"}');
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
    /**
     * Read once per panel: the files are a few hundred lines and never change
     * while the IDE is running.
     */
    private val cachedCarveCss: String by lazy {
        listOf("tokens", "recipes")
            .mapNotNull { name ->
                CarvePreviewPanel::class.java.getResourceAsStream("/css/$name.css")
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            .joinToString("\n")
    }

    private fun readDocumentText(): String? =
        ReadAction.compute<String?, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(file)?.text
        }

    /**
     * `file://` base for the vendored browser assets.
     *
     * Lazy, and every caller reads it from a pooled thread: the first read
     * unpacks 5.2 MB out of the plugin jar, and the EDT is not where that
     * belongs. Lazy alone would not have been enough - the value has to be
     * pulled BEFORE the `invokeLater` that builds the page, not inside it.
     */
    private val assetBase: String by lazy { CarvePreviewAssets.baseUrl(CarvePreviewAssets.directory()) }

    /**
     * The body of `window.carveCopyText(text, onDone)`, as JS.
     *
     * `inject` writes the CEF query call plus the two callbacks; the success one
     * is what turns the button green, so a refused or lost copy cannot be
     * reported as a successful one. Both close over `onDone`, the caller's own
     * callback, rather than a global: the round trip is asynchronous, and two
     * buttons clicked in quick succession would otherwise answer each other.
     */
    private fun copyBridgeJs(): String =
        copyQuery.inject(
            "text",
            "function(response) { onDone(true); }",
            "function(errCode, errMsg) { onDone(false); }",
        )

    private fun loadPreviewShell() {
        val content = readDocumentText() ?: ""
        val isDark = isDarkTheme()
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = CarveConverter.toHtml(content, project, sourceLine = true)
            val css = userCss()
            // Both touch the disk on first use; neither may run on the EDT below.
            val assets = assetBase
            val carveStyles = carveCss()
            ApplicationManager.getApplication().invokeLater {
                // Load with the file's directory as the document URL so relative image
                // paths (e.g. `![](logo.svg)`) resolve against the .crv file's folder.
                pageReady = false
                val baseUrl = file.parent?.let { "file://${it.path}/preview.html" }
                val page = CarvePreviewHtml.create(
                    initialHtml = html,
                    isDark = isDark,
                    assetBase = assets,
                    carveCss = carveStyles,
                    userCss = css,
                    copyBridge = copyBridgeJs(),
                )
                if (baseUrl != null) {
                    browser.loadHTML(page, baseUrl)
                } else {
                    browser.loadHTML(page)
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

    /**
     * The vendored carve-css layers, cached after the first read.
     *
     * `tokens.css` defines the `--carve-*` custom properties and nothing else,
     * so it cannot argue with the built-in styles above it. `recipes.css` styles
     * the constructs the engine has no handler for - `::: tree`, `::: cards`,
     * `::: columns` and the rest - which reach the page as a generic
     * `<div class="name">` and would otherwise render unstyled here while every
     * other Carve consumer that installs carve-css shows them properly.
     *
     * Injected after the built-in block and before the user's own CSS, so the
     * precedence order the class docs promise is unchanged.
     */
    private fun carveCss(): String = cachedCarveCss

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

    override fun dispose() {
        updateTimer.stop()
        copyQuery.dispose()
        browser.dispose()
    }
}
