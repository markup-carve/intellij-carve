package org.markupcarve.carve.lsp

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.markupcarve.carve.lists.CarveListIndent.Direction
import org.markupcarve.carve.lists.CarveListIndent.Position
import org.markupcarve.carve.lists.CarveListIndent.TextEdit
import java.util.concurrent.CompletableFuture

/**
 * Drives the registered Tab / Shift+Tab handlers through the real editor actions, with
 * the server replaced by [FakeBackend]. What this cannot show: LSP4IJ itself, the node
 * server, and the didChange flush.
 */
class CarveListIndentHandlerTest : BasePlatformTestCase() {

    private class FakeBackend(
        var unavailable: String? = null,
        var answer: (List<Int>) -> List<List<TextEdit>> = { emptyList() },
    ) : CarveListIndentBackend {
        val requests = mutableListOf<Pair<List<Int>, Direction>>()

        override fun unavailableReason(project: Project): String? = unavailable

        override fun request(
            project: Project,
            file: VirtualFile,
            lines: List<Int>,
            direction: Direction,
        ): CompletableFuture<CarveListIndentBackend.Answer> {
            requests += lines to direction
            return CompletableFuture.completedFuture(CarveListIndentBackend.Answer(answer(lines)))
        }
    }

    private val backend = FakeBackend()

    override fun setUp() {
        super.setUp()
        CarveListIndentHandler.useBackendForTests(backend)
    }

    override fun tearDown() {
        try {
            CarveListIndentHandler.useBackendForTests(null)
        } finally {
            super.tearDown()
        }
    }

    private fun insert(line: Int, text: String) = TextEdit(Position(line, 0), Position(line, 0), text)

    private fun press(action: String) {
        myFixture.performEditorAction(action)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    fun `test Tab on a bare ordered marker asks the server and applies its edit`() {
        backend.answer = { listOf(listOf(insert(2, "   "))) }
        myFixture.configureByText("x.crv", "1. a\n2. b\n3. <caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(listOf(listOf(2) to Direction.INDENT), backend.requests)
        assertEquals("1. a\n2. b\n   3. ", myFixture.editor.document.text)
    }

    fun `test Tab on a bare bullet marker asks the server`() {
        backend.answer = { listOf(listOf(insert(1, "  "))) }
        myFixture.configureByText("x.crv", "- a\n- <caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(listOf(listOf(1) to Direction.INDENT), backend.requests)
        assertEquals("- a\n  - ", myFixture.editor.document.text)
    }

    fun `test Shift+Tab sends an outdent`() {
        backend.answer = { listOf(listOf(TextEdit(Position(1, 0), Position(1, 2), ""))) }
        myFixture.configureByText("x.crv", "- a\n  - b<caret>")
        press(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
        assertEquals(listOf(listOf(1) to Direction.OUTDENT), backend.requests)
        assertEquals("- a\n- b", myFixture.editor.document.text)
    }

    fun `test a continuation line is not a list line and keeps the default Tab`() {
        myFixture.configureByText("x.crv", "- a\n  <caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(emptyList<Pair<List<Int>, Direction>>(), backend.requests)
        assertTrue(myFixture.editor.document.text.length > "- a\n  ".length)
    }

    fun `test a null answer falls back to the default Tab`() {
        myFixture.configureByText("x.crv", "- a<caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(listOf(listOf(0) to Direction.INDENT), backend.requests)
        assertTrue(myFixture.editor.document.text.length > "- a".length)
    }

    fun `test an unavailable server keeps the default Tab without a request`() {
        backend.unavailable = "server is stopped"
        myFixture.configureByText("x.crv", "- a\n- b<caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(emptyList<Pair<List<Int>, Direction>>(), backend.requests)
        assertTrue(myFixture.editor.document.text.length > "- a\n- b".length)
    }

    fun `test two carets on list lines make one request and one edit`() {
        backend.answer = { lines -> lines.map { listOf(insert(it, "  ")) } }
        myFixture.configureByText("x.crv", "- a\n- b<caret>\n- c<caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(listOf(listOf(1, 2) to Direction.INDENT), backend.requests)
        assertEquals("- a\n  - b\n  - c", myFixture.editor.document.text)
    }

    fun `test a non-Carve file is left to the default Tab`() {
        myFixture.configureByText("x.txt", "- a\n- <caret>")
        press(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(emptyList<Pair<List<Int>, Direction>>(), backend.requests)
    }
}
