package com.github.phillco.talonjetbrains.services

import com.intellij.openapi.project.Project
import com.github.phillco.talonjetbrains.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
