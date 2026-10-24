package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.service.Garden
import com.sneakyvenustrap.service.FlytrapGeometry
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/** All visual and hitbox entities are transient and explicitly owned by one encounter. */
class GardenView(val origin: Location, val textured: Boolean = false) {
    private var model: TexturedPlant? = null
    private var recoilTicks = 0
    private var recoilStrength = 0f
    private var lastAim: Location? = null
    private var lastPhase = com.sneakyvenustrap.service.PlantAttack.Phase.IDLE
    private var lastElapsed = 0
    private var lastSwallowing = false

    fun hurt(vulnerable: Boolean) {
        recoilTicks = maxOf(recoilTicks, if (vulnerable) 40 else 20)
        recoilStrength = maxOf(recoilStrength, if (vulnerable) 1.5f else 0.5f)
        origin.world.spawnParticle(org.bukkit.Particle.DUST, mouths.first().location.clone().add(0.0, 1.0, 0.0),
            if (vulnerable) 18 else 8, 0.45, 0.4, 0.45, 0.0,
            org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(119, 169, 36), 1.3f))
        pose(lastAim, lastPhase, lastElapsed, lastSwallowing)
    }
    fun tickFeedback() {
        if (recoilTicks == 0) return
        recoilTicks = (recoilTicks - 5).coerceAtLeast(0)
        if (recoilTicks == 0) recoilStrength = 0f
        pose(lastAim, lastPhase, lastElapsed, lastSwallowing)
    }
    val entities = mutableListOf<Entity>()
    val mouths = mutableListOf<Interaction>()
    val health = Garden(400.0)

    fun build() {
        try {
            if (textured) {
                val plant = TexturedPlant(origin) { entities += it }
                model = plant
                rest = plant.rest
                mouths += hitbox(plant.rest.clone().add(0.0, -1.2, 0.0), 4.2f, 2.4f)
                return
            }
            block(0.0, 0.0, 0.0, Material.MANGROVE_ROOTS, 3f, 3f, 3f)
            block(0.9, 1.5, 1.2, Material.GREEN_CONCRETE, 1.2f, 3.7f, 1.2f)
            block(1.1, 3.8, 1.5, Material.GREEN_TERRACOTTA, 0.9f, 2.0f, 1.2f)
            // Broad layered leaves and spreading roots anchor the head rather than a UFO pedestal.
            for (side in listOf(-1, 1)) {
                for (step in 0..3) {
                    val x = 1.5 + side * (0.9 + step * 0.65)
                    block(x - 0.6, 2.4 - step * 0.26, 0.8 + step * 0.16,
                        Material.GREEN_CONCRETE, 1.2f, 0.24f, (1.6 - step * 0.25).toFloat())
                    block(x - 0.35, 0.15, 1.1, Material.MANGROVE_ROOTS, 0.7f, 0.4f, 1.0f)
                }
            }
            jaw(origin.clone().add(1.5, 6.1, 1.5), 2.4)
            mouths += hitbox(origin.clone().add(1.5, 3.3, 1.0), 5.2f, 5.8f)
        } catch (failure: Exception) { close(); throw failure }
    }

    private data class HeadPart(val display: BlockDisplay, val local: Vector3f, val scale: Vector3f, val upper: Boolean)
    private val head = mutableListOf<HeadPart>()
    private var rest: Location? = null

    fun jaw(at: Location, radius: Double) {
        rest = at.clone()
        for (piece in FlytrapGeometry.head(radius)) {
            block(at.x - origin.x + piece.x, at.y - origin.y + piece.y,
                at.z - origin.z + piece.z, Material.valueOf(piece.material),
                piece.sx.toFloat(), piece.sy.toFloat(), piece.sz.toFloat())
            head += HeadPart(entities.last() as BlockDisplay,
                Vector3f(piece.x.toFloat(), piece.y.toFloat(), piece.z.toFloat()),
                Vector3f(piece.sx.toFloat(), piece.sy.toFloat(), piece.sz.toFloat()), piece.y >= 0)
        }
    }

    fun pose(aim: Location?, phase: com.sneakyvenustrap.service.PlantAttack.Phase, elapsed: Int, swallowing: Boolean = false) {
        lastAim = aim?.clone(); lastPhase = phase; lastElapsed = elapsed; lastSwallowing = swallowing
        val center = rest ?: return
        val recoil = recoilStrength * (recoilTicks / 40f) * if (recoilTicks % 10 == 0) 1f else -0.6f
        val idle = phase == com.sneakyvenustrap.service.PlantAttack.Phase.IDLE
        val windup = phase == com.sneakyvenustrap.service.PlantAttack.Phase.WINDUP
        val duration = if (swallowing) 50f else 30f
        val progress = when {
            idle -> 0f
            windup -> (elapsed / duration) * 0.35f
            else -> (1f - (elapsed - duration) / 50f).coerceIn(0f, 1f)
        }
        val delta = if (aim == null) Vector3f() else Vector3f(
            (aim.x - center.x).toFloat(), (aim.y - center.y).toFloat(), (aim.z - center.z).toFloat()).mul(progress)
        val yaw = if (aim == null || idle) 0f else kotlin.math.atan2(-(aim.x - center.x), -(aim.z - center.z)).toFloat()
        model?.pose(delta, yaw, if (windup) 26f + 12f * progress else 26f - 18f * progress,
            if (windup) -22f - 12f * progress else -22f + 14f * progress, recoil)
        for (part in head) {
            val jaw = if (windup) (elapsed / duration) * 0.20f else if (idle) 0f else -0.65f * progress
            val rotation = Quaternionf().rotateY(yaw).rotateX(if (part.upper) jaw else -jaw).rotateZ(recoil * 0.15f)
            val local = rotation.transform(Vector3f(part.local))
            part.display.interpolationDuration = 5
            part.display.interpolationDelay = 0
            part.display.transformation = Transformation(local.add(delta).sub(part.local), rotation, Vector3f(part.scale), Quaternionf())
        }
        mouths.firstOrNull()?.teleport(center.clone().add(delta.x.toDouble(), delta.y.toDouble() - (if (textured) 1.2 else 2.8), delta.z.toDouble()))
    }

    fun hitbox(at: Location, width: Float, height: Float): Interaction =
        at.world.spawn(at, Interaction::class.java).also {
            entities += it
            it.isPersistent = false
            it.interactionWidth = width
            it.interactionHeight = height
        }

    fun block(x: Double, y: Double, z: Double, material: Material, sx: Float, sy: Float, sz: Float) {
        origin.world.spawn(origin.clone().add(x, y, z), BlockDisplay::class.java).also {
            entities += it
            it.isPersistent = false
            it.block = material.createBlockData()
            it.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(sx, sy, sz), Quaternionf())
        }
    }

    fun close() { entities.forEach { it.remove() }; entities.clear(); mouths.clear(); head.clear() }
}
