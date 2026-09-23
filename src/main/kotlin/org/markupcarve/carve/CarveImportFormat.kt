package org.markupcarve.carve

/** Source formats the bundled engine can import into Carve. */
enum class CarveImportFormat(val label: String, val jsFunction: String, val extensions: Set<String>) {
    MARKDOWN("Markdown", "markdownToCarve", setOf("md", "markdown")),
    HTML("HTML", "htmlToCarve", setOf("html", "htm"));

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
