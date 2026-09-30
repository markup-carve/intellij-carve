package org.markupcarve.carve.corpus

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarveLineInlineGenerationTest {
    private val prefix = "line-inline-"

    @Suppress("UNCHECKED_CAST")
    private val repository = (JsonSlurper().parse(File("src/main/resources/textmate/carve.tmLanguage.json"))
        as Map<String, Any?>)["repository"] as Map<String, Any?>

    private fun normalize(node: Any?, generated: Boolean): Any? = when (node) {
        is Map<*, *> -> node.filterKeys { it != "comment" }.mapValues { (key, value) ->
            when {
                generated && key == "include" && value is String -> value.replace("#$prefix", "#")
                generated && key == "end" && node.containsKey("begin") -> {
                    val end = value as String
                    assertTrue(end, end.startsWith("(?:") && end.endsWith(")|(?=$)"))
                    end.removePrefix("(?:").removeSuffix(")|(?=$)")
                }
                else -> normalize(value, generated)
            }
        }
        is List<*> -> node.map { normalize(it, generated) }
        else -> node
    }

    @Test
    fun generatedRulesPreserveTheSharedRulesAndBoundEveryRegion() {
        val generated = repository.filterKeys { it.startsWith(prefix) }
        assertTrue(generated.isNotEmpty())
        generated.forEach { (name, node) ->
            assertEquals(name, JsonOutput.toJson(normalize(repository.getValue(name.removePrefix(prefix)), false)),
                JsonOutput.toJson(normalize(node, true)))
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun headingRootIncludesAllSupportedInlineConstructs() {
        val expected = listOf("raw-inline", "inline-literal", "inline-code", "links", "images", "footnotes",
            "citations", "critic-markup", "emphasis", "heading-span-attributes", "inline-comment", "math",
            "cross-reference", "autolink", "extension-inline", "include-directive", "mentions-tags",
            "smart-typography", "hard-break", "trailing-comment")
        val root = repository.getValue("heading-inline") as Map<String, Any?>
        val patterns = root.getValue("patterns") as List<Map<String, String>>
        assertEquals(expected, patterns.map { it.getValue("include").removePrefix("#").removePrefix(prefix) })
    }

    @Test
    fun headingRulesReachOnlyBoundedInlineRegions() {
        val visited = mutableSetOf<String>()
        fun visit(node: Any?) {
            when (node) {
                is Map<*, *> -> {
                    val include = node["include"] as? String
                    if (include?.startsWith("#") == true && visited.add(include.drop(1))) {
                        val name = include.drop(1)
                        val target = repository.getValue(name)
                        fun hasRegion(value: Any?): Boolean = when (value) {
                            is Map<*, *> -> value.containsKey("begin") || value.values.any { hasRegion(it) }
                            is List<*> -> value.any { hasRegion(it) }
                            else -> false
                        }
                        assertTrue("$name introduces an unbounded heading region", !hasRegion(target) || name.startsWith(prefix))
                        visit(target)
                    }
                    node.values.forEach { visit(it) }
                }
                is List<*> -> node.forEach { visit(it) }
            }
        }
        visit(repository.getValue("heading-inline"))
    }

}
