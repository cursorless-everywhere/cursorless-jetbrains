package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import java.awt.event.FocusEvent

class TalonFocusChangeListener : FocusChangeListener {
    override fun focusGained(editor: Editor) {
        println("focus gained: $editor")
        serializeEditorStateToFile()
    }

    override fun focusGained(editor: Editor, event: FocusEvent) {
        println("focus gained: $editor $event")
        serializeEditorStateToFile()
    }

    override fun focusLost(editor: Editor) {
        println("focus lost: $editor")
        serializeEditorStateToFile()
    }

    override fun focusLost(editor: Editor, event: FocusEvent) {
        println("focus lost: $editor $event")
        serializeEditorStateToFile()
    }
}
