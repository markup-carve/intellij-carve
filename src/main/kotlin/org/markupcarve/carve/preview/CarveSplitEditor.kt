package org.markupcarve.carve.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JSplitPane

class CarveSplitEditor(
    private val textEditor: TextEditor,
    private val previewEditor: FileEditor,
) : FileEditor {

    private val splitPane: JSplitPane = JSplitPane(
        JSplitPane.HORIZONTAL_SPLIT,
        textEditor.component,
        previewEditor.component,
    ).apply {
        resizeWeight = 0.5
        dividerSize = 3
    }

    private val userData = UserDataHolderBase()

    init {
        Disposer.register(this, textEditor)
        Disposer.register(this, previewEditor)
    }

    override fun getComponent(): JComponent = splitPane

    override fun getPreferredFocusedComponent(): JComponent? = textEditor.preferredFocusedComponent

    override fun getName(): String = "Carve Editor"

    override fun setState(state: FileEditorState) {
        textEditor.setState(state)
    }

    override fun isModified(): Boolean = textEditor.isModified

    override fun isValid(): Boolean = textEditor.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        textEditor.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        textEditor.removePropertyChangeListener(listener)
    }

    override fun dispose() {}

    override fun getFile(): VirtualFile? = textEditor.file

    override fun <T : Any?> getUserData(key: Key<T>): T? = userData.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) = userData.putUserData(key, value)

    fun getTextEditor(): TextEditor = textEditor
}
