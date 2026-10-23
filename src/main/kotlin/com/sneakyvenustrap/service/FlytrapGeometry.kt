package com.sneakyvenustrap.service

import kotlin.math.abs

/** Vanilla sculpture, facing -Z. Coordinates are box minima relative to the mouth centre. */
object FlytrapGeometry {
    data class Piece(val x: Double, val y: Double, val z: Double,
        val sx: Double, val sy: Double, val sz: Double, val material: String, val role: String)

    fun head(radius: Double): List<Piece> {
        require(radius.isFinite() && radius > 0)
        val pieces = mutableListOf<Piece>()
        fun box(x: Double, y: Double, z: Double, sx: Double, sy: Double, sz: Double,
                material: String, role: String) {
            pieces += Piece(x * radius, y * radius, z * radius,
                sx * radius, sy * radius, sz * radius, material, role)
        }
        // Longitudinal lobes converge at a rear hinge: not two horizontal circular rings.
        for (side in listOf(-1, 1)) {
            for (slice in 0..5) {
                val z = -0.95 + slice * 0.30
                val width = listOf(0.83, 1.0, 1.04, 0.94, 0.73, 0.48)[slice]
                val opening = 0.92 - slice * 0.12
                val shellY = if (side > 0) opening else -opening - 0.24
                box(-width, shellY, z, width * 2, 0.24, 0.34, "GREEN_CONCRETE", "shell")
                val fleshY = if (side > 0) opening - 0.10 else -opening
                box(-width + 0.09, fleshY, z - 0.015, width * 2 - 0.18, 0.10, 0.34,
                    "NETHER_WART_BLOCK", "flesh")
            }
            // Broad green lips, darker corners and interleaved ivory stepped fangs.
            for (tooth in 0..8) {
                val x = -0.78 + tooth * 0.19
                val lip = 0.90 - abs(x) * 0.20
                box(x - 0.12, if (side > 0) lip else -lip - 0.17, -1.02,
                    0.24, 0.17, 0.25, "LIME_TERRACOTTA", "shell")
                val length = if (tooth % 2 == 0) 0.46 else 0.34
                for (step in 0..2) {
                    val width = 0.14 - step * 0.045
                    val y = if (side > 0) lip - (step + 1) * length / 3
                        else -lip + step * length / 3
                    box(x - width / 2, y, -1.055 - step * 0.025,
                        width, length / 3 + 0.012, width, "BONE_BLOCK", "tooth")
                }
            }
        }
        box(-0.46, -0.43, 0.57, 0.92, 0.86, 0.20, "BLACK_CONCRETE", "throat")
        box(-0.34, -0.64, -0.65, 0.68, 0.17, 1.22, "CRIMSON_HYPHAE", "tongue")
        // Side cheeks join the halves at the back while leaving the front gaping.
        for (side in listOf(-1, 1)) {
            box(if (side < 0) -0.72 else 0.48, -0.48, 0.22,
                0.24, 0.96, 0.52, "GREEN_TERRACOTTA", "shell")
        }
        return pieces
    }
}
