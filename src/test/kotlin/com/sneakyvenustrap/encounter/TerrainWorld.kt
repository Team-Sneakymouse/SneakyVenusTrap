package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.BoundingBox
import org.bukkit.util.VoxelShape
import org.mockbukkit.mockbukkit.block.BlockMock
import org.mockbukkit.mockbukkit.world.Coordinate
import org.mockbukkit.mockbukkit.world.WorldMock

/** Only missing MockBukkit collision shapes are supplied; all entity/runtime logic stays real. */
internal class TerrainWorld : WorldMock() {
    private val terrain = mutableMapOf<Coordinate, BlockMock>()
    override fun createBlock(coordinate: Coordinate): BlockMock = terrain.getOrPut(coordinate) { block(coordinate) }
    private fun block(coordinate: Coordinate): BlockMock = object : BlockMock(
        if (coordinate.y < 64) Material.STONE else Material.AIR,
        Location(this, coordinate.x.toDouble(), coordinate.y.toDouble(), coordinate.z.toDouble())) {
        override fun getCollisionShape(): VoxelShape {
            val boxes = when {
                type.isAir || isLiquid -> emptyList()
                type == Material.STONE_SLAB -> listOf(BoundingBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))
                type == Material.STONE_STAIRS -> listOf(BoundingBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0), BoundingBox(0.0, 0.5, 0.5, 1.0, 1.0, 1.0))
                else -> listOf(BoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
            }
            return object : VoxelShape {
                override fun getBoundingBoxes(): Collection<BoundingBox> = boxes
                override fun overlaps(other: BoundingBox) = boxes.any { it.overlaps(other) }
            }
        }
    }
}
