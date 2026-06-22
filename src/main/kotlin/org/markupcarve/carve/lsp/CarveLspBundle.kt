package org.markupcarve.carve.lsp

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Locates the bundled carve-lsp server (vendored under resources/lsp/server.js
 * by tools/build-lsp-bundle.sh) and exposes it as a real file on disk so a Node
 * process can run it.
 *
 * Plugin resources live inside the plugin's classpath (a jar after packaging),
 * and `node` cannot execute a path inside a jar. The bundle is therefore copied
 * out to a stable location under the IDE system directory on first use and
 * reused on later launches when unchanged.
 */
object CarveLspBundle {
    private val LOG = logger<CarveLspBundle>()

    private const val RESOURCE_DIR = "/lsp"
    private const val SERVER_FILE = "server.js"
    private const val VERSION_FILE = "VERSION"

    /**
     * Materializes the bundled server and returns the path to its server.js, or
     * null when the bundle is missing from the plugin (a packaging bug). The
     * returned file is safe to pass to `node ... --stdio`.
     */
    fun extractServer(): Path? {
        val serverBytes = readResource(SERVER_FILE) ?: run {
            LOG.warn("Bundled carve-lsp server.js not found on the plugin classpath")
            return null
        }
        val version = readResourceText(VERSION_FILE) ?: ""

        val targetDir = Path.of(PathManager.getSystemPath(), "carve-lsp")
        val targetServer = targetDir.resolve(SERVER_FILE)
        val targetVersion = targetDir.resolve(VERSION_FILE)

        // Re-extract only when the vendored VERSION differs from what is on disk,
        // so an IDE restart does not rewrite an identical file every launch.
        val onDisk = runCatching { Files.readString(targetVersion) }.getOrNull()
        if (Files.exists(targetServer) && onDisk == version) {
            return targetServer
        }

        Files.createDirectories(targetDir)
        Files.write(targetServer, serverBytes)
        Files.writeString(targetVersion, version)
        LOG.info("Extracted bundled carve-lsp to $targetServer ($version)")
        return targetServer
    }

    private fun readResource(name: String): ByteArray? =
        CarveLspBundle::class.java.getResourceAsStream("$RESOURCE_DIR/$name")?.use { it.readBytes() }

    private fun readResourceText(name: String): String? =
        readResource(name)?.toString(Charsets.UTF_8)
}

/**
 * Resolves a `node` executable. Prefers an explicit path from settings, then a
 * `node`/`node.exe` on the system PATH. Returns null when none is found so the
 * caller can surface an actionable notification instead of crashing.
 */
object NodeLocator {
    fun find(explicitPath: String?): String? {
        if (!explicitPath.isNullOrBlank()) {
            val file = File(explicitPath)
            if (file.canExecute()) return file.absolutePath
        }
        val names = if (isWindows()) listOf("node.exe", "node") else listOf("node")
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            for (name in names) {
                val candidate = File(dir, name)
                if (candidate.canExecute()) return candidate.absolutePath
            }
        }
        return null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
