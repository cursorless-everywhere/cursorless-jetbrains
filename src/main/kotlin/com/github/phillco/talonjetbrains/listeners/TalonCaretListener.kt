package com.github.phillco.talonjetbrains.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener

class TalonCaretListener : CaretListener, Disposable {
    override fun caretPositionChanged(event: CaretEvent) {
        println("PHIL: caret changed 8")
        super.caretPositionChanged(event)
    }

    override fun dispose() {

    }

    override fun caretAdded(event: CaretEvent) {
        super.caretAdded(event)
        println("phil: added")
    }

    override fun caretRemoved(event: CaretEvent) {
        super.caretRemoved(event)
    }
}