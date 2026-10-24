package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor

/** Bounded loaded-terrain route, no block writes, chunk loads, teleports, or independent tasks. */
internal object VineTerrain {
    private fun loaded(at: Location) = at.world.isChunkLoaded(at.blockX shr 4, at.blockZ shr 4)
    fun clear(at: Location): Boolean {
        val box = BoundingBox(at.x - 0.32, at.y + 0.02, at.z - 0.32, at.x + 0.32, at.y + 1.85, at.z + 0.32)
        for (x in floor(box.minX).toInt()..floor(box.maxX).toInt())
            for (z in floor(box.minZ).toInt()..floor(box.maxZ).toInt()) {
                if (!at.world.isChunkLoaded(x shr 4, z shr 4)) return false
                for (y in floor(box.minY).toInt()..floor(box.maxY).toInt()) {
                    val block = at.world.getBlockAt(x, y, z)
                    if (block.isLiquid) return false
                    if (block.collisionShape.boundingBoxes.any { box.overlaps(it.clone().shift(x.toDouble(), y.toDouble(), z.toDouble())) }) return false
                }
            }
        return at.world.worldBorder.isInside(at)
    }
    fun surface(at: Location): Location? {
        if (!loaded(at)) return null
        val x = at.x - at.blockX; val z = at.z - at.blockZ
        for (y in at.blockY + 1 downTo at.blockY - 2) {
            val block = at.world.getBlockAt(at.blockX, y, at.blockZ)
            if (block.isLiquid || block.type in setOf(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE)) continue
            val top = block.collisionShape.boundingBoxes.filter { x >= it.minX && x <= it.maxX && z >= it.minZ && z <= it.maxZ }.maxOfOrNull { it.maxY } ?: continue
            val result = at.clone().apply { this.y = y + top }
            if (clear(result)) return result
        }
        return null
    }
    /** A step starts when the body's leading edge reaches it, not only its centre.
     * Consider supported heights under the footprint, then check the entire raised body.
     * The caller still limits every rise/drop to 0.6; full walls and low ceilings stay blocked.
     */
    fun routeSurface(at: Location): Location? {
        surface(at)?.let { return it }
        for (dx in listOf(-0.32, 0.0, 0.32)) for (dz in listOf(-0.32, 0.0, 0.32)) {
            val edge = surface(at.clone().add(dx, 0.0, dz)) ?: continue
            val raised = at.clone().apply { y = edge.y }
            if (clear(raised)) return raised
        }
        return null
    }

    fun route(root: Location, target: Location): List<Location>? {
        if (root.world != target.world || root.distanceSquared(target) > 26 * 26) return null
        val steps = ceil(root.distance(target) / 1.5).toInt().coerceIn(1, 18)
        val result = mutableListOf<Location>()
        var previous = root
        for (i in 0..steps) {
            val next = root.clone().add(target.toVector().subtract(root.toVector()).multiply(i.toDouble() / steps))
            next.y = previous.y
            val sample = routeSurface(next) ?: return null
            if (kotlin.math.abs(sample.y - previous.y) > 0.6) return null
            // Sweep every quarter block; coarse route points alone can tunnel through thin walls.
            val span = previous.distance(sample)
            val sweep = ceil(span / 0.25).toInt().coerceAtLeast(1)
            var swept = previous
            for (j in 1..sweep) {
                val probe = previous.clone().add(sample.toVector().subtract(previous.toVector()).multiply(j.toDouble() / sweep))
                probe.y = swept.y
                val ground = routeSurface(probe) ?: return null
                if (kotlin.math.abs(ground.y - swept.y) > 0.6) return null
                swept = ground
            }
            result += sample
            previous = sample
        }
        return result
    }
}

internal class TunnellingVine(private val view: GardenView, val target: java.util.UUID, route: List<Location>,
                              private val prepare: (Player) -> Boolean) {
    val entities = mutableListOf<Entity>()
    enum class Phase { TRACKING, LOCKED_WARNING, PULL, DONE }
    var phase = Phase.TRACKING
        private set
    private var route = route.map { it.clone() }
    private var lockedAt = 0
    val lockedEndpoint get() = if (phase == Phase.TRACKING) null else route.last().clone()
    private var index = 0
    private var age = 0
    private var caught = false
    private var erupted = false
    private var front: org.bukkit.entity.ItemDisplay? = null
    private val tipParts = mutableListOf<org.bukkit.entity.ItemDisplay>()
    private fun part(at: Location, name: String): Entity {
        val register: (Entity) -> Unit = { entities += it; view.entities += it }
        return if (view.textured) PlantModels.spawn(at, name, register) else PlantModels.owned(at, org.bukkit.entity.BlockDisplay::class.java, register) {
            it.isPersistent = false
            it.block = (if (name.startsWith("trail")) Material.MANGROVE_ROOTS else Material.GREEN_CONCRETE).createBlockData()
            it.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(0.25f, 0.5f, 0.25f), Quaternionf())
        }
    }
    /** Returns false on miss, timeout, changed terrain or invalid/disconnected/world-changing victim. */
    private var pulledPlayer: Player? = null
    private var pullWorld: org.bukkit.World? = null
    private var ownedPull: Vector? = null

    // Bounded ownership heuristic: horizontal friction may attenuate the last pull uniformly.
    // Preserve changed headings and stronger impulses, and always preserve vertical motion.
    // A same-heading external impulse no stronger than our pull is indistinguishable from decay.
    private fun releasePull() {
        val player = pulledPlayer
        val pull = ownedPull
        val world = pullWorld
        pulledPlayer = null; ownedPull = null; pullWorld = null
        if (player != null && pull != null && player.isOnline && player.world == world) {
            val current = player.velocity
            val horizontalSquared = pull.x * pull.x + pull.z * pull.z
            val scale = (current.x * pull.x + current.z * pull.z) / horizontalSquared
            val tolerance = 1.0e-9
            if (scale.isFinite() && scale >= 0.0 && scale <= 1.0 + tolerance &&
                kotlin.math.abs(current.x - scale * pull.x) <= tolerance &&
                kotlin.math.abs(current.z - scale * pull.z) <= tolerance) {
                player.velocity = current.setX(0.0).setZ(0.0)
            }
        }
    }

    fun tick(player: Player?): Boolean = try {
        tickOwned(player).also { if (!it) close() }
    } catch (failure: Exception) {
        close()
        throw failure
    }

    private fun tickOwned(player: Player?): Boolean {
        if (player == null || !player.isOnline || player.world != view.origin.world) return false
        age += 5
        if (age > 220) return false
        if (phase == Phase.TRACKING && age in 10..20 && index > 0) {
            val suffix = VineTerrain.route(route[index - 1], player.location)
            if (suffix != null && index + suffix.size - 1 <= 24) route = route.take(index) + suffix.drop(1)
        }
        if (index < route.size) {
            repeat(if (index == 0) 1 else 2) {
                if (index >= route.size) return@repeat
                val at = route[index]
                if (index > 0 && VineTerrain.route(route[index - 1], at) == null) return false
                if (VineTerrain.routeSurface(at)?.let { kotlin.math.abs(it.y - at.y) < 0.1 } != true) return false
                val trail = part(at.clone().add(0.0, 0.01, 0.0), "trail_segment")
                val direction = route.last().toVector().subtract(route.first().toVector())
                val yaw = kotlin.math.atan2(direction.x, direction.z).toFloat()
                if (trail is org.bukkit.entity.ItemDisplay) trail.transformation = Transformation(Vector3f(), Quaternionf().rotateY(yaw), Vector3f(2f), Quaternionf())
                if (view.textured) {
                    val nose = front ?: (part(at, "trail_front") as org.bukkit.entity.ItemDisplay).also { front = it }
                    nose.teleport(at.clone().add(0.0, 0.01, 0.0))
                    nose.transformation = Transformation(Vector3f(), Quaternionf().rotateY(yaw + Math.PI.toFloat()), Vector3f(2f), Quaternionf())
                }
                index++
            }
            if (index < route.size) return true
        }
        val endpoint = route.last()
        if (!erupted) {
            erupted = true
            phase = Phase.LOCKED_WARNING
            lockedAt = age
            for (i in 0..2) {
                val part = part(endpoint.clone().add(0.0, i * 1.275, 0.0), if (i == 2) "vine_tip" else "vine_segment")
                if (part is org.bukkit.entity.ItemDisplay) {
                    part.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(2f), Quaternionf())
                    tipParts += part
                }
            }
            endpoint.world.spawnParticle(org.bukkit.Particle.COMPOSTER, endpoint, 18, 0.7, 0.3, 0.7, 0.02)
            endpoint.world.playSound(endpoint, "block.roots.break", 1f, 0.6f)
            return true
        }
        if (age < lockedAt + 20) return true
        if (!caught) {
            if (player.location.distanceSquared(endpoint) > 2.25 || !prepare(player)) return false
            caught = true
            phase = Phase.PULL
        }
        if (player.location.distanceSquared(view.origin) <= 36) return false
        val delta = route.first().toVector().subtract(player.location.toVector()).setY(0)
        if (delta.lengthSquared() < 0.01) return false
        val step = delta.normalize().multiply(0.35)
        // Sweep the whole five-tick movement horizon; never force motion through new obstacles.
        for (i in 1..5) {
            val projected = player.location.clone().add(step.clone().multiply(i.toDouble()))
            val ground = VineTerrain.surface(projected) ?: return false
            if (kotlin.math.abs(ground.y - projected.y) > 0.6 || !VineTerrain.clear(projected)) return false
        }
        step.y = player.velocity.y
        player.velocity = step
        pulledPlayer = player
        pullWorld = player.world
        ownedPull = step.clone()
        return true
    }
    fun close() {
        phase = Phase.DONE
        releasePull()
        entities.forEach { it.remove() }
        view.entities.removeAll(entities.toSet())
        entities.clear()
    }
}
