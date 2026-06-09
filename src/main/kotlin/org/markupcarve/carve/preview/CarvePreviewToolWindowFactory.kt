package org.markupcarve.carve.preview

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.markupcarve.carve.CarveFileType
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class CarvePreviewToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val placeholderPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Open a .carve file to see preview", JLabel.CENTER), BorderLayout.CENTER)
        }
        toolWindow.contentManager.addContent(
            contentFactory.createContent(placeholderPanel, "", false),
        )

        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    if (CarveFileType.matches(file.extension)) {
                        updatePreview(project, toolWindow, file)
                    }
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile
                    if (file != null && CarveFileType.matches(file.extension)) {
                        updatePreview(project, toolWindow, file)
                    }
                }
            },
        )

        FileEditorManager.getInstance(project).selectedFiles
            .firstOrNull { CarveFileType.matches(it.extension) }
            ?.let { updatePreview(project, toolWindow, it) }
    }

    private fun updatePreview(project: Project, toolWindow: ToolWindow, file: VirtualFile) {
        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)
        val previewPanel = CarvePreviewPanel(project, file)
        contentManager.addContent(
            ContentFactory.getInstance().createContent(previewPanel.component, file.name, false),
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
