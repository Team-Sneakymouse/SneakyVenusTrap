package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.service.Bribes
import com.sneakyvenustrap.service.Noise
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Random
import java.util.UUID

/** Single main-thread owner. At most eight gardens, one main head each, one bounded tick task. */
class Encounters(private val plugin: JavaPlugin, val stomachs: Stomachs = Stomachs(plugin)) {
    private class Encounter(val view: GardenView, random: Random) {
        val id = UUID.randomUUID().toString()
        val bribes = Bribes(random, 1200, 300)
        var age = 0
        var target: UUID? = null
        var cycles = 0
        var swallowing = false
        val drama = com.sneakyvenustrap.service.PlantDrama()
        val inhale = InhalePull()
        val attack = com.sneakyvenustrap.service.PlantAttack()
        var aim: Location? = null
        var vine: TunnellingVine? = null
        var vineCooldown = 0L
        var anchor: Pair<UUID, Location>? = null
    }
    private val random = Random()
    private val gardens = mutableListOf<Encounter>()
    var microphoneActive: (UUID) -> Boolean = { false }
    private var now = 0L
    private val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 5, 5)
    val size get() = gardens.size

    fun spawn(at: Location): Boolean {
        if (stomachs.isArena(at.world) || gardens.size >= 8 || gardens.any { it.view.origin.world == at.world && it.view.origin.distanceSquared(at) < 1600 }) return false
        if (at.y < at.world.minHeight + 1 || at.y + 12 >= at.world.maxHeight) return false
        val view = GardenView(at.clone(), plugin.config.getString("visuals.mode", "textured") != "block")
        view.build()
        view.buildBundles()
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
        found.inhale.close()
        found.vine?.close()
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
            garden.view.tickFeedback()
            // Do not load production chunks for a dormant encounter. Unload removes it instead.
            if (!garden.view.origin.isChunkLoaded) { remove(garden); continue }
            val players = garden.view.origin.world.players.filter { eligible(it, garden) }
            val attack = garden.attack
            val drama = garden.drama
            drama.tick(garden.view.health.health / garden.view.health.maximumHealth)
            val wasHeld = garden.view.held
            garden.view.held = drama.staggerTicks > 0
            if (wasHeld && !garden.view.held) garden.view.pose(null, attack)
            if (drama.staggerTicks > 0) {
                garden.inhale.close()
                if (!renderDrama(garden, players)) continue
                garden.view.updateBar(players, false)
                continue
            }
            if (!renderDrama(garden, players)) continue
            val activeVine = garden.vine
            if (activeVine != null) {
                val player = players.firstOrNull { it.uniqueId == activeVine.target }
                if (!activeVine.tick(player)) {
                    activeVine.close()
                    garden.vine = null
                    garden.vineCooldown = now + 100
                    if (player == null || player.location.distanceSquared(garden.view.origin) > 49) garden.anchor = null
                }
            }
            if (garden.anchor != null && players.none { it.uniqueId == garden.anchor!!.first }) garden.anchor = null
            if (attack.phase == com.sneakyvenustrap.service.PlantAttack.Phase.IDLE && garden.vine == null) {
                val target = players.filter { it.location.distanceSquared(garden.view.origin) <= 49 }
                    .maxByOrNull { Noise.score(it.isSprinting, it.isSneaking, microphoneActive(it.uniqueId)) }
                if (target != null) {
                    garden.target = target.uniqueId
                    garden.aim = target.location.clone().add(0.0, 1.0, 0.0)
                    garden.swallowing = garden.cycles++ % 2 == 1
                    attack.start(garden.swallowing, drama.raging)
                    sound(target.location, "warning", "entity.warden.tendril_clicks", 0.7f)
                }
            }
            if (attack.phase == com.sneakyvenustrap.service.PlantAttack.Phase.IDLE && garden.vine == null && now >= garden.vineCooldown) {
                val distant = players.filter { it.location.distanceSquared(garden.view.origin) > 49 }
                    .maxByOrNull { Noise.score(it.isSprinting, it.isSneaking, microphoneActive(it.uniqueId)) }
                if (distant != null) {
                    garden.vineCooldown = now + 100
                    val route = VineTerrain.route(garden.view.origin.clone().add(1.5, 0.0, 1.5), distant.location)
                    if (route != null) {
                        garden.vine = TunnellingVine(garden.view, distant.uniqueId, route) { player ->
                            val safe = stomachs.prepareAnchor(player) ?: return@TunnellingVine false
                            garden.anchor = player.uniqueId to safe
                            true
                        }
                        garden.vine!!.tick(distant)
                    }
                }
            }
            val victim = garden.target?.let { plugin.server.getPlayer(it) }
            val valid = victim != null && victim.isOnline && victim.world == garden.view.origin.world && eligible(victim, garden)
            if (attack.phase != com.sneakyvenustrap.service.PlantAttack.Phase.IDLE) {
                if (attack.tracking && valid) garden.aim = victim.location.clone().add(0.0, 1.0, 0.0)
                val contact = attack.tick(valid)
                if (attack.inhaling && valid) {
                    if (garden.anchor?.first != victim.uniqueId) stomachs.prepareAnchor(victim)?.let { garden.anchor = victim.uniqueId to it }
                    if (garden.anchor?.first == victim.uniqueId) garden.inhale.apply(victim, garden.view.origin.clone().add(1.5, 0.0, 1.5))
                } else garden.inhale.close()
                garden.view.pose(garden.aim, attack)
                // The committed snap exposes her throat even when the victim dodges it.
                if (contact) garden.view.health.expose(45)
                val absorbed = contact && garden.view.absorbBundle(drama, garden.swallowing)
                if (absorbed) {
                    garden.inhale.close()
                    garden.view.health.expose(60)
                    garden.view.held = true
                    attack.reset()
                }
                if (contact && !absorbed && victim != null && garden.aim != null && victim.location.clone().add(0.0, 1.0, 0.0).distanceSquared(garden.view.contactCenter) <= 4) {
                    if (garden.swallowing) {
                        if (!stomachs.swallow(victim, garden.id, garden.anchor?.takeIf { it.first == victim.uniqueId }?.second)) {
                            plugin.logger.warning("Main-head swallow failed: garden=${garden.id}, player=${victim.uniqueId}; see stomach diagnostic.")
                        }
                    } else victim.damage(if (victim.isSprinting) 6.0 else 3.0)
                    sound(garden.aim!!, "bite", "entity.ravager.roar", 0.6f)
                }
            }
            if (attack.phase == com.sneakyvenustrap.service.PlantAttack.Phase.IDLE) garden.view.pose(null, attack)
            garden.view.updateBar(players, garden.swallowing && attack.phase == com.sneakyvenustrap.service.PlantAttack.Phase.WINDUP)
        }
    }
    private fun renderDrama(garden: Encounter, players: List<Player>): Boolean = try {
        garden.view.drama(garden.drama, players)
        true
    } catch (failure: Exception) {
        plugin.logger.warning("Exterior drama failed; removing garden=${garden.id}: ${failure.message}")
        remove(garden)
        false
    }

    private fun sound(at: Location, key: String, fallback: String, pitch: Float) {
        at.world.playSound(at, plugin.config.getString("sounds.$key", fallback)!!, 1.3f, pitch)
    }
    private val lures = Lures(plugin, random)
    private val paidGardens = mutableSetOf<String>()
    fun interact(player: Player, entity: UUID): Boolean {
        val garden = gardens.firstOrNull { g -> g.view.mouths.any { it.uniqueId == entity } } ?: return false
        val mouth = garden.view.mouths.first { it.uniqueId == entity }
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
        val isGarden = gardens.any { g -> g.view.mouths.any { it.uniqueId == entity } }
        if (stomachs.contains(player.uniqueId) && isGarden) return true
        if (!isGarden && !stomachs.contains(player.uniqueId)) return false
        val authorization = EncounterAttackAuthorizationEvent(player, target)
        plugin.server.pluginManager.callEvent(authorization)
        if (authorization.isCancelled || !authorization.finalDamage.isFinite() || authorization.finalDamage <= 0) return true
        if (stomachs.strike(player, entity)) return true
        val garden = gardens.firstOrNull { g -> g.view.mouths.any { it.uniqueId == entity } } ?: return false
        val mouth = garden.view.mouths.first { it.uniqueId == entity }
        if (player.world != mouth.world || player.location.distanceSquared(mouth.location) > 36 ||
            now - (lastAttack[player.uniqueId] ?: -20) < 10) return true
        if (garden.view.health.hit(authorization.finalDamage)) {
            lastAttack[player.uniqueId] = now
            stomachs.help(garden.id)
            garden.view.hurt(garden.view.health.exposedTicks > 0)
            sound(mouth.location, "hurt", "entity.ghast.hurt", 0.6f)
            if (garden.view.health.dead) { sound(garden.view.origin, "death", "entity.ender_dragon.death", 0.5f); remove(garden) }
        }
        return true
    }

    fun unload(world: World) { gardens.filter { it.view.origin.world == world }.toList().forEach(::remove) }
    fun unload(chunk: Chunk) {
        gardens.filter { g -> g.view.entities.any { it.world == chunk.world && it.location.blockX shr 4 == chunk.x && it.location.blockZ shr 4 == chunk.z } }
            .toList().forEach(::remove)
    }
    fun close() {
        task.cancel()
        gardens.toList().forEach(::remove)
        stomachs.close()
    }
}
