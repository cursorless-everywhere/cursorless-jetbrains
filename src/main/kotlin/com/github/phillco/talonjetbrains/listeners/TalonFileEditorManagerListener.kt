package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile

class TalonFileEditorManagerListener : FileEditorManagerListener {
    override fun fileOpenedSync(
        source: FileEditorManager,
        file: VirtualFile,
        editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>
    ) {
        super.fileOpenedSync(source, file, editors)
        println("PHIL: file opened synchronized")
        markEditorChange()
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        super.fileOpened(source, file)
        println("PHIL: file opened (tab opened)")
        markEditorChange()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        super.fileClosed(source, file)
        println("PHIL: file closed (tab closed)")
        markEditorChange()
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        super.selectionChanged(event)
        println("PHIL: selection changed (tab switched)")
        markEditorChange()
    }
}
