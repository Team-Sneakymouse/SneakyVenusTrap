package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/** Temporary nameplate workaround authorized by DaniDipp; the owning plugin recreates nameplates. */
internal class StomachTransfer(private val plugin: JavaPlugin) : Listener {
    private var serial = 0L
    private var active: UUID? = null
    private var cancelled: Boolean? = null
    private val recent = linkedMapOf<String, Pair<Long, Int>>()

    init { plugin.server.pluginManager.registerEvents(this, plugin) }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun observe(event: PlayerTeleportEvent) {
        if (event.player.uniqueId == active) cancelled = event.isCancelled
        // Observe only: never uncancel another plugin's protection.
    }

    fun decline(player: Player, garden: String, reason: String): Boolean {
        report("player=${player.uniqueId} garden=${token(garden)} phase=preflight outcome=$reason", "${player.uniqueId}:$reason")
        return false
    }

    internal fun diagnostic(message: String, key: String) = report(message, key)

    private fun token(value: String) = value.take(64).replace(Regex("[^a-zA-Z0-9_.-]"), "_")
    private fun report(message: String, key: String) {
        val now = System.nanoTime()
        val previous = recent[key]
        if (previous != null && now - previous.first < 5_000_000_000L) {
            recent[key] = previous.first to previous.second + 1
            return
        }
        plugin.logger.info("stomach-transfer $message suppressed=${previous?.second ?: 0}")
        recent[key] = now to 0
        if (recent.size > 128) recent.remove(recent.keys.first())
    }

    fun move(player: Player, destination: Location, garden: String = "recovery", mode: String = "return", reason: String = "unspecified"): Boolean {
        val attempt = ++serial
        val origin = player.location.clone()
        val passengers = player.passengers.toList()
        val types = passengers.take(8).joinToString(",") { it.type.name }
        val vehicle = player.vehicle?.type
        var removed = 0
        var result: Boolean? = null
        cancelled = null
        fun finish(outcome: String, error: Exception? = null): Boolean {
            report("attempt=$attempt player=${player.uniqueId} garden=${token(garden)} mode=$mode reason=${token(reason)} phase=transfer from=${token(origin.world.name)}:${origin.blockX},${origin.blockY},${origin.blockZ} to=${token(destination.world.name)}:${destination.blockX},${destination.blockY},${destination.blockZ} vehicle=$vehicle passengers=${passengers.size} types=[$types] removed=$removed actual=${token(player.world.name)}:${player.location.blockX},${player.location.blockY},${player.location.blockZ} teleportReturn=$result cancelledAtMonitor=$cancelled outcome=$outcome exception=${error?.javaClass?.simpleName ?: "none"}", "${player.uniqueId}:$mode:$reason:$outcome")
            return outcome == "success"
        }
        try {
            if (vehicle != null) return finish("refused-mounted")
            if (origin.world != destination.world) {
                // Validate the entire snapshot BEFORE deleting anything. Never eject players, living
                // entities, Item/BlockDisplays, nested passengers or foreign seats.
                if (passengers.any { it !is TextDisplay || it.passengers.isNotEmpty() }) return finish("refused-non-nameplate-passenger")
                for (nameplate in passengers) {
                    if (nameplate.vehicle?.uniqueId != player.uniqueId || !player.removePassenger(nameplate)) return finish("nameplate-detach-failed")
                    nameplate.remove()
                    removed++
                    if (nameplate.isValid) return finish("nameplate-remove-failed")
                }
            }
            active = player.uniqueId
            result = player.teleport(destination)
            return finish(if (result == true) "success" else "teleport-return-false")
        } catch (error: Exception) {
            return finish("exception-recovery-retained", error)
        } finally { active = null }
    }
}
