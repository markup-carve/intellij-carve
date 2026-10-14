package org.markupcarve.carve.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.markupcarve.carve.lists.CarveListIndent.Position
import org.markupcarve.carve.lists.CarveListIndent.TextEdit
import java.util.concurrent.CompletableFuture

/**
 * Types Enter through the real editor action with the server replaced by [FakeBackend].
 * The answers are the shapes carve-lsp sends: one insert at the caret holding the
 * closer (0.1.11 and earlier), or the lead at the caret and the closer as its own edit.
 */
class CarveEnterContinuationHandlerTest : BasePlatformTestCase() {

    private class FakeBackend : CarveOnTypeBackend {
        var unavailable: String? = null
        var answer: (Position) -> List<TextEdit> = { emptyList() }
        val requests = mutableListOf<Position>()

        override fun unavailableReason(project: Project, file: PsiFile): String? = unavailable

        override fun request(project: Project, file: VirtualFile, position: Position, tabSize: Int, insertSpaces: Boolean) =
            CompletableFuture.completedFuture(answer(position).also { requests += position })
    }

    private val backend = FakeBackend()

    override fun setUp() {
        super.setUp()
        CarveEnterContinuationHandler.useBackendForTests(backend)
    }

    override fun tearDown() {
        try {
            CarveEnterContinuationHandler.useBackendForTests(null)
        } finally {
            super.tearDown()
        }
    }

    private fun insert(line: Int, character: Int, text: String) =
        TextEdit(Position(line, character), Position(line, character), text)

    private fun enter(text: String): String {
        myFixture.configureByText("x.crv", text)
        myFixture.type('\n')
        val document = myFixture.editor.document.text
        val caret = myFixture.editor.caretModel.offset
        return document.substring(0, caret) + "<caret>" + document.substring(caret)
    }

    fun `test a closer sent at the caret leaves the caret on the body line`() {
        backend.answer = { listOf(insert(it.line, it.character, "\n:::")) }
        assertEquals("::: note\n<caret>\n:::", enter("::: note<caret>"))
        assertEquals(listOf(Position(1, 0)), backend.requests)
    }

    fun `test a quoted lead goes before the caret and the closer after`() {
        backend.answer = { listOf(insert(it.line, it.character, "> \n> :::")) }
        assertEquals("> ::: note\n> <caret>\n> :::", enter("> ::: note<caret>"))
    }

    fun `test a code fence closer on the next line keeps the caret`() {
        backend.answer = { listOf(insert(it.line + 1, 0, "```\n")) }
        assertEquals("```js\n<caret>\n```\nrest", enter("```js<caret>\nrest"))
    }

    fun `test split lead and closer at the end of the document`() {
        backend.answer = { listOf(insert(it.line, it.character, "> "), insert(it.line, it.character, "\n> :::")) }
        assertEquals("> ::: note\n> <caret>\n> :::", enter("> ::: note<caret>"))
    }

    fun `test a list continuation puts the caret after the marker`() {
        backend.answer = { listOf(TextEdit(Position(it.line, 0), it, "- ")) }
        assertEquals("- a\n- <caret>", enter("- a<caret>"))
    }

    fun `test no answer leaves the plain Enter`() {
        assertEquals("plain\n<caret>", enter("plain<caret>"))
        assertEquals(1, backend.requests.size)
    }

    fun `test an unavailable server sends nothing`() {
        backend.unavailable = "server is stopped"
        assertEquals("::: note\n<caret>", enter("::: note<caret>"))
        assertEquals(emptyList<Position>(), backend.requests)
    }

    fun `test a non-Carve file is left alone`() {
        backend.answer = { listOf(insert(it.line, it.character, "\n:::")) }
        myFixture.configureByText("x.txt", "::: note<caret>")
        myFixture.type('\n')
        assertEquals("::: note\n", myFixture.editor.document.text)
        assertEquals(emptyList<Position>(), backend.requests)
    }
}
