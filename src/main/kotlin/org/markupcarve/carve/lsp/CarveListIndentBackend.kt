package org.markupcarve.carve.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport
import org.eclipse.lsp4j.ExecuteCommandParams
import org.markupcarve.carve.lists.CarveListIndent
import org.markupcarve.carve.lists.CarveListIndent.Direction
import org.markupcarve.carve.lists.CarveListIndent.TextEdit
import java.util.concurrent.CompletableFuture

/** How [CarveListIndentHandler] reaches the server; a seam so tests can stand in for LSP4IJ. */
interface CarveListIndentBackend {

    /** Null when a request may go out, else why the key keeps its default. Called on the EDT; must not block. */
    fun unavailableReason(project: Project): String?

    /** The per-line answers, or [Answer.skipped] when the server turned out unable to answer. */
    fun request(project: Project, file: VirtualFile, lines: List<Int>, direction: Direction): CompletableFuture<Answer>

    data class Answer(val edits: List<List<TextEdit>>, val skipped: String? = null)
}

/** The real backend: carve-lsp through LSP4IJ. */
object Lsp4ijListIndentBackend : CarveListIndentBackend {

    /** The server id carve-lsp.xml registers. */
    const val SERVER_ID = "carveLanguageServer"

    private val LOG = Logger.getInstance(Lsp4ijListIndentBackend::class.java)

    override fun unavailableReason(project: Project): String? {
        val manager = LanguageServerManager.getInstance(project)
        val status = manager.getServerStatus(SERVER_ID)
        if (status != ServerStatus.started) return "server $SERVER_ID is $status, not started"
        val server = manager.getLanguageServer(SERVER_ID)
        // A started server usually resolves at once; anything else is decided in request().
        if (!server.isDone || server.isCompletedExceptionally) return null
        return reasonItCannotAnswer(server.getNow(null))
    }

    override fun request(
        project: Project,
        file: VirtualFile,
        lines: List<Int>,
        direction: Direction,
    ): CompletableFuture<CarveListIndentBackend.Answer> =
        LanguageServerManager.getInstance(project).getLanguageServer(SERVER_ID).thenCompose { item ->
            val reason = reasonItCannotAnswer(item)
            // The same URI LSP4IJ sent in didOpen for this file.
            val uri = item?.let { FileUriSupport.toString(file, it.clientFeatures) }
            if (item == null || reason != null || uri == null) {
                return@thenCompose CompletableFuture.completedFuture(
                    CarveListIndentBackend.Answer(emptyList(), reason ?: "no URI for $file"),
                )
            }
            flushPendingChanges(item, file).thenCompose { item.initializedServer }.thenCompose { server ->
                val perLine = lines.map { line ->
                    val params = ExecuteCommandParams(
                        CarveListIndent.COMMAND,
                        listOf(CarveListIndent.arguments(uri, line, direction)),
                    )
                    server.workspaceService.executeCommand(params)
                        .thenApply { CarveListIndent.parseEdits(it, uri) }
                }
                CompletableFuture.allOf(*perLine.toTypedArray())
                    .thenApply { CarveListIndentBackend.Answer(perLine.map { it.join() }) }
            }
        }

    private fun reasonItCannotAnswer(item: LanguageServerItem?): String? {
        if (item == null) return "LSP4IJ returned no $SERVER_ID server"
        val commands = item.serverCapabilities?.executeCommandProvider?.commands
        if (!CarveListIndent.serverHasCommand(commands)) {
            return "server does not advertise ${CarveListIndent.COMMAND} (commands: $commands)"
        }
        return null
    }

    /**
     * Sends the didChange LSP4IJ still holds for [file], as its own on-type formatting does
     * before a request; otherwise the command can overtake it and answer for older text.
     * The opened-document API is LSP4IJ-internal, so a release that moves it degrades to
     * the document commit the handler does instead of failing the key.
     */
    private fun flushPendingChanges(item: LanguageServerItem, file: VirtualFile): CompletableFuture<*> =
        try {
            val uri = FileUriSupport.getFileUri(file, item.clientFeatures)
            item.serverWrapper.getOpenedDocument(uri)?.synchronizer?.flushPendingChanges()
                ?: CompletableFuture.completedFuture(null)
        } catch (e: LinkageError) {
            LOG.debug("LSP4IJ offers no pending-change flush", e)
            CompletableFuture.completedFuture(null)
        }
}
