package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.mockbukkit.mockbukkit.MockBukkit

class VinePursuitTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun acceleratesTracksEarlyThenKeepsFullStationaryWarningWithBoundedPieces() {
        val server = MockBukkit.mock()
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load(); world.getChunkAt(1, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 14.0, 64.0, 2.0))
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
        val vine = TunnellingVine(view, player.uniqueId, route) { true }
        fun trails() = vine.entities.filterIsInstance<ItemDisplay>().count { it.itemStack.itemMeta.itemModel.toString().endsWith("trail_segment") }
        vine.tick(player)
        assertEquals(1, trails())
        player.teleport(Location(world, 14.0, 64.0, 4.0))
        vine.tick(player)
        assertEquals(3, trails(), "after one readable sample advance two validated samples per update")
        repeat(20) {
            if (vine.phase == TunnellingVine.Phase.TRACKING) assertTrue(vine.tick(player))
        }
        assertEquals(TunnellingVine.Phase.LOCKED_WARNING, vine.phase)
        val endpoint = vine.lockedEndpoint!!
        assertEquals(4.0, endpoint.z, 0.01, "early target motion changes only untravelled suffix")
        player.teleport(endpoint.clone().add(0.0, 0.0, 3.0))
        repeat(3) {
            assertTrue(vine.tick(player))
            assertEquals(endpoint, vine.lockedEndpoint)
            assertEquals(0.0, player.velocity.lengthSquared())
        }
        assertFalse(vine.tick(player), "twenty ticks after lock a dodging target is missed")
        assertTrue(trails() <= 24)
        vine.close(); view.close()
    }
}
