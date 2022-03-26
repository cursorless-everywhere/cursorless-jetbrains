package com.github.phillco.talonjetbrains.services

import com.intellij.openapi.project.Project
import com.github.phillco.talonjetbrains.MyBundle

class TalonProjectService(project: Project) {

    init {
        println("phil: project service in it")

        println(MyBundle.message("projectService", project.name))
    }
}
