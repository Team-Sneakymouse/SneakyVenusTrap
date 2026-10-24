package com.sneakyvenustrap.service

/** Bounded per-garden drama clock, advanced only by the encounter's five-tick task. */
class PlantDrama {
    private val bundles = IntArray(3)
    private val hits = mutableMapOf<String, Int>()
    var raging = false
        private set
    var staggerTicks = 0
        private set
    private var lanes = 0
    val lanesVisible get() = lanes > 0
    val hazardActive get() = lanes in 1..80
    val trackedHits get() = hits.size
    fun tick(healthFraction: Double) {
        if (healthFraction <= 0.5) raging = true
        staggerTicks = (staggerTicks - 5).coerceAtLeast(0)
        bundles.indices.forEach { bundles[it] = (bundles[it] - 5).coerceAtLeast(0) }
        lanes = (lanes - 5).coerceAtLeast(0)
        hits.replaceAll { _, time -> time - 5 }
        hits.entries.removeIf { it.value <= 0 }
        if (!lanesVisible) hits.clear()
    }
    fun bundleAvailable(slot: Int) = bundles[slot] == 0
    fun absorb(slot: Int, swallowing: Boolean): Boolean {
        if (swallowing || !bundleAvailable(slot) || staggerTicks > 0) return false
        bundles[slot] = 200
        staggerTicks = 60
        return true
    }
    fun prune(): Boolean {
        if (lanesVisible || staggerTicks > 0) return false
        lanes = 100
        return true
    }
    fun hit(player: String): Boolean {
        if (!hazardActive || player in hits) return false
        hits[player] = 20
        return true
    }
}
