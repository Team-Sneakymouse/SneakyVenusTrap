package com.sneakyvenustrap.service

/** Bounded per-garden drama clock, advanced only by the encounter's five-tick task. */
class PlantDrama {
    private val bundles = IntArray(3)
    var raging = false
        private set
    var staggerTicks = 0
        private set
    fun tick(healthFraction: Double) {
        if (healthFraction <= 0.5) raging = true
        staggerTicks = (staggerTicks - 5).coerceAtLeast(0)
        bundles.indices.forEach { bundles[it] = (bundles[it] - 5).coerceAtLeast(0) }
    }
    fun bundleAvailable(slot: Int) = bundles[slot] == 0
    fun absorb(slot: Int, swallowing: Boolean): Boolean {
        if (swallowing || !bundleAvailable(slot) || staggerTicks > 0) return false
        bundles[slot] = 200
        staggerTicks = 60
        return true
    }
}
