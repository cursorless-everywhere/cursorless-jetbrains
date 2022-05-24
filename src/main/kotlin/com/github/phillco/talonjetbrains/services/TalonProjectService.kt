package com.github.phillco.talonjetbrains.services

import com.github.phillco.talonjetbrains.MyBundle
import com.intellij.openapi.project.Project

class TalonProjectService(project: Project) {

    init {
        println("phil: project service in it")

        println(MyBundle.message("projectService", project.name))
    }
}
