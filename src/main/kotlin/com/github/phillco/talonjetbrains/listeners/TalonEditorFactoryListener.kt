package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.cursorless.CursorlessContainer
import com.github.phillco.talonjetbrains.services.TalonApplicationService
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class TalonEditorFactoryListener : EditorFactoryListener {

    var containers = ArrayList<CursorlessContainer>()

    init {
        println("phil: listener scope")
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        println("PHIL: editor created")

        val applicationService = service<TalonApplicationService>()
        applicationService.editorCreated(event.editor)

        containers += CursorlessContainer(event.editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        println("phil: editor released")
        super.editorReleased(event)
    }
}
