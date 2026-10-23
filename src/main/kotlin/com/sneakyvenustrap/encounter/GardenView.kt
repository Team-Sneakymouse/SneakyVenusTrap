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
class GardenView(val origin: Location) {
    val entities = mutableListOf<Entity>()
    val mouths = mutableListOf<Interaction>()
    val health = Garden(400.0)

    fun build() {
        try {
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

    fun jaw(at: Location, radius: Double) {
        for (piece in FlytrapGeometry.head(radius)) {
            block(at.x - origin.x + piece.x, at.y - origin.y + piece.y,
                at.z - origin.z + piece.z, Material.valueOf(piece.material),
                piece.sx.toFloat(), piece.sy.toFloat(), piece.sz.toFloat())
        }
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

    fun close() { entities.forEach { it.remove() }; entities.clear(); mouths.clear() }
}
