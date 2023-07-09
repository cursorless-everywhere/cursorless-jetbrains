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

        // NOTE(pcohen): this is a useful way to test the socket connection
        // (doing so from the control socket leads to weird stack traces
        // if it's a linking issue)
//        try {
//            val r = sendCommand(VSCodeCommand("ping"))
//            println("PH: result |${r}|")
//        } catch (e: Exception) {
//            e.printStackTrace()
//            println("PH: |${e}|")
//            throw e
//        }

        // NOTE(pcohen): terrible workaround
        // https://github.com/cursorless-everywhere/cursorless-jetbrains/issues/16
        System.setProperty(
            "jna.boot.library.path",
            "/Applications/IntelliJ IDEA 2023.1.app/Contents/lib/jna/aarch64"
        )

//        System.setProperty("jna.debug_load.jna", "true");
//        System.setProperty("jna.debug_load", "true");
//
//        var watcher = DirectoryWatcher.builder()
//            .path(Path.of(CURSORLESS_FOLDER))
//            .logger(NOPLogger.NOP_LOGGER)
//            .listener { event: DirectoryChangeEvent ->
//
//            }.build()
//
//        var watchThread = Thread {
//            try {
//                watcher.watch()
//            } catch (e: UnsatisfiedLinkError) {
//                e.printStackTrace()
//
//                println("PH: |${e}|")
//                // NOTE(pcohen): On 2023.1 there seems to be a JNI link error.
//                System.exit(1)
//            }
//        }
//        watchThread.start()

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
