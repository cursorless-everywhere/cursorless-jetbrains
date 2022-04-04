package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener

class TalonCaretListener : CaretListener, Disposable {
    override fun caretPositionChanged(event: CaretEvent) {
        super.caretPositionChanged(event)
        serializeEditorStateToFile()
    }

    override fun dispose() {

    }

    override fun caretAdded(event: CaretEvent) {
        super.caretAdded(event)
        serializeEditorStateToFile()

    }

    override fun caretRemoved(event: CaretEvent) {
        super.caretRemoved(event)
        serializeEditorStateToFile()
    }
}