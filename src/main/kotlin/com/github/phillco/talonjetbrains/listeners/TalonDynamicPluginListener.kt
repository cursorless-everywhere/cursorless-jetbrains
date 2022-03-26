package com.github.phillco.talonjetbrains.listeners

import com.github.phillco.talonjetbrains.services.TalonApplicationService
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import kotlin.Throws
import com.intellij.ide.plugins.CannotUnloadPluginException
import com.intellij.openapi.components.service

class TalonDynamicPluginListener : DynamicPluginListener {
    init {
        println("PHIL: dynamic plug and listener created!")
    }

    override fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        super.beforePluginLoaded(pluginDescriptor)
        println("PHIL: before loaded...")
    }

    override fun beforePluginUnload(
        pluginDescriptor: IdeaPluginDescriptor,
        isUpdate: Boolean
    ) {
        super.beforePluginUnload(
            pluginDescriptor,
            isUpdate
        )
        println("PHIL: before unloaded...")
    }


    @Throws(CannotUnloadPluginException::class)
    override fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
        super.checkUnloadPlugin(pluginDescriptor)
        println("PHIL: check unload...")
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        super.pluginLoaded(pluginDescriptor)
        println("PHIL: plugin loaded!")

        val applicationService = service<TalonApplicationService>()

        applicationService.rebindListeners()

    }

    override fun pluginUnloaded(
        pluginDescriptor: IdeaPluginDescriptor,
        isUpdate: Boolean
    ) {
        super.pluginUnloaded(pluginDescriptor, isUpdate)
        println("PHIL: unloaded...")
    }
}