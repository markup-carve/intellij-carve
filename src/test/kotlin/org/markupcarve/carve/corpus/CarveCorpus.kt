package org.markupcarve.carve.corpus

import com.google.gson.JsonParser
import java.io.File

/** Option-free render cases from the Carve 0.2 conformance corpus. */
object CarveCorpus {
    private const val CONFORMANCE_REL = "spec/spec/0.2/conformance"

    val directory: File? by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var found: File? = null
        while (dir != null && found == null) {
            val candidate = File(dir, CONFORMANCE_REL)
            if (candidate.isDirectory) found = candidate
            dir = dir.parentFile
        }
        found
    }

    data class Example(val id: String, val input: String, val html: String)

    /** The vendored subset is checked byte for byte against the pinned source. */
    fun examples(): List<Example> {
        val source = File(directory ?: return emptyList(), "examples.json")
        if (!source.isFile) return emptyList()
        val stream = javaClass.getResourceAsStream("/conformance-0.2.json") ?: return emptyList()
        return JsonParser.parseString(stream.bufferedReader().use { it.readText() })
            .asJsonObject.getAsJsonArray("examples")
            .map { item ->
                val row = item.asJsonObject
                Example(row.get("id").asString, row.get("input").asString, row.get("html").asString)
            }
    }

    val MISSING_MESSAGE: String =
        "Carve 0.2 conformance not found at $CONFORMANCE_REL. Check out the spec submodule " +
            "with `git submodule update --init spec`."
}
