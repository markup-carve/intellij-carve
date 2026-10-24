package org.markupcarve.carve.lsp

import com.intellij.openapi.project.Project

/**
 * Restarts the bundled language server so it re-reads its `initialize`
 * settings.
 *
 * lsp4ij is an OPTIONAL dependency, so no class that is always loaded may name
 * one of its types. The implementation is registered by the optional descriptor.
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
        project.getService(CarveLspRestarter::class.java)?.restart(SERVER_ID)
    }
}

interface CarveLspRestarter {
    fun restart(serverId: String)
}
