package org.markupcarve.carve.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.includes.CarveFlattenReport
import org.markupcarve.carve.includes.CarveTransferable

/**
 * Put the assembled document on the clipboard - the same primitive as the
 * self-contained export, with a different destination.
 *
 * It goes on under `text/x-carve` and as plain text at once
 * (markup-carve/carve#2050), so pasting into a Carve editor and pasting into a
 * web box both do the right thing with no choice to make.
 */
class CopyFlattenedDocumentAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val (_, flattened) = CarveFlattenSupport.flatten(e, TITLE) ?: return

        CopyPasteManager.getInstance().setContents(CarveTransferable(flattened.carve))
        Messages.showInfoMessage(
            project,
            "Copied the assembled document.\n\n" +
                CarveFlattenReport.summary(flattened.warnings, flattened.dependencies),
            TITLE,
        )
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private companion object {
        const val TITLE = "Copy as a Single Document"
    }
}
