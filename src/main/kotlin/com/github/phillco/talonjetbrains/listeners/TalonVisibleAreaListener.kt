package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener

class TalonVisibleAreaListener : VisibleAreaListener {
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
//        TODO("Not yet implemented")
        println("PHIL: scroll!")

        markEditorChange()
    }
}
