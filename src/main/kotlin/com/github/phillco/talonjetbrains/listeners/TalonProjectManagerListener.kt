package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.services.TalonProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class TalonProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        println("PHIL: project opened 2")

        project.service<TalonProjectService>()

//        serializeEditorStateToFile()
    }

    override fun projectClosed(project: Project) {
        println("PHIL: project closed")
        super.projectClosed(project)
    }
}
