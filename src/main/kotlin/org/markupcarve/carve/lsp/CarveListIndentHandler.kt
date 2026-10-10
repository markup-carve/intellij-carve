package org.markupcarve.carve.lsp

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.TestOnly
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.lists.CarveListIndent
import org.markupcarve.carve.lists.CarveListIndent.Direction
import org.markupcarve.carve.lists.CarveListIndent.Outcome
import java.util.concurrent.TimeUnit

/**
 * Tab / Shift+Tab on Carve list items, through carve-lsp's `carve.listIndent`.
 *
 * Registered only in carve-lsp.xml, which loads only with LSP4IJ. Every case the
 * server cannot answer goes to the wrapped handler synchronously; only a press on
 * list lines with a running server that advertises the command waits for the
 * server, off the EDT. Each fallback is logged at debug level, and at info level
 * with `-Dcarve.listIndent.debug=true`.
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
        // A key press arrives with the current caret from the data context, never null. An
        // outer for-each-caret handler calls once per caret; the first call decides for all.
        val carets = editor.caretModel.allCarets
        if (caret != null && carets.size > 1) {
            val batch = editor.getUserData(BATCH)
            if (batch != null && batch.rest.remove(caret)) {
                if (batch.rest.isEmpty()) editor.putUserData(BATCH, null)
                if (!batch.handled) original.execute(editor, caret, dataContext)
                return
            }
            val handled = handlePress(editor)
            editor.putUserData(BATCH, Batch(carets.filter { it != caret }.toMutableSet(), handled))
            if (!handled) original.execute(editor, caret, dataContext)
            return
        }
        if (!handlePress(editor)) original.execute(editor, caret, dataContext)
    }

    private class Batch(val rest: MutableSet<Caret>, val handled: Boolean)

    /** Whether this press is the handler's (queued or sent); false leaves it to the default key. */
    private fun handlePress(editor: Editor): Boolean {
        // A press while an answer is pending waits its turn, so held or fast Tabs all land.
        val pending = editor.getUserData(PENDING)
        if (pending != null) {
            log("queued behind a pending answer")
            pending.add { press(editor) }
            return true
        }
        val request = prepare(editor) ?: return false
        editor.putUserData(PENDING, ArrayDeque())
        return try {
            send(request)
            true
        } catch (e: RuntimeException) {
            LOG.warn("carve.listIndent could not be sent", e)
            editor.putUserData(PENDING, null)
            false
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
        val lines: List<Int>,
        val stamp: Long,
        val carets: List<CarveListIndent.Selection>,
    )

    /** The request this press makes, or null when the key keeps its default. */
    private fun prepare(editor: Editor): Request? {
        val project = editor.project ?: return fallback("editor has no project")
        if (editor.isViewer || !editor.document.isWritable) return fallback("editor is read-only")
        val file = FileDocumentManager.getInstance().getFile(editor.document)
            ?: return fallback("editor has no file")
        if (!isCarve(file)) return fallback("not a Carve file: ${file.name}")
        if (LookupManager.getActiveLookup(editor) != null) return fallback("completion popup is open")
        if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
            return fallback("live template is active")
        }

        val carets = selections(editor)
        val lines = CarveListIndent.selectedListLines(editor.document.text, carets)
        if (lines.isEmpty()) return fallback("a caret is not on a list line (carets: $carets)")

        backend().unavailableReason(project)?.let { return fallback(it) }

        // LSP4IJ holds didChange until the document is committed; commit so it can go out.
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        log("requesting ${CarveListIndent.COMMAND} ${direction.wire} for lines $lines")
        return Request(editor, file, lines, editor.document.modificationStamp, carets)
    }

    private fun send(request: Request) {
        val project = request.editor.project ?: return
        val modality = ModalityState.stateForComponent(request.editor.component)
        backend().request(project, request.file, request.lines, direction)
            .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete { answer, error ->
                when {
                    error != null -> fallbackReason("request failed or timed out: $error")
                    answer.skipped != null -> fallbackReason(answer.skipped)
                }
                val edits = CarveListIndent.mergeEdits(answer?.edits.orEmpty())
                ApplicationManager.getApplication().invokeLater({ finish(request, edits) }, modality)
            }
    }

    private fun finish(request: Request, edits: List<CarveListIndent.TextEdit>) {
        var outcome: Outcome? = null
        try {
            outcome = apply(request, edits)
        } finally {
            // Presses queued behind a stale answer were made for a state that is gone too.
            if (outcome == Outcome.NOTHING) {
                request.editor.getUserData(PENDING)?.let { if (it.isNotEmpty()) log("dropping ${it.size} queued press(es)") }
                request.editor.putUserData(PENDING, null)
            } else {
                drain(request.editor)
            }
        }
    }

    private fun apply(request: Request, edits: List<CarveListIndent.TextEdit>): Outcome {
        val editor = request.editor
        if (editor.isDisposed) return Outcome.NOTHING
        val project = editor.project ?: return Outcome.NOTHING
        val stale = editor.document.modificationStamp != request.stamp || selections(editor) != request.carets
        val outcome = CarveListIndent.outcome(edits, stale)
        when (outcome) {
            Outcome.NOTHING -> fallbackReason("answer is stale: the document or carets changed meanwhile")
            Outcome.DEFAULT_KEY -> {
                fallbackReason("server answered with no edit for lines ${request.lines}")
                CommandProcessor.getInstance().executeCommand(
                    project,
                    { original.execute(editor, null, DataManager.getInstance().getDataContext(editor.contentComponent)) },
                    commandName(),
                    null,
                    editor.document,
                )
            }
            Outcome.APPLY -> {
                val document = editor.document
                val ranges = CarveListIndent.toOffsets(
                    edits,
                    document::getLineStartOffset,
                    document::getLineEndOffset,
                    document.lineCount,
                    document.textLength,
                ) ?: run {
                    fallbackReason("answer has an invalid range: $edits")
                    return Outcome.NOTHING
                }
                log("applying ${ranges.size} edit(s)")
                WriteCommandAction.writeCommandAction(project).withName(commandName()).run<RuntimeException> {
                    for ((start, end, text) in ranges) document.replaceString(start, end, text)
                }
            }
        }
        return outcome
    }

    private fun commandName(): String =
        if (direction == Direction.INDENT) "Indent List Item" else "Outdent List Item"

    private fun fallback(reason: String): Request? {
        fallbackReason(reason)
        return null
    }

    private fun fallbackReason(reason: String) = log("default ${direction.wire} key: $reason")

    companion object {
        private val LOG = Logger.getInstance(CarveListIndentHandler::class.java)
        private const val TIMEOUT_SECONDS = 2L
        private val PENDING = Key.create<ArrayDeque<() -> Unit>>("carve.listIndent.pending")
        private val BATCH = Key.create<Batch>("carve.listIndent.batch")

        @Volatile
        private var backendOverride: CarveListIndentBackend? = null

        private fun backend(): CarveListIndentBackend = backendOverride ?: Lsp4ijListIndentBackend

        @TestOnly
        fun useBackendForTests(backend: CarveListIndentBackend?) {
            backendOverride = backend
        }

        private fun log(message: String) {
            if (java.lang.Boolean.getBoolean("carve.listIndent.debug")) LOG.info(message) else LOG.debug(message)
        }

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
