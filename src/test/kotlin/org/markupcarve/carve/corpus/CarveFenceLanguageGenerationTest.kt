package org.markupcarve.carve.corpus

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The generated per-language fence rules are copies of a hand-written generic fence rule
 * (markup-carve/intellij-carve#207).
 *
 * `tools/generate-fence-languages.mjs` writes 114 rules from three hand-written ones, so a
 * hand edit to a generic rule - a new fence width, another title spelling, a different
 * closer - leaves 38 stale copies behind it that still match first. The generator's own
 * `--check` mode says that too, but it needs node and CI runs `gradlew test`, so the
 * invariant is asserted here against the committed grammar instead: every generated rule
 * must BE its generic rule, with the language capture narrowed and an embedded body added.
 *
 * Asserting the relationship rather than re-running the generator is also what keeps this
 * test honest about a generator bug: a second implementation of the same code would agree
 * with the first one's mistakes.
 */
class CarveFenceLanguageGenerationTest {

    private val grammarFile = File("src/main/resources/textmate/carve.tmLanguage.json")

    /** generated rule name to the hand-written rule its copies must match. */
    private val variants =
        mapOf(
            "fenced-code-languages" to "code-blocks",
            "fenced-code-languages-on-a-marker-line" to "code-fence-on-marker-line",
            "fenced-code-languages-at-a-body-column" to "code-fence-at-body-column",
        )

    @Suppress("UNCHECKED_CAST")
    private val repository: Map<String, Any?> by lazy {
        val root = JsonSlurper().parse(grammarFile, "UTF-8") as Map<String, Any?>
        root["repository"] as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun rules(name: String): List<Map<String, Any?>> =
        (repository.getValue(name) as Map<String, Any?>)["patterns"] as List<Map<String, Any?>>

    /** The hand-written rule inside a wrapper: the one entry that is not an include. */
    private fun genericRule(wrapper: String): Map<String, Any?> =
        rules(wrapper).single { it.containsKey("begin") }

    private val languageCapture = Regex("""\(\(\?i:[^)]+\)\)""")

    @Test
    fun `every generic fence rule includes its generated family first`() {
        variants.forEach { (generated, wrapper) ->
            val first = rules(wrapper).first()
            assertEquals(
                "$wrapper does not consult $generated before its own rule",
                "#$generated",
                first["include"],
            )
        }
    }

    @Test
    fun `a generated rule is its generic rule with the language capture narrowed`() {
        variants.forEach { (generated, wrapper) ->
            val generic = genericRule(wrapper).filterKeys { it != "comment" }
            val genericBegin = generic.getValue("begin") as String
            rules(generated).forEach { rule ->
                val begin = rule.getValue("begin") as String
                val words = languageCapture.find(begin)
                assertTrue("$generated: a rule carries no narrowed language capture: $begin", words != null)
                val placeholder = capturePlaceholder(genericBegin)
                assertEquals(
                    "$generated: a rule's begin is not $wrapper's begin with the language capture narrowed",
                    genericBegin,
                    languageCapture.replace(begin) { placeholder },
                )
                // Everything except the begin and the embedded body is copied verbatim, so
                // a change to the generic rule's captures or closer cannot be left behind.
                val copied = rule.filterKeys { it != "begin" && it != "patterns" && it != "contentName" }
                val expected = generic.filterKeys { it != "begin" && it != "patterns" && it != "contentName" }
                assertEquals(
                    "$generated: a rule diverges from $wrapper outside the language capture",
                    JsonOutput.toJson(expected),
                    JsonOutput.toJson(copied),
                )
            }
        }
    }

    /** The generic rule's own optional info-string capture, in either of its two spellings. */
    private fun capturePlaceholder(genericBegin: String): String =
        listOf("""([^`~\s\["]+)?""", """([^`~\s\[\"]+)?""")
            .firstOrNull { genericBegin.contains(it) }
            ?: error("the generic fence rule has no info-string capture: $genericBegin")

    @Test
    fun `only the column-0 family guards its body with a while region`() {
        // A `while` guard cannot see the begin rule's captures in the IDE's engine, so the
        // two container families cannot express their container boundary in one and include
        // the language directly instead; their generic rule's own `end` closes the block.
        // The column-0 family has no capture in its boundary, so it keeps the guard, which
        // is what stops an unterminated construct in the embedded language from running
        // past the closer.
        rules("fenced-code-languages").forEach { rule ->
            val json = JsonOutput.toJson(rule)
            val isCarve = json.contains("meta.embedded.block.carve\"")
            assertEquals(
                "fenced-code-languages: a ${if (isCarve) "carve" else "language"} body has the wrong body shape: $json",
                !isCarve,
                json.contains("\"while\""),
            )
        }
        listOf("fenced-code-languages-on-a-marker-line", "fenced-code-languages-at-a-body-column").forEach { family ->
            rules(family).forEach { rule ->
                assertTrue(
                    "$family: a rule carries a while guard, whose \\1 the engine does not resolve",
                    !JsonOutput.toJson(rule).contains("\"while\""),
                )
            }
        }
    }

    @Test
    fun `a container body keeps the raw scope its generic rule gives it`() {
        // The embedded scope is added to the generic rule's contentName rather than
        // replacing it: a fence body inside a list item is still raw code.
        val generic = genericRule("code-fence-on-marker-line")["contentName"] as String
        rules("fenced-code-languages-on-a-marker-line").forEach { rule ->
            val contentName = rule["contentName"] as String
            assertTrue(
                "a marker-line rule dropped the raw scope: $contentName",
                contentName.startsWith("$generic meta.embedded.block."),
            )
        }
    }

    @Test
    fun `the three families cover the same languages`() {
        val ids = variants.keys.map { generated ->
            rules(generated).map { JsonOutput.toJson(it).substringAfter("meta.embedded.block.").substringBefore('"') }
        }
        assertEquals("the families are not all 38 languages", listOf(38, 38, 38), ids.map { it.size })
        assertEquals("the families cover different languages", ids[0], ids[1])
        assertEquals("the families cover different languages", ids[0], ids[2])
        assertEquals("a language is generated twice", ids[0].size, ids[0].toSet().size)
    }
}
