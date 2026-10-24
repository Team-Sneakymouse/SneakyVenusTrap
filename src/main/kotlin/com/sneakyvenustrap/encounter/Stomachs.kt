package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.service.Digestion
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Random
import java.util.UUID

/** Owns only a dedicated void world. Exterior worlds are NEVER written. Inventories are never replaced. */
class Stomachs(private val plugin: JavaPlugin, private val worldFactory: () -> World = { arenaWorld(plugin) }) {
    enum class ReleaseReason {
        MANUAL, EXTERNAL_TELEPORT, WORLD_CHANGE, UNEXPECTED_WORLD_TICK, CHUNK_UNLOAD, WORLD_UNLOAD,
        EXPIRY, ESCAPE, SHUTDOWN, GARDEN_REMOVE, CAPTURE_FAILURE, JOIN_RECOVERY, RESPAWN, QUIT
    }
    private data class Captive(val garden: String, val slot: Int, val returnTo: Location, val puzzle: Digestion,
                               val flowers: List<Interaction>, val entities: List<Entity>, val center: Location) {
        var cueGeneration = 0
        val capturedAt = System.nanoTime()
        val bar = Bukkit.createBossBar("Digestion 0/100 • knots 0/3", org.bukkit.boss.BarColor.PURPLE, org.bukkit.boss.BarStyle.SOLID)
    }
    private val captives = mutableMapOf<UUID, Captive>()
    private val transfer = StomachTransfer(plugin)
    private val recoveryFile = File(plugin.dataFolder, "captives.yml")
    private val recovery = YamlConfiguration.loadConfiguration(recoveryFile)
    private var arena: World? = null
    private val random = Random()
    var transferring = false
        private set

    fun contains(id: UUID) = id in captives
    fun isArena(world: World) = world.uid == arena?.uid || world.name == WORLD
    internal fun progressBar(id: UUID) = captives[id]?.bar
    fun originalFlower(id: UUID): UUID? = captives[id]?.let { it.flowers[it.puzzle.original].uniqueId }

    internal fun isRoomDestination(id: UUID, at: Location): Boolean {
        val center = captives[id]?.center ?: return false
        val player = plugin.server.getPlayer(id) ?: return false
        val halfWidth = player.width / 2
        // Room walls occupy the outer block columns; constrain the whole body, not its centre.
        return at.world.uid == center.world.uid &&
            kotlin.math.abs(at.x - center.x) + halfWidth < 3.5 &&
            kotlin.math.abs(at.z - center.z) + halfWidth < 3.5 &&
            at.y >= center.y && at.y + player.height < center.y + 4
    }

    internal fun observeTeleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
        if (!contains(event.player.uniqueId) && !isArena(event.from.world) && !isArena(event.to.world)) return
        fun at(location: Location) = "${location.world.name.take(64).replace(Regex("[^a-zA-Z0-9_.-]"), "_")}@${location.world.uid}:${location.blockX},${location.blockY},${location.blockZ}"
        transfer.diagnostic("player=${event.player.uniqueId} phase=teleport-observe cause=${event.cause.name} from=${at(event.from)} to=${at(event.to)} owned=$transferring cancelledAtMonitor=${event.isCancelled}",
            "${event.player.uniqueId}:teleport-observe:${event.cause.name}:$transferring:${event.isCancelled}")
    }

    /** Snapshot before exterior drag/lift. No world writes or recovery I/O until actual swallow. */
    fun prepareAnchor(player: Player): Location? = safe(player.location)

    fun swallow(player: Player, garden: String, anchor: Location? = null): Boolean {
        if (contains(player.uniqueId)) return transfer.decline(player, garden, "already-captive")
        if (isArena(player.world)) return transfer.decline(player, garden, "already-in-arena")
        if (player.isDead) return transfer.decline(player, garden, "dead")
        if (captives.size >= 16) return transfer.decline(player, garden, "arena-full")
        if (player.vehicle != null) return transfer.decline(player, garden, "mounted")
        if (player.passengers.any { it !is org.bukkit.entity.TextDisplay || it.passengers.isNotEmpty() })
            return transfer.decline(player, garden, "unsupported-passenger")
        val destination = safe(anchor?.takeIf { it.world == player.world } ?: player.location)
            ?: return transfer.decline(player, garden, "unsafe-anchor")
        val world = try { arena ?: worldFactory().also { arena = it } } catch (error: Exception) {
            plugin.logger.severe("Stomach unavailable; swallow cancelled: ${com.sneakyvenustrap.service.FailureDiagnostic.describe(error)}"); return transfer.decline(player, garden, "arena-unavailable")
        }
        val slot = (0..15).first { candidate -> captives.values.none { it.slot == candidate } }
        val center = Location(world, slot * 32.0 + 8.5, 65.0, 8.5)
        val flowers = mutableListOf<Interaction>()
        try {
            // Small sealed greenhouse: a genuine walkable room, isolated from all production blocks.
            for (x in -4..4) for (z in -4..4) for (y in -1..4) {
                val edge = x == -4 || x == 4 || z == -4 || z == 4
                center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block.type = when {
                    y == -1 -> Material.MOSS_BLOCK
                    y == 4 -> Material.RED_STAINED_GLASS
                    edge -> if (y == 0) Material.MUD_BRICKS else Material.GREEN_STAINED_GLASS
                    else -> Material.AIR
                }
            }
            for ((x, z) in listOf(-2 to -2, 2 to -2, 2 to 2, -2 to 2)) {
                val at = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                at.block.type = Material.WITHER_ROSE
                flowers += world.spawn(at.clone().add(0.0, -0.1, 0.0), Interaction::class.java).also {
                    it.isPersistent = false; it.interactionWidth = 1.2f; it.interactionHeight = 1.5f
                }
            }
            recovery.set(player.uniqueId.toString(), destination)
            saveRecovery() // Durable BEFORE teleport. A restart cannot strand a captive.
            val captive = Captive(garden, slot, destination, Digestion(random), flowers, flowers, center)
            captives[player.uniqueId] = captive
            transferring = true
            val moved = try { transfer.move(player, center, garden, "swallow") } finally { transferring = false }
            if (!moved) { release(player, ReleaseReason.CAPTURE_FAILURE); return false }
            captive.bar.addPlayer(player)
            updateBar(captive)
            player.sendMessage("Make Her Scream: remember the ORIGINAL cry. Strike its flower; echoes burn. Break three throat knots!")
            cue(player, captive)
            return true
        } catch (error: Exception) {
            // Teleport may already have succeeded. Keep ownership until evacuation succeeds.
            if (contains(player.uniqueId)) release(player, ReleaseReason.CAPTURE_FAILURE) else flowers.forEach { it.remove() }
            plugin.logger.severe("Swallow failed player=${player.uniqueId}; evacuation attempted, recovery retained if needed: ${com.sneakyvenustrap.service.FailureDiagnostic.describe(error)}")
            return false
        }
    }

    fun strike(player: Player, flower: UUID): Boolean {
        val captive = captives[player.uniqueId] ?: return false
        val index = captive.flowers.indexOfFirst { it.uniqueId == flower }
        if (index < 0) return false
        when (captive.puzzle.hit(index)) {
            Digestion.Hit.ECHO -> {
                player.playSound(player.location, "block.fire.extinguish", 0.6f, 0.6f)
                player.damage(2.0)
            }
            Digestion.Hit.KNOT -> { player.playSound(player.location, "block.rooted_dirt.break", 1f, 0.6f); cue(player, captive) }
            Digestion.Hit.ESCAPED -> { player.playSound(player.location, "entity.ghast.scream", 1f, 0.6f); release(player, ReleaseReason.ESCAPE) }
            else -> Unit
        }
        if (contains(player.uniqueId)) updateBar(captive)
        return true
    }

    private fun updateBar(captive: Captive) {
        captive.bar.setTitle("Digestion ${captive.puzzle.pressure}/100 • knots ${captive.puzzle.broken}/3")
        captive.bar.progress = (captive.puzzle.pressure / 100.0).coerceIn(0.0, 1.0)
        captive.bar.color = if (captive.puzzle.pressure >= 70) org.bukkit.boss.BarColor.RED else org.bukkit.boss.BarColor.PURPLE
    }

    private fun cue(player: Player, captive: Captive) {
        // The original is always played first; echoes use a different pitch. Also provide an accessible visual clue.
        val flower = captive.flowers[captive.puzzle.original]
        player.playSound(flower.location, plugin.config.getString("sounds.cry", "entity.ghast.scream")!!, 1f, 0.6f)
        player.spawnParticle(Particle.SOUL, flower.location.clone().add(0.0, 1.0, 0.0), 8, 0.1, 0.2, 0.1, 0.0)
        val generation = ++captive.cueGeneration
        captive.flowers.filter { it != flower }.forEachIndexed { index, echo ->
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (captives[player.uniqueId] !== captive || captive.cueGeneration != generation ||
                    player.isDead || player.world != captive.center.world) return@Runnable
                try {
                    player.playSound(echo.location, plugin.config.getString("sounds.echo", "entity.ghast.scream")!!, 0.4f, 1.5f)
                    player.spawnParticle(Particle.SMOKE, echo.location.clone().add(0.0, 1.0, 0.0), 5, 0.1, 0.2, 0.1, 0.0)
                } catch (error: Exception) {
                    plugin.logger.warning("stomach-cue player=${player.uniqueId} outcome=echo-failed exception=${com.sneakyvenustrap.service.FailureDiagnostic.describe(error)}")
                }
            }, (index + 1) * 16L)
        }
    }

    fun returnForRespawn(id: UUID): Location? = recovery.getLocation(id.toString())?.let(::safe)
    fun releaseWorld(world: World) {
        captives.filterValues { it.center.world.uid == world.uid || it.returnTo.world.uid == world.uid }.keys.toList()
            .forEach { plugin.server.getPlayer(it)?.let { player -> release(player, ReleaseReason.WORLD_UNLOAD) } }
    }
    fun releaseChunk(chunk: Chunk) {
        captives.filterValues { it.center.world.uid == chunk.world.uid && it.center.blockX shr 4 == chunk.x && it.center.blockZ shr 4 == chunk.z }
            .keys.toList().forEach { plugin.server.getPlayer(it)?.let { player -> release(player, ReleaseReason.CHUNK_UNLOAD) } }
    }

    fun release(player: Player, reason: ReleaseReason = ReleaseReason.MANUAL): Boolean {
        try {
            val captive = captives[player.uniqueId]
            captive?.bar?.removeAll()
            val saved = recovery.getLocation(player.uniqueId.toString()) ?: captive?.returnTo ?: return false
            val heldMillis = captive?.let { (System.nanoTime() - it.capturedAt) / 1_000_000 } ?: -1
            transfer.diagnostic("player=${player.uniqueId} phase=release-request reason=${reason.name} heldMillis=$heldMillis currentWorld=${player.world.uid} arenaWorld=${captive?.center?.world?.uid} slot=${captive?.slot ?: -1}",
                "${player.uniqueId}:release:${reason.name}")
            val destination = safe(saved) ?: plugin.server.worlds.filterNot { isArena(it) }.firstNotNullOfOrNull { safe(it.spawnLocation) }
            if (destination == null) { plugin.logger.severe("No safe release location for ${player.uniqueId}; recovery retained."); return false }
            transferring = true
            val moved = try { transfer.move(player, destination, captive?.garden ?: "recovery", "return", reason.name) } finally { transferring = false }
            if (!moved) return false
            player.fallDistance = 0f
            recovery.set(player.uniqueId.toString(), null)
            try { saveRecovery() } catch (error: Exception) {
                recovery.set(player.uniqueId.toString(), saved)
                throw error
            }
            captive?.entities?.forEach { it.remove() }
            captives.remove(player.uniqueId)
            return true
        } catch (error: Exception) {
            plugin.logger.severe("Release failed; recovery retained for retry: ${com.sneakyvenustrap.service.FailureDiagnostic.describe(error)}")
            return false
        }
    }

    private fun saveRecovery() {
        plugin.dataFolder.mkdirs()
        val staging = File(plugin.dataFolder, "captives.yml.new")
        recovery.save(staging)
        Files.move(staging.toPath(), recoveryFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun safe(at: Location): Location? {
        val world = at.world
        for (radius in 0..4) for (x in -radius..radius) for (z in -radius..radius) for (dy in listOf(0, -1, 1, -2, 2, -3, 3, -4, 4)) {
            val candidate = at.clone().add(x.toDouble(), dy.toDouble(), z.toDouble())
            val feet = candidate.block
            val floor = feet.getRelative(0, -1, 0)
            if (candidate.y > world.minHeight && candidate.y + 2 < world.maxHeight && feet.type.isAir &&
                feet.getRelative(0, 1, 0).type.isAir && floor.type.isSolid &&
                floor.type !in setOf(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE) &&
                world.worldBorder.isInside(candidate)) return candidate.apply { this.x = blockX + 0.5; this.z = blockZ + 0.5 }
        }
        return null
    }

    private var seconds = 0L
    private val lastHelp = mutableMapOf<String, Long>()
    fun tick() {
        seconds++
        for ((id, captive) in captives.toMap()) {
            val player = plugin.server.getPlayer(id) ?: continue
            if (!player.isOnline || player.isDead) { captive.bar.removeAll(); continue }
            if (player.world.uid != captive.center.world.uid) { release(player, ReleaseReason.UNEXPECTED_WORLD_TICK); continue }
            captive.puzzle.tick()
            if (player !in captive.bar.players) captive.bar.addPlayer(player)
            updateBar(captive)
            if (seconds % 8 == 0L) cue(player, captive)
            if (captive.puzzle.expired) {
                player.playSound(player.location, "entity.ravager.roar", 0.8f, 0.5f)
                release(player, ReleaseReason.EXPIRY)
                player.damage(6.0)
            }
        }
    }
    fun help(garden: String): Boolean {
        if (captives.values.none { it.garden == garden } || seconds - (lastHelp[garden] ?: -10) < 5) return false
        lastHelp[garden] = seconds
        captives.values.filter { it.garden == garden }.forEach { it.puzzle.help() }
        return true
    }
    fun releaseGarden(garden: String) {
        captives.filterValues { it.garden == garden }.keys.toList().forEach { plugin.server.getPlayer(it)?.let { player -> release(player, ReleaseReason.GARDEN_REMOVE) } }
        lastHelp.remove(garden)
    }

    fun close() {
        captives.values.forEach { it.bar.removeAll() }
        captives.keys.toList().forEach { plugin.server.getPlayer(it)?.let { player -> release(player, ReleaseReason.SHUTDOWN) } }
        // Failed/offline evacuations retain their room, ownership and durable journal.
        // A subsequent close or join recovery can retry without losing player state.
    }

    companion object {
        const val WORLD = "sneakyvenustrap_stomach"
        private fun arenaWorld(plugin: JavaPlugin): World {
            val folder = File(plugin.server.worldContainer, WORLD)
            val marker = File(folder, ".sneakyvenustrap-owned")
            require(!folder.exists() || marker.isFile) { "Refusing unowned world $WORLD; choose another world name manually before retrying." }
            folder.mkdirs()
            marker.writeText("Dedicated SneakyVenusTrap greenhouse arena; no player building.\n")
            return plugin.server.getWorld(WORLD) ?: WorldCreator(WORLD).generator(object : ChunkGenerator() {
                override fun shouldGenerateNoise() = false
                override fun shouldGenerateSurface() = false
                override fun shouldGenerateCaves() = false
                override fun shouldGenerateDecorations() = false
                override fun shouldGenerateMobs() = false
                override fun shouldGenerateStructures() = false
            }).generateStructures(false).createWorld() ?: error("Could not create stomach world")
        }
    }
}
