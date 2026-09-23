package org.markupcarve.carve.lsp

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
 *
 * It also carries the client half of the settings handshake - see
 * [getInitializationOptions]. The server ships capabilities that are off until
 * the client asks for them, and this provider is the only place that can ask.
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
     * The settings the server reads once, at `initialize`.
     *
     * Until this existed the plugin sent NOTHING, and the omission was not
     * neutral: carve-lsp defaults `carve.includes.enabled` to `auto`, `auto`
     * resolves includes only in a trusted workspace, and `workspaceTrusted`
     * defaults to false. So include resolution was off for every user of this
     * plugin while the server carried the whole feature - go-to-definition into
     * an included file, include-path completion, the child's headings in the
     * Structure view, and the section 19 diagnostics that turn an unresolved
     * `{{ path }}` from ordinary-looking prose into a reported warning.
     *
     * IntelliJ's project trust maps straight onto the server's, so an untrusted
     * project still resolves nothing - which is what section 19 asks for, and
     * why the setting's default is `auto` rather than `on`.
     */
    override fun getInitializationOptions(rootUri: VirtualFile?): Any = clientSettings(project)

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

    companion object {
        /** Sent both at `initialize` and on `workspace/didChangeConfiguration`. */
        fun clientSettings(project: Project): Any {
            val settings = CarveSettings.getInstance(project)
            return CarveLspInitializationOptions.build(
                mode = settings.includeMode,
                includeRoot = settings.includeRoot,
                workspaceTrusted = project.isTrusted(),
            )
        }
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Carve")
            ?.createNotification("Carve language server", message, NotificationType.WARNING)
            ?.notify(project)
    }
}
