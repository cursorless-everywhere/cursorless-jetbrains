package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener

class TalonSelectionListener : SelectionListener, Disposable {

    override fun selectionChanged(e: SelectionEvent) {
        super.selectionChanged(e)
        markEditorChange("selection listener -> selection changed")
    }

    // TODO(pcohen):
    override fun dispose() {
    }
}
