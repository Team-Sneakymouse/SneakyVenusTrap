package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.service.Bribes
import com.sneakyvenustrap.service.Noise
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Random
import java.util.UUID

/** Single main-thread owner. At most eight gardens, one transient bud per garden, one bounded tick task. */
class Encounters(private val plugin: JavaPlugin) {
    private class Encounter(val view: GardenView, random: Random) {
        val id = UUID.randomUUID().toString()
        val bribes = Bribes(random, 1200, 300)
        var age = 0
        var target: UUID? = null
        var warning: Location? = null
        var bud: GardenView? = null
    }
    private val random = Random()
    private val gardens = mutableListOf<Encounter>()
    val stomachs = Stomachs(plugin)
    var microphoneActive: (UUID) -> Boolean = { false }
    private var now = 0L
    private val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 5, 5)
    val size get() = gardens.size

    fun spawn(at: Location): Boolean {
        if (stomachs.isArena(at.world) || gardens.size >= 8 || gardens.any { it.view.origin.world == at.world && it.view.origin.distanceSquared(at) < 1600 }) return false
        if (at.y < at.world.minHeight + 1 || at.y + 12 >= at.world.maxHeight) return false
        val view = GardenView(at.clone())
        view.build()
        gardens += Encounter(view, random)
        return true
    }
    private fun nearest(at: Location) = gardens.filter { it.view.origin.world == at.world && it.view.origin.distanceSquared(at) <= 400 }
        .minByOrNull { it.view.origin.distanceSquared(at) }
    fun exposedAt(at: Location) = nearest(at)?.view?.health?.exposedTicks?.let { it > 0 } ?: false
    fun removeNear(at: Location): Boolean {
        val found = nearest(at) ?: return false
        remove(found)
        return true
    }
    private fun remove(found: Encounter) {
        stomachs.releaseGarden(found.id)
        found.bud?.close()
        found.view.close()
        gardens.remove(found)
        paidGardens.remove(found.id)
    }
    private fun eligible(player: Player, garden: Encounter): Boolean =
        !player.isDead && player.gameMode != GameMode.SPECTATOR && player.gameMode != GameMode.CREATIVE &&
            !stomachs.contains(player.uniqueId) && !garden.bribes.truce(player.uniqueId, now) &&
            player.location.distanceSquared(garden.view.origin) <= 24 * 24

    private fun tick() {
        now += 5
        lastAttack.entries.removeIf { now - it.value >= 10 }
        if (now % 20 == 0L) stomachs.tick()
        for (garden in gardens.toList()) {
            garden.age += 5
            repeat(5) { garden.view.health.tick() }
            // Do not load production chunks for a dormant encounter. Unload removes it instead.
            if (!garden.view.origin.isChunkLoaded) { remove(garden); continue }
            val players = garden.view.origin.world.players.filter { eligible(it, garden) }
            if (garden.age % 100 == 0) {
                garden.bud?.close(); garden.bud = null
                val target = players.maxByOrNull {
                    Noise.score(it.isSprinting, it.isSneaking, microphoneActive(it.uniqueId)) / (1 + it.location.distance(garden.view.origin))
                }
                garden.target = target?.uniqueId
                garden.warning = target?.location?.clone()
                if (target != null) {
                    target.sendMessage("The roots hear you. The ground begins to split — MOVE!")
                    garden.warning!!.world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, garden.warning!!, 25, 0.7, 0.2, 0.7, 0.0)
                    sound(garden.warning!!, "warning", "entity.warden.tendril_clicks", 0.7f)
                }
            }
            if (garden.age % 100 == 20 && garden.warning != null) {
                val at = garden.warning!!
                val bud = GardenView(at.clone())
                bud.jaw(at.clone(), 0.8)
                bud.mouths += bud.hitbox(at, 2.5f, 2.5f)
                garden.bud = bud
                garden.view.health.expose(45)
                sound(at, "bite", "entity.ravager.roar", 0.6f)
                val target = plugin.server.getPlayer(garden.target!!)
                if (target != null && target.world == at.world && target.location.distanceSquared(at) < 6.25 && eligible(target, garden)) {
                    // A failed optional arena never strands players; the bite still works.
                    if (random.nextInt(4) != 0 || !stomachs.swallow(target, garden.id)) target.damage(if (target.isSprinting) 6.0 else 3.0)
                }
            }
            if (now % 20 == 0L) players.forEach {
                it.sendActionBar(net.kyori.adventure.text.Component.text("The Mother • ${garden.view.health.health.toInt()}/${garden.view.health.maximumHealth.toInt()}" +
                    if (garden.view.health.exposedTicks > 0) " • THROAT EXPOSED!" else " • Armoured roots"))
            }
        }
    }
    private fun sound(at: Location, key: String, fallback: String, pitch: Float) {
        at.world.playSound(at, plugin.config.getString("sounds.$key", fallback)!!, 1.3f, pitch)
    }
    private val lures = Lures(plugin, random)
    private val paidGardens = mutableSetOf<String>()
    fun interact(player: Player, entity: UUID): Boolean {
        val garden = gardens.firstOrNull { g -> (g.view.mouths + (g.bud?.mouths ?: emptyList())).any { it.uniqueId == entity } } ?: return false
        val mouth = (garden.view.mouths + (garden.bud?.mouths ?: emptyList())).first { it.uniqueId == entity }
        if (player.world != mouth.world || player.location.distanceSquared(mouth.location) > 36 || player.isDead || stomachs.contains(player.uniqueId)) return true
        if (player.isSneaking) {
            val offered = plugin.config.getItemStack("bribe.item")
            val held = player.inventory.itemInMainHand
            if (offered == null || offered.type.isAir || !held.isSimilar(offered)) {
                player.sendMessage("She refuses that offering."); return true
            }
            val result = garden.bribes.offer(player.uniqueId, now)
            if (result == Bribes.Result.COOLDOWN) { player.sendMessage("She will not bargain again yet."); return true }
            held.amount -= 1
            player.sendMessage(if (result == Bribes.Result.TRUCE) "She accepts. Fifteen seconds of peace — not friendship." else "She eats the offering. And still wants YOU.")
        } else {
            val configured = plugin.config.getItemStack("lure.item")
            if (configured == null || configured.type.isAir) {
                player.sendMessage("There is nothing genuine in her bait today.")
            } else if (garden.id !in paidGardens) {
                val paid = try { lures.claim(player) } catch (failure: Exception) {
                    plugin.logger.severe("Lure payout failed closed: ${failure.message}"); false
                }
                if (paid) { paidGardens += garden.id; player.sendMessage("You snatch a real morsel from her jaws!") }
                else player.sendMessage("The bait slips away. She remembers your attempt.")
            }
        }
        return true
    }

    private val lastAttack = mutableMapOf<UUID, Long>()
    fun healthAt(at: Location) = nearest(at)?.view?.health?.health
    internal fun afterAttack(action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable { action() })
    }
    fun attack(player: Player, entity: UUID): Boolean {
        val target = plugin.server.getEntity(entity) ?: return false
        if (player.isDead || player.gameMode == GameMode.SPECTATOR || player.world != target.world ||
            player.location.distanceSquared(target.location) > 36) return true
        // Captives may strike their puzzle flowers, never the exterior shared-health garden.
        val isGarden = gardens.any { g -> (g.view.mouths + (g.bud?.mouths ?: emptyList())).any { it.uniqueId == entity } }
        if (stomachs.contains(player.uniqueId) && isGarden) return true
        if (!isGarden && !stomachs.contains(player.uniqueId)) return false
        val authorization = EncounterAttackAuthorizationEvent(player, target)
        plugin.server.pluginManager.callEvent(authorization)
        if (authorization.isCancelled || !authorization.finalDamage.isFinite() || authorization.finalDamage <= 0) return true
        if (stomachs.strike(player, entity)) return true
        val garden = gardens.firstOrNull { g -> (g.view.mouths + (g.bud?.mouths ?: emptyList())).any { it.uniqueId == entity } } ?: return false
        val mouth = (garden.view.mouths + (garden.bud?.mouths ?: emptyList())).first { it.uniqueId == entity }
        if (player.world != mouth.world || player.location.distanceSquared(mouth.location) > 36 ||
            now - (lastAttack[player.uniqueId] ?: -20) < 10) return true
        if (garden.view.health.hit(authorization.finalDamage)) {
            lastAttack[player.uniqueId] = now
            stomachs.help(garden.id)
            sound(mouth.location, "hurt", "entity.ghast.hurt", 0.6f)
            if (garden.view.health.dead) { sound(garden.view.origin, "death", "entity.ender_dragon.death", 0.5f); remove(garden) }
        }
        return true
    }

    fun unload(world: World) { gardens.filter { it.view.origin.world == world }.toList().forEach(::remove) }
    fun unload(chunk: Chunk) {
        gardens.filter { g -> (g.view.entities + (g.bud?.entities ?: emptyList())).any { it.world == chunk.world && it.location.blockX shr 4 == chunk.x && it.location.blockZ shr 4 == chunk.z } }
            .toList().forEach(::remove)
    }
    fun close() {
        task.cancel()
        gardens.toList().forEach(::remove)
        stomachs.close()
    }
}
