package com.github.phillco.talonjetbrains.services

import com.github.phillco.talonjetbrains.MyBundle
import com.github.phillco.talonjetbrains.listeners.TalonCaretListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory

class MyApplicationService : Disposable {

    val cursorWatchers = mutableMapOf<Editor, TalonCaretListener>()

    init {
        println("phil: application service in it 2")

//        println(MyBundle.message("applicationService"))
    }



    fun foo() {
        println("PHIL: foo 3!")
    }

    fun editorCreated(e: Editor) {
        val cw = TalonCaretListener()
        e.caretModel.addCaretListener(cw)
        cursorWatchers[e] = cw
    }

    fun rebindListeners() {
        println("rebinding listeners...")
        EditorFactory.getInstance().allEditors.forEach { e ->
            println("hi ${e}")
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


        cursorWatchers.forEach { t, u ->  t.caretModel.removeCaretListener(u) }

    }
}
