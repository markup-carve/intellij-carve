package org.markupcarve.carve.lsp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.markupcarve.carve.settings.CarveSettings

/**
 * Launches the bundled carve-lsp Node server over stdio for lsp4ij.
 *
 * The command is `node <extractedServerDir>/server.js --stdio`. `node` is taken
 * from the Carve settings node-path override, falling back to the system PATH.
 * When neither the bundle nor `node` is available the provider stays
 * unconfigured (empty command line) and the user is told what to do via a
 * notification, rather than the IDE throwing on a missing process.
 */
class CarveLspServer(private val project: Project) : OSProcessStreamConnectionProvider() {

    /** Why the server cannot start, or null once a command line is configured. */
    private var unavailableReason: String? = null

    init {
        configure()
    }

    private fun configure() {
        val serverPath = CarveLspBundle.extractServer()
        if (serverPath == null) {
            disable(
                "The bundled Carve language server is missing from the plugin. " +
                    "Diagnostics, completion, and related features are unavailable.",
            )
            return
        }

        val nodePath = NodeLocator.find(CarveSettings.getInstance(project).nodePath)
        if (nodePath == null) {
            disable(
                "Node.js was not found on your PATH, so the Carve language server " +
                    "(diagnostics, completion, folding, outline, code actions) is disabled. " +
                    "Install Node.js, or set its path in Settings | Tools | Carve.",
            )
            return
        }

        commandLine = com.intellij.execution.configurations.GeneralCommandLine(
            nodePath,
            serverPath.toString(),
            "--stdio",
        )
    }

    /**
     * Without a configured command line, the parent `start()` would throw an
     * opaque `CannotStartProcessException` (the superclass cannot launch a null
     * command). Short-circuit with the actionable reason instead, so the LSP
     * console shows why the server is off rather than a generic process error.
     * The user also got a balloon at configuration time.
     */
    override fun start() {
        unavailableReason?.let { throw CannotStartProcessException(it) }
        super.start()
    }

    private fun disable(message: String) {
        unavailableReason = message
        notify(message)
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Carve")
            ?.createNotification("Carve language server", message, NotificationType.WARNING)
            ?.notify(project)
    }
}
