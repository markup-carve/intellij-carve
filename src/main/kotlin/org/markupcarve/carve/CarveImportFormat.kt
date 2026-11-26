package org.markupcarve.carve

/** Source formats the bundled engine can import into Carve. */
enum class CarveImportFormat(val label: String, val from: String, val extensions: Set<String>) {
    // Markdown files are read as GFM, the dialect the 0.1 Markdown importer took.
    MARKDOWN("Markdown", "gfm", setOf("md", "markdown")),
    HTML("HTML", "html", setOf("html", "htm"));

    companion object {
        private const val TARGET_EXTENSION = "crv"

        fun forExtension(extension: String?): CarveImportFormat? {
            val ext = extension?.lowercase() ?: return null
            return entries.firstOrNull { ext in it.extensions }
        }

        /** Sibling `.crv` name for an importable file, or null when the file is not importable. */
        fun targetName(fileName: String): String? {
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0) return null
            forExtension(fileName.substring(dot + 1)) ?: return null
            return fileName.substring(0, dot) + ".$TARGET_EXTENSION"
        }
    }
}
