package org.markupcarve.carve.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.includes.CarveFlattenReport

/**
 * Write the document out as ONE self-contained `.crv`, every include merged in.
 *
 * Its own named action rather than an option on "export to Carve", because
 * spec I15 says writing a document back as Carve returns the AUTHOR's document
 * - directives intact. Flattening asks for the other document, the one that can
 * be handed to something with no filesystem behind it.
 */
class ExportFlattenedCarveAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val (file, flattened) = CarveFlattenSupport.flatten(e, TITLE) ?: return

        val descriptor = FileSaverDescriptor(
            TITLE,
            "Choose where to write the self-contained Carve file",
            "crv",
        )
        val wrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(file.parent, file.nameWithoutExtension + ".flat.crv")
            ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val outcome = runCatching { wrapper.file.writeText(flattened.carve) }
            ApplicationManager.getApplication().invokeLater {
                if (outcome.isFailure) {
                    Messages.showErrorDialog(
                        project,
                        "Failed to write ${wrapper.file.absolutePath}: ${outcome.exceptionOrNull()?.message}",
                        TITLE,
                    )
                    return@invokeLater
                }
                Messages.showInfoMessage(
                    project,
                    "Wrote ${wrapper.file.absolutePath}\n\n" +
                        CarveFlattenReport.summary(flattened.warnings, flattened.dependencies),
                    TITLE,
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private companion object {
        const val TITLE = "Export Self-Contained Carve"
    }
}
