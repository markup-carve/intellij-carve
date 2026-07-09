package org.markupcarve.carve

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object CarveFileType : LanguageFileType(CarveLanguage) {
    override fun getName(): String = "Carve"
    override fun getDescription(): String = "Carve markup file"
    override fun getDefaultExtension(): String = "crv"
    override fun getIcon(): Icon = CarveIcons.FILE

    /** Recognized Carve file extensions (lowercase, no dot). */
    val EXTENSIONS: Set<String> = setOf("crv")

    /** True when the given file extension belongs to Carve. */
    fun matches(extension: String?): Boolean =
        extension != null && extension.lowercase() in EXTENSIONS
}
