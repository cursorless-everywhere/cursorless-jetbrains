package com.github.phillco.talonjetbrains.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.github.phillco.talonjetbrains.services.MyProjectService

internal class MyProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        println("PHIL: project opened 2")

        project.service<MyProjectService>()
    }

    override fun projectClosed(project: Project) {
        println("PHIL: project closed")
        super.projectClosed(project)
    }
}

