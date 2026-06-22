package org.markupcarve.carve

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider.PluginBundle
import java.nio.file.Files
import java.nio.file.Path

/**
 * Supplies the Carve TextMate bundle (grammar + language configuration) to the
 * IDE's bundled TextMate engine, which drives editor syntax highlighting.
 *
 * Plugin resources live individually inside the jar, so the bundle files are
 * extracted once to a versioned directory under the IDE system path and that
 * directory is handed to TextMate. Bump [BUNDLE_VERSION] whenever a bundled
 * file changes so the cached copy is refreshed.
 */
class CarveTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<PluginBundle> {
        val dir = extractBundle() ?: return emptyList()
        return listOf(PluginBundle("carve", dir))
    }

    private fun extractBundle(): Path? {
        val target = Path.of(PathManager.getSystemPath(), "carve-textmate-bundle", BUNDLE_VERSION)
        for (rel in BUNDLE_FILES) {
            val out = target.resolve(rel)
            if (Files.exists(out)) continue
            val resource = javaClass.classLoader.getResourceAsStream("textmate/$rel") ?: return null
            Files.createDirectories(out.parent)
            resource.use { input -> Files.copy(input, out) }
        }
        return target
    }

    private companion object {
        const val BUNDLE_VERSION = "0.2.0"
        val BUNDLE_FILES = listOf(
            "package.json",
            "carve.tmLanguage.json",
            "language-configuration.json",
        )
    }
}
