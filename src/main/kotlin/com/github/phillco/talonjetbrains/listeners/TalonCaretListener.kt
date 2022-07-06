package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener

class TalonCaretListener : CaretListener, Disposable {
    override fun caretPositionChanged(event: CaretEvent) {
        super.caretPositionChanged(event)
        markEditorChange("caret listener -> caret changed")
    }

    // TODO(pcohen):
    override fun dispose() {
    }

    override fun caretAdded(event: CaretEvent) {
        super.caretAdded(event)
        markEditorChange("caret listener -> caret added")
    }

    override fun caretRemoved(event: CaretEvent) {
        super.caretRemoved(event)
        markEditorChange("caret listener -> caret removed")
    }
}
