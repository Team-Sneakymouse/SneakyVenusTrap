package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

/** Pack parts are ordinary item_model components, never CraftEngine give IDs. */
internal object PlantModels {
    fun <T : org.bukkit.entity.Entity> owned(at: Location, type: Class<T>, register: (T) -> Unit, configure: (T) -> Unit): T {
        val entity = at.world.spawn(at, type)
        try {
            register(entity)
            configure(entity)
            return entity
        } catch (failure: Exception) {
            entity.remove()
            throw failure
        }
    }

    fun spawn(at: Location, part: String, register: (ItemDisplay) -> Unit): ItemDisplay = owned(at, ItemDisplay::class.java, register) {
        it.isPersistent = false
        it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
        it.setItemStack(ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta.apply { itemModel = NamespacedKey("dev", "venus_trap/$part") }
        })
        it.interpolationDuration = 5
    }

    /** Model q is already centred by the client's NONE item context. Units here are blocks. */
    fun transform(offset: Vector3f, pivot: Vector3f, rotation: Quaternionf, yaw: Float = 0f, delta: Vector3f = Vector3f()): Transformation {
        val parent = Quaternionf().rotateY(yaw)
        val compensation = Vector3f(pivot).sub(rotation.transform(Vector3f(pivot)))
        val translation = parent.transform(Vector3f(offset).add(compensation)).add(delta)
        return Transformation(translation, parent.mul(Quaternionf(rotation)), Vector3f(2f), Quaternionf())
    }
}

internal class TexturedPlant(private val origin: Location, private val register: (ItemDisplay) -> Unit) {
    val parts = mutableListOf<ItemDisplay>()
    private val center = origin.clone().add(1.5, 0.01, 1.5)
    private val body = PlantModels.spawn(center, "body") { parts += it; register(it) }
    private val upper = PlantModels.spawn(center, "upperjaw") { parts += it; register(it) }
    private val lower = PlantModels.spawn(center, "lowerjaw") { parts += it; register(it) }
    private val leaves = List(6) { PlantModels.spawn(center, "leaf") { parts += it; register(it) } }
    val rest get() = center.clone().add(0.0, 3.01, 0.0)

    init { pose(Vector3f(), 0f, 26f, -22f, 0f) }
    fun pose(delta: Vector3f, yaw: Float, upperAngle: Float, lowerAngle: Float, recoil: Float) {
        val lift = Vector3f(0f, 1.76f, 0f)
        val sway = Quaternionf().rotateZ(recoil * 0.12f)
        body.transformation = PlantModels.transform(lift, Vector3f(0f, -1.76f, 0f), sway)
        val shift = Vector3f(delta).add(0f, recoil * 0.25f, recoil * 0.6f)
        for ((part, angle) in listOf(upper to upperAngle, lower to lowerAngle)) {
            part.interpolationDelay = 0
            part.transformation = PlantModels.transform(Vector3f(0f, 3.01f, 0f), Vector3f(0f, 0f, 0.625f),
                Quaternionf().rotateX(Math.toRadians(angle.toDouble()).toFloat()).rotateZ(recoil * 0.15f), yaw, shift)
        }
        leaves.forEachIndexed { i, part ->
            val side = if (i < 3) -1 else 1
            val angle = side * listOf(48, 80, 118)[i % 3] + recoil * 20
            part.transformation = PlantModels.transform(Vector3f(side * 0.25f, 1.135f, 0.125f), Vector3f(),
                Quaternionf().rotateZ(Math.toRadians(angle.toDouble()).toFloat()))
        }
    }
}
