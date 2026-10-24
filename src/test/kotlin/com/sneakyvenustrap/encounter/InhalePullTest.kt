package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.mockbukkit.mockbukkit.MockBukkit

class InhalePullTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    @org.junit.jupiter.api.BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun modestPullSweepsGroundAndReleasesAttenuatedMotionWithoutRemovingVerticalMotion() {
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 5.0, 64.0, 2.0))
        player.velocity = Vector(0.0, 0.42, 0.0)
        val pull = InhalePull()
        assertTrue(pull.apply(player, Location(world, 2.0, 64.0, 2.0)))
        assertEquals(-0.08, player.velocity.x, 0.0001)
        assertEquals(0.42, player.velocity.y)
        player.velocity = player.velocity.multiply(0.6).setY(0.2)
        pull.close()
        assertEquals(Vector(0.0, 0.2, 0.0), player.velocity)
        world.getBlockAt(4, 64, 2).type = Material.STONE
        assertFalse(pull.apply(player, Location(world, 2.0, 64.0, 2.0)))
        assertEquals(Vector(0.0, 0.2, 0.0), player.velocity)
        world.getBlockAt(4, 64, 2).type = Material.AIR
        assertTrue(pull.apply(player, Location(world, 2.0, 64.0, 2.0)))
        val knockback = Vector(0.6, 0.4, -0.2)
        player.velocity = knockback
        pull.close()
        assertEquals(knockback, player.velocity)
    }
}
