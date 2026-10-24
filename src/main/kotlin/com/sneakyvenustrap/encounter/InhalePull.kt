package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/** Short, escapable horizontal inhale lease; never teleports or alters vertical motion. */
internal class InhalePull {
    private var player: Player? = null
    private var world: org.bukkit.World? = null
    private var owned: Vector? = null

    fun apply(target: Player, toward: Location): Boolean {
        close()
        if (!target.isOnline || target.isDead || target.world != toward.world) return false
        val step = toward.toVector().subtract(target.location.toVector()).setY(0.0)
        if (step.lengthSquared() < 0.04) return false
        step.normalize().multiply(0.08)
        for (i in 1..5) {
            val at = target.location.clone().add(step.clone().multiply(i.toDouble()))
            val floor = VineTerrain.surface(at) ?: return false
            if (kotlin.math.abs(floor.y - at.y) > 0.6 || !VineTerrain.clear(at)) return false
        }
        step.y = target.velocity.y
        target.velocity = step
        player = target; world = target.world; owned = step.clone()
        return true
    }

    fun close() {
        val target = player
        val motion = owned
        val from = world
        player = null; world = null; owned = null
        if (target == null || motion == null || !target.isOnline || target.world != from) return
        val current = target.velocity
        val scale = (current.x * motion.x + current.z * motion.z) / (motion.x * motion.x + motion.z * motion.z)
        // Same bounded friction attribution as the vine; independent headings/stronger impulses survive.
        if (scale.isFinite() && scale >= 0 && scale <= 1.0 + 1e-9 &&
            kotlin.math.abs(current.x - scale * motion.x) <= 1e-9 && kotlin.math.abs(current.z - scale * motion.z) <= 1e-9) {
            target.velocity = current.setX(0.0).setZ(0.0)
        }
    }
}
