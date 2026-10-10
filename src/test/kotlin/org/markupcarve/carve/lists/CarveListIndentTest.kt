package org.markupcarve.carve.lists

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.lists.CarveListIndent.Direction
import org.markupcarve.carve.lists.CarveListIndent.Outcome
import org.markupcarve.carve.lists.CarveListIndent.Position
import org.markupcarve.carve.lists.CarveListIndent.Selection
import org.markupcarve.carve.lists.CarveListIndent.TextEdit
import java.io.File

class CarveListIndentTest {

    private fun caret(line: Int, character: Int = 0) = Selection(Position(line, character), Position(line, character))
    private fun select(fromLine: Int, fromChar: Int, toLine: Int, toChar: Int) =
        Selection(Position(fromLine, fromChar), Position(toLine, toChar))

    private fun edit(line: Int, character: Int, text: String, endCharacter: Int = character) =
        TextEdit(Position(line, character), Position(line, endCharacter), text)

    @Test
    fun `recognizes list item markers`() {
        for (line in listOf("- a", "* a", "1. a", "1) a", "a. a", "iv. a", "IV) a", ". a", "-", "  - nested", "> - quoted", "- [x] task")) {
            assertTrue(line, CarveListIndent.isListItemLine(line))
        }
    }

    @Test
    fun `a bare marker right after Enter is a list line`() {
        for (line in listOf("- ", "* ", "3. ", "3) ", "b. ", "iv. ", "IV) ", ". ", "-", "3.", "- [ ] ", "- [>] ", "-\t", "  - ", "> - ", "> > 2. ")) {
            assertTrue(line, CarveListIndent.isListItemLine(line))
        }
        assertEquals(listOf(2), CarveListIndent.selectedListLines("1. a\n2. b\n3. ", listOf(caret(2, 3))))
        assertEquals(listOf(1), CarveListIndent.selectedListLines("- a\n- ", listOf(caret(1, 2))))
    }

    @Test
    fun `container leads may precede the marker`() {
        for (line in listOf("[^a]: - a", "[^note]: 1. a", ": - a", "  : - b", ">- a")) {
            assertTrue(line, CarveListIndent.isListItemLine(line))
        }
    }

    @Test
    fun `leaves text, continuations and thematic breaks alone`() {
        for (line in listOf("-a", "1.5 apples", "+ continued", "---", "* * *", "- - -", "plain text", "", "  ", ": term text", "[^a]: text")) {
            assertFalse(line, CarveListIndent.isListItemLine(line))
        }
    }

    @Test
    fun `a single caret on an item line selects that line`() {
        val text = "- a\n- b\n"
        assertEquals(listOf(1), CarveListIndent.selectedListLines(text, listOf(caret(1, 3))))
    }

    @Test
    fun `a caret off a list line keeps the default key`() {
        val text = "intro\n- a\n"
        assertEquals(emptyList<Int>(), CarveListIndent.selectedListLines(text, listOf(caret(0))))
    }

    @Test
    fun `mixed carets keep the default key for all of them`() {
        val text = "- a\nplain\n- b\n"
        assertEquals(emptyList<Int>(), CarveListIndent.selectedListLines(text, listOf(caret(0), caret(1))))
    }

    @Test
    fun `several carets on item lines collect every line once, ascending`() {
        val text = "- a\n- b\n- c\n"
        assertEquals(listOf(1, 2), CarveListIndent.selectedListLines(text, listOf(caret(2), caret(1), caret(2, 1))))
    }

    @Test
    fun `a multi-line selection takes its item lines and skips item content`() {
        val text = "- a\n- b\n  more of b\n- c\n"
        assertEquals(listOf(1, 3), CarveListIndent.selectedListLines(text, listOf(select(1, 0, 3, 2))))
    }

    @Test
    fun `a selection ending at column 0 leaves that line out`() {
        val text = "- a\n- b\n- c\n"
        assertEquals(listOf(0, 1), CarveListIndent.selectedListLines(text, listOf(select(0, 0, 2, 0))))
    }

    @Test
    fun `a backwards selection reads the same`() {
        val text = "- a\n- b\n- c\n"
        assertEquals(listOf(0, 1), CarveListIndent.selectedListLines(text, listOf(select(2, 0, 0, 1))))
    }

    @Test
    fun `list-looking lines inside a code fence are code`() {
        val text = "```\n- a\n```\n- b\n"
        assertEquals(emptyList<Int>(), CarveListIndent.selectedListLines(text, listOf(caret(1))))
        assertEquals(listOf(3), CarveListIndent.selectedListLines(text, listOf(caret(3))))
    }

    @Test
    fun `an inline triple-backtick span opens no fence`() {
        val mask = CarveListIndent.codeFenceMask(listOf("```code```", "- a"))
        assertFalse(mask[1])
    }

    @Test
    fun `CRLF text splits into the same lines`() {
        assertEquals(listOf(1), CarveListIndent.selectedListLines("- a\r\n- b\r\n", listOf(caret(1))))
    }

    @Test
    fun `no carets selects nothing`() {
        assertEquals(emptyList<Int>(), CarveListIndent.selectedListLines("- a", emptyList()))
    }

    @Test
    fun `arguments carry uri, line and direction`() {
        val args = CarveListIndent.arguments("file:///a.crv", 3, Direction.OUTDENT)
        assertEquals("file:///a.crv", args.get("uri").asString)
        assertEquals(3, args.get("line").asInt)
        assertEquals("outdent", args.get("direction").asString)
    }

    private val uri = "file:///home/me/a%20b.crv"

    @Test
    fun `a TextEdit array is the answer`() {
        val json = JsonParser.parseString(
            """[{"range":{"start":{"line":1,"character":0},"end":{"line":1,"character":0}},"newText":"  "}]""",
        )
        assertEquals(listOf(edit(1, 0, "  ")), CarveListIndent.parseEdits(json, uri))
    }

    @Test
    fun `null and non-edit answers mean no edit`() {
        assertEquals(emptyList<TextEdit>(), CarveListIndent.parseEdits(null, uri))
        assertEquals(emptyList<TextEdit>(), CarveListIndent.parseEdits(JsonParser.parseString("null"), uri))
        assertEquals(emptyList<TextEdit>(), CarveListIndent.parseEdits(JsonParser.parseString("true"), uri))
        assertEquals(emptyList<TextEdit>(), CarveListIndent.parseEdits(JsonParser.parseString("""[{"newText":"x"}]"""), uri))
    }

    @Test
    fun `a WorkspaceEdit contributes only this document's edits`() {
        val json = JsonParser.parseString(
            """{"changes":{
                "file:///home/me/a b.crv":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":2}},"newText":""}],
                "file:///home/me/other.crv":[{"range":{"start":{"line":5,"character":0},"end":{"line":5,"character":0}},"newText":"x"}]
            }}""",
        )
        assertEquals(listOf(edit(0, 0, "", endCharacter = 2)), CarveListIndent.parseEdits(json, uri))
    }

    @Test
    fun `documentChanges are read per document`() {
        val json = JsonParser.parseString(
            """{"documentChanges":[
                {"textDocument":{"uri":"file:///home/me/a%20b.crv","version":1},
                 "edits":[{"range":{"start":{"line":2,"character":0},"end":{"line":2,"character":0}},"newText":"  "}]},
                {"textDocument":{"uri":"file:///elsewhere.crv"},"edits":[]}
            ]}""",
        )
        assertEquals(listOf(edit(2, 0, "  ")), CarveListIndent.parseEdits(json, uri))
    }

    @Test
    fun `a Windows drive letter compares case-insensitively`() {
        val json = JsonParser.parseString(
            """{"changes":{"file:///C:/a.crv":[{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":0}},"newText":"  "}]}}""",
        )
        assertEquals(1, CarveListIndent.parseEdits(json, "file:///c%3A/a.crv").size)
    }

    @Test
    fun `an answer that overlaps an earlier one is dropped whole`() {
        val parent = listOf(edit(1, 0, "  "), edit(2, 0, "  "))
        val child = listOf(edit(2, 0, "  "))
        val other = listOf(edit(5, 0, "  "))
        assertEquals(parent + other, CarveListIndent.mergeEdits(listOf(parent, child, other)))
    }

    @Test
    fun `the command must be advertised`() {
        assertTrue(CarveListIndent.serverHasCommand(listOf("carve.other", "carve.listIndent")))
        assertFalse(CarveListIndent.serverHasCommand(listOf("carve.other")))
        assertFalse(CarveListIndent.serverHasCommand(null))
    }

    @Test
    fun `fallback decision`() {
        assertEquals(Outcome.DEFAULT_KEY, CarveListIndent.outcome(emptyList(), stale = false))
        assertEquals(Outcome.APPLY, CarveListIndent.outcome(listOf(edit(0, 0, "  ")), stale = false))
        assertEquals(Outcome.NOTHING, CarveListIndent.outcome(listOf(edit(0, 0, "  ")), stale = true))
        assertEquals(Outcome.NOTHING, CarveListIndent.outcome(emptyList(), stale = true))
    }

    @Test
    fun `edits map to offsets, last first, clamped to the line end`() {
        val text = "- a\n- b\n"
        val starts = listOf(0, 4, 8)
        val ends = listOf(3, 7, 8)
        val ranges = CarveListIndent.toOffsets(
            listOf(edit(0, 0, "x"), edit(1, 0, "  "), edit(1, 1, "", endCharacter = 99)),
            starts::get,
            ends::get,
            starts.size,
            text.length,
        )
        assertEquals(listOf(Triple(5, 7, ""), Triple(4, 4, "  "), Triple(0, 0, "x")), ranges)
    }

    @Test
    fun `an inverted or negative range is refused`() {
        val starts = listOf(0)
        val ends = listOf(3)
        assertNull(CarveListIndent.toOffsets(listOf(edit(0, -1, "")), starts::get, ends::get, 1, 3))
        assertNull(
            CarveListIndent.toOffsets(
                listOf(TextEdit(Position(0, 2), Position(0, 1), "")),
                starts::get,
                ends::get,
                1,
                3,
            ),
        )
    }

    @Test
    fun `the handler is wired only where LSP4IJ is present`() {
        val metaInf = File("src/main/resources/META-INF")
        val strip = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        val plugin = File(metaInf, "plugin.xml").readText().replace(strip, "")
        val lsp = File(metaInf, "carve-lsp.xml").readText().replace(strip, "")
        assertFalse(plugin.contains("CarveListIndentHandler"))
        assertTrue(lsp.contains("""action="EditorTab""""))
        assertTrue(lsp.contains("""action="EditorUnindentSelection""""))
        val pure = File("src/main/kotlin/org/markupcarve/carve/lists/CarveListIndent.kt").readText()
        assertFalse("the pure half must load without LSP4IJ", pure.contains("lsp4ij") || pure.contains("lsp4j"))
    }
}
