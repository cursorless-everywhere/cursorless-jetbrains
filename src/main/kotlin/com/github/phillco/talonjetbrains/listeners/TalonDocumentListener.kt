package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markEditorChange
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class TalonDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)

        getCursorlessContainers().filter { c -> c.editor.document == event.document }.forEach { c -> c.addLocalOffset(event.offset, event.newLength - event.oldLength) }

        markEditorChange("document listener -> document area changed (offset = ${event.offset}, old length = ${event.oldLength}, new length = ${event.newLength}")
    }
}
