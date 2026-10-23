package com.sneakyvenustrap.voice

import com.sneakyvenustrap.encounter.Encounters
import com.sneakyvenustrap.service.MicrophoneActivity
import org.bukkit.plugin.java.JavaPlugin

/** Isolates optional API linkage from the always-loaded bootstrap. Start/close on the server thread. */
object VoiceIntegration {
    fun start(plugin: JavaPlugin, encounters: Encounters): AutoCloseable {
        val activity = MicrophoneActivity()
        encounters.microphoneActive = activity::active
        return try {
            SvcMicrophoneIntegration.start(plugin, activity) ?: activity.also {
                plugin.logger.info("Simple Voice Chat service absent; hunting uses movement noise only.")
            }
        } catch (failure: LinkageError) {
            activity.close()
            plugin.logger.info("Simple Voice Chat API unavailable; hunting uses movement noise only.")
            activity
        } catch (failure: Exception) {
            activity.close()
            plugin.logger.warning("Simple Voice Chat integration unavailable: ${failure.message}; movement noise only.")
            activity
        }
    }
}
