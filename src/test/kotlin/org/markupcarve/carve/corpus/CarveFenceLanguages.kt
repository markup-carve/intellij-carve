package org.markupcarve.carve.corpus

/**
 * The generated per-language fence rules, read back off the committed grammar: language id
 * to the first info-string word that reaches it.
 *
 * Read rather than listed, so a test over this family measures the grammar that ships
 * instead of a second copy of the language table that could agree with nothing. The
 * generator writes one rule per line, and every generated rule carries both an inline
 * case-insensitive word list and the `meta.embedded.block.<id>` scope, which no
 * hand-written rule in the grammar does.
 */
object CarveFenceLanguages {

    private val LINE = Regex("""\(\?i:([^)|]+)[^)]*\)\).*meta\.embedded\.block\.([A-Za-z]+)""")

    /** Language id to its first info-string word, in the grammar's own order. */
    fun read(): List<Pair<String, String>> {
        val text = CarveFenceLanguages::class.java.getResourceAsStream("/textmate/carve.tmLanguage.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Carve grammar not found on test classpath")
        return text.lineSequence()
            .mapNotNull { LINE.find(it) }
            .map { it.groupValues[2] to it.groupValues[1].replace("\\\\", "\\") }
            .distinctBy { it.first }
            .toList()
    }
}
