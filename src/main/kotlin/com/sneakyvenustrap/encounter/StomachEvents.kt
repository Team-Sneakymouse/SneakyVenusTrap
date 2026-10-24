package com.sneakyvenustrap.encounter

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class StomachEvents(private val plugin: JavaPlugin, private val stomachs: Stomachs) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun breakBlock(event: BlockBreakEvent) { if (stomachs.isArena(event.block.world)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun teleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
        if (stomachs.transferring) return
        if (stomachs.contains(event.player.uniqueId)) {
            // Position corrections inside the owned room are not escapes. Never override cancellation.
            if (stomachs.isArena(event.to.world)) {
                if (!stomachs.isRoomDestination(event.player.uniqueId, event.to)) event.isCancelled = true
                return
            }
            event.isCancelled = true
            stomachs.release(event.player, Stomachs.ReleaseReason.EXTERNAL_TELEPORT)
        } else if (stomachs.isArena(event.to.world)) event.isCancelled = true
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun observeTeleport(event: org.bukkit.event.player.PlayerTeleportEvent) { stomachs.observeTeleport(event) }

    @EventHandler fun join(event: org.bukkit.event.player.PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { stomachs.release(event.player, Stomachs.ReleaseReason.JOIN_RECOVERY) })
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun death(event: org.bukkit.event.entity.PlayerDeathEvent) {
        if (!stomachs.isArena(event.entity.world)) return
        event.keepInventory = true
        event.keepLevel = true
        event.drops.clear()
        event.droppedExp = 0
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun respawn(event: org.bukkit.event.player.PlayerRespawnEvent) {
        stomachs.returnForRespawn(event.player.uniqueId)?.let { event.respawnLocation = it }
        plugin.server.scheduler.runTask(plugin, Runnable { stomachs.release(event.player, Stomachs.ReleaseReason.RESPAWN) })
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    fun interact(event: org.bukkit.event.player.PlayerInteractEvent) {
        if (stomachs.isArena(event.player.world)) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun place(event: org.bukkit.event.block.BlockPlaceEvent) { if (stomachs.isArena(event.block.world)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun drop(event: org.bukkit.event.player.PlayerDropItemEvent) { if (stomachs.isArena(event.player.world)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun explode(event: org.bukkit.event.entity.EntityExplodeEvent) { if (stomachs.isArena(event.location.world)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun chunk(event: org.bukkit.event.world.ChunkUnloadEvent) { stomachs.releaseChunk(event.chunk) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun world(event: org.bukkit.event.world.WorldUnloadEvent) { stomachs.releaseWorld(event.world) }
    @EventHandler fun changedWorld(event: org.bukkit.event.player.PlayerChangedWorldEvent) {
        if (!stomachs.transferring && stomachs.contains(event.player.uniqueId)) stomachs.release(event.player, Stomachs.ReleaseReason.WORLD_CHANGE)
    }
    @EventHandler fun quit(event: PlayerQuitEvent) { stomachs.release(event.player, Stomachs.ReleaseReason.QUIT) }
}
