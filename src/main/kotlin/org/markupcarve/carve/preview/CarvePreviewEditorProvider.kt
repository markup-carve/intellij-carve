package org.markupcarve.carve.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.markupcarve.carve.CarveFileType
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class CarvePreviewEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        CarveFileType.matches(file.extension)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return CarveSplitEditor(
            TextEditorProvider.getInstance().createEditor(project, file) as TextEditor,
            CarvePreviewFileEditor(project, file),
        )
    }

    override fun getEditorTypeId(): String = "carve-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class CarvePreviewFileEditor(
    project: Project,
    private val file: VirtualFile,
) : FileEditor {

    private val previewPanel = CarvePreviewPanel(project, file)
    private val userData = UserDataHolderBase()

    override fun getComponent(): JComponent = previewPanel.component

    override fun getPreferredFocusedComponent(): JComponent = previewPanel.component

    override fun getName(): String = "Carve Preview"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun dispose() {
        previewPanel.dispose()
    }

    override fun getFile(): VirtualFile = file

    override fun <T : Any?> getUserData(key: Key<T>): T? = userData.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)
}
