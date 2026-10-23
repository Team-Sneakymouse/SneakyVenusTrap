package com.sneakyvenustrap.voice

import com.sneakyvenustrap.service.MicrophoneActivity
import de.maxhenkel.voicechat.api.BukkitVoicechatService
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/** UUID/timestamp metadata only. No codec, recording, packet mutation, or per-packet scheduled work. */
internal class SvcMicrophoneIntegration private constructor(
    private val plugin: JavaPlugin,
    private val activity: MicrophoneActivity,
) : VoicechatPlugin, Listener, AutoCloseable {
    @Volatile private var closed = false
    private var task: BukkitTask? = null
    override fun getPluginId() = "sneakyvenustrap"
    override fun registerEvents(registration: EventRegistration) {
        if (closed) return
        registration.registerEvent(MicrophonePacketEvent::class.java, { event ->
            // Voice network thread: only the SVC API and bounded thread-safe metadata are accessed.
            if (!closed && !event.isCancelled) {
                val sender = event.senderConnection
                // Group conversations are not nearby-world speech. Empty packets end a stream.
                if (sender != null && !sender.isInGroup && !sender.isDisabled && event.packet.opusEncodedData.isNotEmpty()) {
                    activity.transmitted(sender.player.uuid)
                }
            }
        })
    }
    private fun snapshot(exclude: java.util.UUID? = null) {
        activity.admit(plugin.server.onlinePlayers.map { it.uniqueId }.filter { it != exclude }.toSet())
    }
    @EventHandler fun join(event: PlayerJoinEvent) { snapshot() }
    @EventHandler fun quit(event: PlayerQuitEvent) { snapshot(event.player.uniqueId) }
    override fun close() {
        closed = true // The API has no unregisterPlugin; retained callbacks must be inert.
        activity.close()
        task?.cancel()
        HandlerList.unregisterAll(this)
    }
    companion object {
        fun start(plugin: JavaPlugin, activity: MicrophoneActivity): AutoCloseable? {
            val service = plugin.server.servicesManager.load(BukkitVoicechatService::class.java) ?: return null
            val integration = SvcMicrophoneIntegration(plugin, activity)
            try {
                integration.snapshot()
                plugin.server.pluginManager.registerEvents(integration, plugin)
                integration.task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { integration.snapshot() }, 1, 20)
                service.registerPlugin(integration)
                plugin.logger.info("Simple Voice Chat microphone activity enabled (metadata only; no audio recording).")
                return integration
            } catch (failure: Throwable) {
                integration.close()
                throw failure
            }
        }
    }
}
