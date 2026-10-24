package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class VineFlowTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun distantTrailStartsAtRootsProgressesThenPullsWithoutAnotherMouth() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = TerrainWorld().also { it.name = "Darkvale"; server.addWorld(it) }
        for (x in -2..17) for (z in -2..4) world.getBlockAt(x, 63, z).type = Material.STONE
        world.getChunkAt(0, 0).load(); world.getChunkAt(1, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 14.0, 64.0, 1.5))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        fun trail() = world.entities.filterIsInstance<ItemDisplay>().filter { it.itemStack.itemMeta.itemModel.toString().contains("trail_segment") }
        server.scheduler.performTicks(5)
        assertEquals(1, trail().size)
        assertTrue(trail().first().location.x < 4, "trail must begin at roots, not target")
        server.scheduler.performTicks(15)
        assertTrue(trail().size > 1)
        assertTrue(trail().maxOf { it.location.x } < player.location.x)
        server.scheduler.performTicks(100)
        assertTrue(player.velocity.x < 0, "caught target receives a real bounded pull toward main head")
        assertFalse(plugin.encounters.stomachs.contains(player.uniqueId), "vine cannot swallow")
        assertEquals(1, world.entities.filterIsInstance<org.bukkit.entity.Interaction>().size)
        player.gameMode = org.bukkit.GameMode.CREATIVE
        server.scheduler.performTicks(5)
        assertTrue(trail().isEmpty())
        assertEquals(18, world.entities.filterIsInstance<ItemDisplay>().size)
        plugin.encounters.close()
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }
}
