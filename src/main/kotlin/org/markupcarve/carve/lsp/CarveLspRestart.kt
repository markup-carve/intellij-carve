package org.markupcarve.carve.lsp

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * Restarts the bundled language server so it re-reads its `initialize`
 * settings.
 *
 * lsp4ij is an OPTIONAL dependency, so no class that is always loaded may name
 * one of its types. The lsp4ij call therefore lives here, behind
 * [isLsp4ijAvailable]: the JVM resolves `LanguageServerManager` when [restart]
 * actually runs, which only happens once the check has said the plugin is
 * installed and enabled.
 */
object CarveLspRestart {

    private const val LSP4IJ_MANAGER_CLASS = "com.redhat.devtools.lsp4ij.LanguageServerManager"
    private const val SERVER_ID = "carveLanguageServer"

    fun isLsp4ijAvailable(): Boolean = try {
        Class.forName(LSP4IJ_MANAGER_CLASS, false, javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    fun restart(project: Project) {
        if (!isLsp4ijAvailable()) return
        try {
            val manager = com.redhat.devtools.lsp4ij.LanguageServerManager.getInstance(project)
            manager.stop(SERVER_ID)
            manager.start(SERVER_ID)
        } catch (e: Exception) {
            // A settings change must not fail because the server would not come
            // back; the next IDE start picks the new options up either way.
            thisLogger().warn("Could not restart the Carve language server after a settings change", e)
        }
    }
}
