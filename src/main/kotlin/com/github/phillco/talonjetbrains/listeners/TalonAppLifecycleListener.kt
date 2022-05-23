package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.sync.markHasShutdown
import com.github.phillco.talonjetbrains.sync.serializeEditorStateToFile
import com.github.phillco.talonjetbrains.sync.serializeOverallState
import com.github.phillco.talonjetbrains.sync.unlinkStateFile
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.project.Project

class TalonAppLifecycleListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        super.appFrameCreated(commandLineArgs)
        println("PHIL: appFrameCreated...")
    }

    override fun welcomeScreenDisplayed() {
        super.welcomeScreenDisplayed()
    }

    override fun appStarting(projectFromCommandLine: Project?) {
        super.appStarting(projectFromCommandLine)
        println("PHIL: app starting...")
        serializeEditorStateToFile()
    }

    override fun appStarted() {
        super.appStarted()
        println("PHIL: app started...")
    }

    override fun projectFrameClosed() {
        super.projectFrameClosed()
    }

    override fun projectOpenFailed() {
        super.projectOpenFailed()
    }

    override fun appClosing() {
        println("PHIL: app closing...")
        markHasShutdown()
        super.appClosing()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        println("PHIL: app closed...")
        markHasShutdown()
        super.appWillBeClosed(isRestart)
    }
}