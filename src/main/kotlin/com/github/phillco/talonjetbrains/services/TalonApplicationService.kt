package com.github.phillco.talonjetbrains.services

import com.github.phillco.talonjetbrains.listeners.TalonCaretListener
import com.github.phillco.talonjetbrains.listeners.TalonDocumentListener
import com.github.phillco.talonjetbrains.listeners.TalonFocusChangeListener
import com.github.phillco.talonjetbrains.listeners.TalonSelectionListener
import com.github.phillco.talonjetbrains.listeners.TalonVisibleAreaListener
import com.github.phillco.talonjetbrains.sync.unlinkStateFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx

class TalonApplicationService : Disposable {

    private val cursorWatchers = mutableMapOf<Editor, TalonCaretListener>()
    private val selectionListeners =
        mutableMapOf<Editor, TalonSelectionListener>()
    private val visibleAreaListeners =
        mutableMapOf<Editor, TalonVisibleAreaListener>()
    private val documentListeners =
        mutableMapOf<Editor, TalonDocumentListener>()

    init {
        println("application service init")

        // Listening for window changes is necessary, since we don't seem to get them from Talon.

        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/4578776718354-How-do-I-listen-for-editor-focus-events-
        val m = EditorFactory.getInstance()
            .eventMulticaster as EditorEventMulticasterEx;
        m.addFocusChangeListener(TalonFocusChangeListener()) {}

    }

    fun editorCreated(e: Editor) {
        val cw = TalonCaretListener()
        e.caretModel.addCaretListener(cw)
        cursorWatchers[e] = cw

        val sl = TalonSelectionListener()
        e.selectionModel.addSelectionListener(sl)
        selectionListeners[e] = sl

        val visibleAreaListener = TalonVisibleAreaListener()
        e.scrollingModel.addVisibleAreaListener(visibleAreaListener)
        visibleAreaListeners[e] = visibleAreaListener

        val dl = TalonDocumentListener()
        e.document.addDocumentListener(dl)
        documentListeners[e] = dl
    }

    fun rebindListeners() {
        println("rebinding listeners...")
        EditorFactory.getInstance().allEditors.forEach { e ->
            println("hi $e")
            this.editorCreated(e)
        }
//        ProjectManager.getInstance().openProjects.forEach { proj ->
//            println("project: ${proj} ${FileEditorManager.getInstance(proj).allEditors.size}")
//            FileEditorManager.getInstance(proj).allEditors.forEach { editor ->
//                run {
//                    println("editor: ${editor.edit}")
//
//                    this.editorCreated(editor.)
//                }
//            }
//        }
    }

    override fun dispose() {
        println("PHIL: disposing")
        unlinkStateFile()

        println("PHIL: unhooking listeners")
        cursorWatchers.forEach { (e, l) -> e.caretModel.removeCaretListener(l) }
        selectionListeners.forEach { (e, l) ->
            e.selectionModel.removeSelectionListener(
                l
            )
        }
        visibleAreaListeners.forEach { (e, l) ->
            e.scrollingModel.removeVisibleAreaListener(
                l
            )
        }
        documentListeners.forEach { (e, l) ->
            e.document.removeDocumentListener(
                l
            )
        }
    }
}
