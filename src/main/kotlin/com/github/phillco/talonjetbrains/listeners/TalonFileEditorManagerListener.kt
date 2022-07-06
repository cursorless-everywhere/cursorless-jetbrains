package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile

class TalonFileEditorManagerListener : FileEditorManagerListener {
    override fun fileOpenedSync(
        source: FileEditorManager,
        file: VirtualFile,
        editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>
    ) {
        super.fileOpenedSync(source, file, editors)
        markEditorChange("file editor manager listener -> file opened sync")
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
        // TODO(pcohen): we seem to be missing a handler on the file editing
        // in general. For example, control-backspace does not fire.
        markEditorChange("file editor manager listener -> file (tab) opened")
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        markEditorChange("file editor manager listener -> file (tab) closed")
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        markEditorChange("file editor manager listener -> selection changed (tab switched)")
    }
}
