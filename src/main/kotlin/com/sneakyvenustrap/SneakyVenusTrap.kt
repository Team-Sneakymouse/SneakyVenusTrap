package com.sneakyvenustrap

import com.sneakyvenustrap.commands.ReloadCommand
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

open class SneakyVenusTrap : JavaPlugin() {
    lateinit var encounters: com.sneakyvenustrap.encounter.Encounters
        private set

    private var voiceIntegration: AutoCloseable? = null

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
        encounters = com.sneakyvenustrap.encounter.Encounters(this)
        voiceIntegration = com.sneakyvenustrap.voice.VoiceIntegration.start(this, encounters)
        server.pluginManager.registerEvents(com.sneakyvenustrap.encounter.EncounterEvents(encounters), this)
        server.pluginManager.registerEvents(com.sneakyvenustrap.encounter.StomachEvents(this, encounters.stomachs), this)
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
        voiceIntegration?.close()
        voiceIntegration = null
        if (::encounters.isInitialized) encounters.close()
        logger.info("SneakyVenusTrap disabled.")
    }
}
