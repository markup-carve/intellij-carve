package org.markupcarve.carve.lsp

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager

class Lsp4ijRestarter(private val project: Project) : CarveLspRestarter {
    override fun restart(serverId: String) {
        try {
            val manager = LanguageServerManager.getInstance(project)
            manager.stop(serverId)
            manager.start(serverId)
        } catch (e: Exception) {
            thisLogger().warn("Could not restart the Carve language server after a settings change", e)
        }
    }
}
