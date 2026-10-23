package com.sneakyvenustrap.encounter

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.event.world.ChunkUnloadEvent

class EncounterEvents(private val encounters: Encounters) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun interact(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
        if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND && encounters.interact(event.player, event.rightClicked.uniqueId)) event.isCancelled = true
    }
    // Interaction.skipAttackInteraction fires the native damage event BEFORE PrePlayerAttackEntityEvent.
    // Its default response=false then makes PrePlayerAttackEntityEvent permanently cancelled.
    // Observe both without changing either; authorize only after their complete dispatch.
    private val pending = mutableMapOf<Pair<java.util.UUID, java.util.UUID>, Pending>()
    private class Pending {
        var pre: PrePlayerAttackEntityEvent? = null
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun damage(event: org.bukkit.event.entity.EntityDamageByEntityEvent) {
        if (event is EncounterAttackAuthorizationEvent || event.entity !is org.bukkit.entity.Interaction) return
        val player = event.damager as? org.bukkit.entity.Player ?: return
        val key = player.uniqueId to event.entity.uniqueId
        val attempt = Pending()
        pending[key] = attempt
        encounters.afterAttack {
            if (pending[key] === attempt) pending.remove(key)
            val pre = attempt.pre
            if (!event.isCancelled && event.finalDamage > 0 &&
                !(pre != null && pre.willAttack() && pre.isCancelled)) {
                encounters.attack(player, event.entity.uniqueId)
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    fun attack(event: PrePlayerAttackEntityEvent) {
        pending[event.player.uniqueId to event.attacked.uniqueId]?.pre = event
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun unload(event: WorldUnloadEvent) { encounters.unload(event.world) }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun chunk(event: ChunkUnloadEvent) { encounters.unload(event.chunk) }
}
