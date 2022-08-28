package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markHasShutdown
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.talon.createControlSocket
import com.intellij.ide.AppLifecycleListener

class TalonAppLifecycleListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        super.appFrameCreated(commandLineArgs)
        println("PHIL: appFrameCreated...")
    }

    override fun appStarted() {
        super.appStarted()
        println("PHIL: app started...")
        createControlSocket()
    }

    override fun appClosing() {
        println("PHIL: app closing...")
        markHasShutdown()
        super.appClosing()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        println("PHIL: app closed...")
        markHasShutdown()
        serializeEditorStateToFile()
        super.appWillBeClosed(isRestart)
    }
}
