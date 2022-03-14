package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.services.MyApplicationService
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class TalonEditorFactoryListener : EditorFactoryListener {



    init {
        println("phil: listener scope")
    }
    override fun editorCreated(event: EditorFactoryEvent) {
        println("PHIL: editor created")

        val applicationService = service<MyApplicationService>()
        applicationService.editorCreated(event.editor)

    }

    override fun editorReleased(event: EditorFactoryEvent) {
        println("phil: editor released")
        super.editorReleased(event)
    }
}