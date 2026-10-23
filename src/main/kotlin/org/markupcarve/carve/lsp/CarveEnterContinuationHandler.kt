package org.markupcarve.carve.lsp

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport
import com.redhat.devtools.lsp4ij.client.features.LSPOnTypeFormattingFeature
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.TestOnly
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.lists.CarveListIndent.Position
import org.markupcarve.carve.lists.CarveListIndent.TextEdit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Enter in a Carve file: asks carve-lsp for its on-type edits (list markers, `> `
 * leads, `:::` and code fence closers) and applies them with [CarveEnterEdits], so
 * the caret stays on the new line instead of landing after a closer.
 *
 * Takes over from LSP4IJ's own on-type Enter handler, which [CarveOnTypeFormattingFeature]
 * switches off for this server.
 */
class CarveEnterContinuationHandler : EnterHandlerDelegateAdapter() {

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        val document = editor.document
        val virtualFile = file.virtualFile ?: FileDocumentManager.getInstance().getFile(document)
        val project = editor.project
        if (virtualFile == null || project == null || !isCarve(virtualFile)) return EnterHandlerDelegate.Result.Continue
        val backend = backend()
        backend.unavailableReason(project, file)?.let {
            log("no on-type request: $it")
            return EnterHandlerDelegate.Result.Continue
        }

        val caret = editor.caretModel.offset
        val line = document.getLineNumber(caret)
        val position = Position(line, caret - document.getLineStartOffset(line))
        val settings = editor.settings
        // LSP4IJ sends the pending didChange when the document is committed.
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val request = backend.request(project, virtualFile, position, settings.getTabSize(project), !settings.isUseTabCharacter(project))
        val edits = try {
            request.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            request.cancel(true)
            log("on-type request failed or timed out: $e")
            return EnterHandlerDelegate.Result.Continue
        }
        if (edits.isEmpty()) return EnterHandlerDelegate.Result.Continue

        val ranges = edits.map { edit ->
            CarveEnterEdits.Range(offset(document, edit.start) ?: return invalid(edits), offset(document, edit.end) ?: return invalid(edits), edit.newText.replace("\r", ""))
        }
        if (ranges.any { it.end < it.start }) return invalid(edits)
        val ordered = CarveEnterEdits.applicationOrder(ranges) ?: return invalid(edits)
        val target = CarveEnterEdits.caretAfter(caret, ranges)
        for (range in ordered) document.replaceString(range.start, range.end, range.text)
        editor.caretModel.moveToOffset(target)
        return EnterHandlerDelegate.Result.Stop
    }

    private fun invalid(edits: List<TextEdit>): EnterHandlerDelegate.Result {
        log("ignoring an on-type answer with an invalid or overlapping range: $edits")
        return EnterHandlerDelegate.Result.Continue
    }

    private fun offset(document: com.intellij.openapi.editor.Document, position: Position): Int? {
        if (position.line < 0 || position.character < 0) return null
        if (position.line >= document.lineCount) return document.textLength
        // A character past the end of the line means the end of that line, as LSP defines it.
        return minOf(document.getLineStartOffset(position.line) + position.character, document.getLineEndOffset(position.line))
    }

    companion object {
        private val LOG = Logger.getInstance(CarveEnterContinuationHandler::class.java)
        // Synchronous like LSP4IJ's own on-type wait, with the cap it uses inside a write action.
        private const val TIMEOUT_MILLIS = 500L

        @Volatile
        private var backendOverride: CarveOnTypeBackend? = null

        private fun backend(): CarveOnTypeBackend = backendOverride ?: Lsp4ijOnTypeBackend

        @TestOnly
        fun useBackendForTests(backend: CarveOnTypeBackend?) {
            backendOverride = backend
        }

        private fun log(message: String) = LOG.debug(message)

        private fun isCarve(file: VirtualFile): Boolean =
            file.fileType == CarveFileType || CarveFileType.matches(file.extension)
    }
}

/** How [CarveEnterContinuationHandler] reaches the server; a seam so tests can stand in for LSP4IJ. */
interface CarveOnTypeBackend {

    /** Null when a request may go out, else why Enter keeps its plain behavior. Must not block. */
    fun unavailableReason(project: Project, file: PsiFile): String?

    fun request(project: Project, file: VirtualFile, position: Position, tabSize: Int, insertSpaces: Boolean): CompletableFuture<List<TextEdit>>
}

/** The real backend: carve-lsp's textDocument/onTypeFormatting through LSP4IJ. */
object Lsp4ijOnTypeBackend : CarveOnTypeBackend {

    override fun unavailableReason(project: Project, file: PsiFile): String? {
        val manager = LanguageServerManager.getInstance(project)
        val status = manager.getServerStatus(Lsp4ijListIndentBackend.SERVER_ID)
        // Enter never waits for a server to start, as LSP4IJ's own handler does not.
        if (status != ServerStatus.started) return "server is $status, not started"
        val server = manager.getLanguageServer(Lsp4ijListIndentBackend.SERVER_ID)
        if (!server.isDone || server.isCompletedExceptionally) return "server is not resolved yet"
        val item = server.getNow(null) ?: return "LSP4IJ returned no server"
        if (!item.clientFeatures.formattingFeature.isOnTypeFormattingEnabled(file)) return "on-type formatting is off in the server settings"
        val provider = item.serverCapabilities?.documentOnTypeFormattingProvider ?: return "server has no on-type formatting"
        if (provider.firstTriggerCharacter != "\n" && provider.moreTriggerCharacter?.contains("\n") != true) return "Enter is not an on-type trigger"
        return null
    }

    override fun request(
        project: Project,
        file: VirtualFile,
        position: Position,
        tabSize: Int,
        insertSpaces: Boolean,
    ): CompletableFuture<List<TextEdit>> =
        LanguageServerManager.getInstance(project).getLanguageServer(Lsp4ijListIndentBackend.SERVER_ID).thenCompose { item ->
            val uri = item?.let { FileUriSupport.toString(file, it.clientFeatures) }
                ?: return@thenCompose CompletableFuture.completedFuture(emptyList())
            item.initializedServer.thenCompose { server ->
                val params = DocumentOnTypeFormattingParams(
                    TextDocumentIdentifier(uri),
                    FormattingOptions(tabSize, insertSpaces),
                    org.eclipse.lsp4j.Position(position.line, position.character),
                    "\n",
                )
                server.textDocumentService.onTypeFormatting(params).thenApply { edits ->
                    edits.orEmpty().filterNotNull().map {
                        TextEdit(
                            Position(it.range.start.line, it.range.start.character),
                            Position(it.range.end.line, it.range.end.character),
                            it.newText.orEmpty(),
                        )
                    }
                }
            }
        }
}

/** Switches off LSP4IJ's on-type Enter handler for carve-lsp; [CarveEnterContinuationHandler] sends the request instead. */
class CarveOnTypeFormattingFeature : LSPOnTypeFormattingFeature() {
    override fun isSupported(file: PsiFile): Boolean = false
}
