package com.github.phillco.talonjetbrains.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class TalonProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        println("PHIL: project opened")
        super.projectOpened(project)
    }

    override fun projectClosed(project: Project) {
        println("PHIL: project closed")
        super.projectClosed(project)
    }
}
