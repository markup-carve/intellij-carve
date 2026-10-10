package org.markupcarve.carve.lsp

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport
import org.eclipse.lsp4j.ExecuteCommandParams
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.lists.CarveListIndent
import org.markupcarve.carve.lists.CarveListIndent.Direction
import org.markupcarve.carve.lists.CarveListIndent.Outcome
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Tab / Shift+Tab on Carve list items, through carve-lsp's `carve.listIndent`.
 *
 * Registered only in carve-lsp.xml, which loads only with LSP4IJ, so this class
 * may name LSP4IJ and lsp4j types. Every case the server cannot answer goes to
 * the wrapped handler synchronously; only a press on list lines with a running
 * server that advertises the command waits for the server, off the EDT.
 */
abstract class CarveListIndentHandler(
    private val original: EditorActionHandler,
    private val direction: Direction,
) : EditorActionHandler() {

    class Indent(original: EditorActionHandler) : CarveListIndentHandler(original, Direction.INDENT)
    class Outdent(original: EditorActionHandler) : CarveListIndentHandler(original, Direction.OUTDENT)

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        // A press while an answer is pending waits its turn, so held or fast Tabs all land.
        val pending = editor.getUserData(PENDING)
        if (caret == null && pending != null) {
            pending.add { press(editor) }
            return
        }
        val request = if (caret == null) prepare(editor) else null
        if (request == null) {
            original.execute(editor, caret, dataContext)
            return
        }
        editor.putUserData(PENDING, ArrayDeque())
        try {
            send(request)
        } catch (e: RuntimeException) {
            thisLogger().warn("carve.listIndent could not be sent", e)
            editor.putUserData(PENDING, null)
            original.execute(editor, null, dataContext)
        }
    }

    /** A queued press, replayed on the EDT as its own command. */
    private fun press(editor: Editor) {
        if (editor.isDisposed) return
        val project = editor.project ?: return
        CommandProcessor.getInstance().executeCommand(
            project,
            { doExecute(editor, null, DataManager.getInstance().getDataContext(editor.contentComponent)) },
            commandName(),
            null,
            editor.document,
        )
    }

    private fun drain(editor: Editor) {
        val queued = editor.getUserData(PENDING) ?: return
        editor.putUserData(PENDING, null)
        // Each replay re-arms PENDING if it waits on the server; the rest queue behind it.
        while (queued.isNotEmpty()) {
            val next = queued.removeFirst()
            val again = editor.getUserData(PENDING)
            if (again != null) {
                again.addAll(queued)
                again.addFirst(next)
                return
            }
            next()
        }
    }

    private class Request(
        val editor: Editor,
        val file: VirtualFile,
        val server: CompletableFuture<LanguageServerItem?>,
        val lines: List<Int>,
        val stamp: Long,
        val carets: List<CarveListIndent.Selection>,
    )

    /** The request this press makes, or null when the key keeps its default. */
    private fun prepare(editor: Editor): Request? {
        val project = editor.project ?: return null
        if (editor.isViewer || !editor.document.isWritable) return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        if (!isCarve(file)) return null
        if (LookupManager.getActiveLookup(editor) != null) return null
        if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) return null

        val carets = selections(editor)
        val lines = CarveListIndent.selectedListLines(editor.document.text, carets)
        if (lines.isEmpty()) return null

        val manager = LanguageServerManager.getInstance(project)
        if (manager.getServerStatus(SERVER_ID) != ServerStatus.started) return null
        val server = manager.getLanguageServer(SERVER_ID)
        // A started server usually resolves at once; decide the common fallbacks before going async.
        if (server.isDone && !server.isCompletedExceptionally && !canAnswer(server.getNow(null))) return null

        // LSP4IJ holds didChange until the document is committed; commit so the server sees this text.
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        return Request(editor, file, server, lines, editor.document.modificationStamp, carets)
    }

    private fun send(request: Request) {
        val modality = ModalityState.stateForComponent(request.editor.component)
        val answers = request.server.thenCompose { item ->
            // The same URI LSP4IJ sent in didOpen for this file.
            val uri = item?.let { FileUriSupport.toString(request.file, it.clientFeatures) }
            if (item == null || uri == null || !canAnswer(item)) {
                return@thenCompose CompletableFuture.completedFuture(emptyList<List<CarveListIndent.TextEdit>>())
            }
            flushPendingChanges(item, request.file).thenCompose { item.initializedServer }.thenCompose { server ->
                val perLine = request.lines.map { line ->
                    val params = ExecuteCommandParams(
                        CarveListIndent.COMMAND,
                        listOf(CarveListIndent.arguments(uri, line, direction)),
                    )
                    server.workspaceService.executeCommand(params)
                        .thenApply { CarveListIndent.parseEdits(it, uri) }
                }
                CompletableFuture.allOf(*perLine.toTypedArray()).thenApply { perLine.map { it.join() } }
            }
        }
        answers.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete { result, error ->
            if (error != null) thisLogger().warn("carve.listIndent failed", error)
            val edits = CarveListIndent.mergeEdits(result.orEmpty())
            ApplicationManager.getApplication().invokeLater({ finish(request, edits) }, modality)
        }
    }

    private fun finish(request: Request, edits: List<CarveListIndent.TextEdit>) {
        try {
            apply(request, edits)
        } finally {
            drain(request.editor)
        }
    }

    private fun apply(request: Request, edits: List<CarveListIndent.TextEdit>) {
        val editor = request.editor
        if (editor.isDisposed) return
        val project = editor.project ?: return
        val stale = editor.document.modificationStamp != request.stamp || selections(editor) != request.carets
        when (CarveListIndent.outcome(edits, stale)) {
            Outcome.NOTHING -> Unit
            Outcome.DEFAULT_KEY -> CommandProcessor.getInstance().executeCommand(
                project,
                { original.execute(editor, null, DataManager.getInstance().getDataContext(editor.contentComponent)) },
                commandName(),
                null,
                editor.document,
            )
            Outcome.APPLY -> {
                val document = editor.document
                val ranges = CarveListIndent.toOffsets(
                    edits,
                    document::getLineStartOffset,
                    document::getLineEndOffset,
                    document.lineCount,
                    document.textLength,
                ) ?: return
                WriteCommandAction.writeCommandAction(project).withName(commandName()).run<RuntimeException> {
                    for ((start, end, text) in ranges) document.replaceString(start, end, text)
                }
            }
        }
    }

    private fun commandName(): String =
        if (direction == Direction.INDENT) "Indent List Item" else "Outdent List Item"

    companion object {
        private const val SERVER_ID = "carveLanguageServer"
        private const val TIMEOUT_SECONDS = 2L
        private val PENDING = Key.create<ArrayDeque<() -> Unit>>("carve.listIndent.pending")

        /**
         * Sends the didChange LSP4IJ still holds for [file], as its own on-type formatting does
         * before a request; otherwise the command can overtake it and answer for older text.
         * The opened-document API is LSP4IJ-internal, so a release that moves it degrades to
         * the commit in [prepare] instead of failing the key.
         */
        private fun flushPendingChanges(item: LanguageServerItem, file: VirtualFile): CompletableFuture<*> =
            try {
                val uri = FileUriSupport.getFileUri(file, item.clientFeatures)
                item.serverWrapper.getOpenedDocument(uri)?.synchronizer?.flushPendingChanges()
                    ?: CompletableFuture.completedFuture(null)
            } catch (e: LinkageError) {
                thisLogger().debug("LSP4IJ offers no pending-change flush", e)
                CompletableFuture.completedFuture(null)
            }

        /** The server advertises the command. */
        private fun canAnswer(item: LanguageServerItem?): Boolean =
            item != null && CarveListIndent.serverHasCommand(item.serverCapabilities?.executeCommandProvider?.commands)

        private fun isCarve(file: VirtualFile): Boolean =
            file.fileType == CarveFileType || CarveFileType.matches(file.extension)

        private fun selections(editor: Editor): List<CarveListIndent.Selection> =
            editor.caretModel.allCarets.map { caret ->
                val document = editor.document
                fun position(offset: Int): CarveListIndent.Position {
                    val line = document.getLineNumber(offset)
                    return CarveListIndent.Position(line, offset - document.getLineStartOffset(line))
                }
                CarveListIndent.Selection(position(caret.selectionStart), position(caret.selectionEnd))
            }
    }
}
