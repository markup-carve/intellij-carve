package org.markupcarve.carve

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider.PluginBundle
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Supplies the Carve TextMate bundle (grammar + language configuration) to the IDE's bundled
 * TextMate engine, which drives editor syntax highlighting.
 *
 * Plugin resources live individually inside the jar, so the bundle files are extracted to a
 * directory under the IDE system path and that directory is handed to TextMate. The directory
 * is named after a **digest of the bundle's own contents**: change any bundled file and the
 * name changes with it, so a stale copy can never be served.
 *
 * This used to be a hand-maintained `BUNDLE_VERSION` constant, which was a trap twice over -
 * it looked like the plugin version (it is unrelated; the plugin is versioned in
 * `build.gradle.kts`) and forgetting to bump it meant the IDE silently kept highlighting with
 * the previous grammar.
 */
class CarveTextMateBundleProvider : TextMateBundleProvider {

    override fun getBundles(): List<PluginBundle> {
        val dir = extractBundle() ?: return emptyList()
        return listOf(PluginBundle("carve", dir))
    }

    private fun extractBundle(): Path? {
        val contents = BUNDLE_FILES.associateWith { rel ->
            javaClass.classLoader.getResourceAsStream("textmate/$rel")?.use { it.readBytes() }
                ?: return null
        }

        val target = Path.of(PathManager.getSystemPath(), "carve-textmate-bundle", digestOf(contents))
        for ((rel, bytes) in contents) {
            val out = target.resolve(rel)
            if (Files.exists(out)) continue
            Files.createDirectories(out.parent)
            Files.write(out, bytes)
        }
        return target
    }

    /** Short content digest, stable across runs and distinct for any bundle change. */
    private fun digestOf(contents: Map<String, ByteArray>): String {
        val sha = MessageDigest.getInstance("SHA-256")
        for (rel in BUNDLE_FILES) {
            sha.update(rel.toByteArray())
            sha.update(contents.getValue(rel))
        }
        return sha.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val BUNDLE_FILES = listOf(
            "package.json",
            "carve.tmLanguage.json",
            "language-configuration.json",
        )
    }
}
