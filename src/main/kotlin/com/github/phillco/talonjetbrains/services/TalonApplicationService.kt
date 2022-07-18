package com.github.phillco.talonjetbrains.services

import com.github.phillco.talonjetbrains.listeners.TalonCaretListener
import com.github.phillco.talonjetbrains.listeners.TalonDocumentListener
import com.github.phillco.talonjetbrains.listeners.TalonSelectionListener
import com.github.phillco.talonjetbrains.listeners.TalonVisibleAreaListener
import com.github.phillco.talonjetbrains.sync.unlinkStateFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import io.sentry.Sentry

class TalonApplicationService : Disposable {

    val cursorWatchers = mutableMapOf<Editor, TalonCaretListener>()
    val selectionListeners = mutableMapOf<Editor, TalonSelectionListener>()
    val visibleAreaListeners = mutableMapOf<Editor, TalonVisibleAreaListener>()
    val documentListeners = mutableMapOf<Editor, TalonDocumentListener>()

    init {
        println("phil: application service in it 2")

        Sentry.init { options ->
            options.dsn =
                "https://9cbfe01d53c14fc99e6a664054ca1a18@o313576.ingest.sentry.io/6307779"
            // Set tracesSampleRate to 1.0 to capture 100% of transactions for performance monitoring.
            // We recommend adjusting this value in production.
            options.tracesSampleRate = 1.0
        }
        println("phil: Sentry set up!")

//        println(MyBundle.message("applicationService"))
    }

    fun foo() {
        println("PHIL: foo 3!")
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
        selectionListeners.forEach { (e, l) -> e.selectionModel.removeSelectionListener(l) }
        visibleAreaListeners.forEach { (e, l) -> e.scrollingModel.removeVisibleAreaListener(l) }
        documentListeners.forEach { (e, l) -> e.document.removeDocumentListener(l) }
    }
}
