package org.markupcarve.carve.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import org.markupcarve.carve.CarveFileType

class TogglePreviewAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Carve Preview") ?: return
        if (toolWindow.isVisible) toolWindow.hide() else toolWindow.show()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = CarveFileType.matches(file?.extension)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
