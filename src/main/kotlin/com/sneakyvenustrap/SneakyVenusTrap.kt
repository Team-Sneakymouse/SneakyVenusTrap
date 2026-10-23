package com.sneakyvenustrap

import com.sneakyvenustrap.commands.ReloadCommand
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

open class SneakyVenusTrap : JavaPlugin() {

    companion object {
        const val IDENTIFIER = "sneakyvenustrap"
        lateinit var instance: SneakyVenusTrap

        fun log(message: String) {
            instance.logger.info(message)
        }
    }

    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        saveDefaultConfig()
        reloadPlugin()
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(ReloadCommand(this).create(), "Reload the plugin configuration")
        }
        logger.info("SneakyVenusTrap enabled.")
    }

    /** Shared configuration entry point for startup and reload. */
    fun reloadPlugin() {
        reloadConfig()
    }

    override fun onDisable() {
        logger.info("SneakyVenusTrap disabled.")
    }
}
