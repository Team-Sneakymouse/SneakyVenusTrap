package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class VineSafetyTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun removalReleasesOnlyOwnedHorizontalPull() {
        val server = MockBukkit.mock()
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 14.0, 64.0, 2.0))
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        view.build()
        val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
        val vine = TunnellingVine(view, player.uniqueId, route) { true }
        repeat(route.size + 5) { assertTrue(vine.tick(player)) }
        assertTrue(player.velocity.lengthSquared() > 0.0)
        player.velocity = player.velocity.setY(0.42)
        vine.close()
        assertEquals(Vector(0.0, 0.42, 0.0), player.velocity)
        view.close()
        assertEquals(listOf(player), world.entities)
    }

    @Test fun removalPreservesSubsequentKnockback() {
        val server = MockBukkit.mock()
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 14.0, 64.0, 2.0))
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
        val vine = TunnellingVine(view, player.uniqueId, route) { true }
        repeat(route.size + 5) { assertTrue(vine.tick(player)) }
        val knockback = Vector(0.8, 0.4, 0.2)
        player.velocity = knockback
        vine.close()
        assertEquals(knockback, player.velocity)
        assertEquals(listOf(player), world.entities)
    }

    @Test fun removalRecognizesUniformDiagonalDecayButPreservesIndependentMotion() {
        val server = MockBukkit.mock()
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        // Both components are nonzero: attenuation must use one common ratio, not
        // merely accept any independently smaller x/z components.
        val cases = listOf(
            Triple(0.6, 0.6, true),
            Triple(1.0e-12, 1.0e-12, true),
            Triple(0.0, 0.0, true),
            Triple(0.6, 0.3, false),
            Triple(1.4, 1.4, false),
            Triple(-0.6, -0.6, false)
        )
        for ((xRatio, zRatio, released) in cases) {
            player.teleport(Location(world, 14.0, 64.0, 8.0))
            val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
            val vine = TunnellingVine(view, player.uniqueId, route) { true }
            repeat(route.size + 5) { assertTrue(vine.tick(player)) }
            val pull = player.velocity
            assertTrue(kotlin.math.abs(pull.x) > 0.01 && kotlin.math.abs(pull.z) > 0.01)
            val current = Vector(pull.x * xRatio, 0.42, pull.z * zRatio)
            player.velocity = current
            vine.close()
            assertEquals(if (released) Vector(0.0, 0.42, 0.0) else current, player.velocity,
                "horizontal ratios $xRatio / $zRatio")
            assertEquals(listOf(player), world.entities)
        }
        view.close()
    }

    @Test fun pullCancelsBeforeUnsupportedGroundWithoutTeleportOrSwallow() {
        val server = MockBukkit.mock()
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 14.0, 64.0, 2.0))
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        view.build()
        val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
        val vine = TunnellingVine(view, player.uniqueId, route) { true }
        repeat(route.size + 5) { assertTrue(vine.tick(player)) }
        assertTrue(player.velocity.lengthSquared() > 0.0, "regression must cancel an actual pull")
        // Simulate ordinary horizontal friction between five-tick vine updates, not a manual stop.
        player.velocity = player.velocity.multiply(0.6).setY(-0.18)
        for (x in 11..13) for (z in 1..2) world.getBlockAt(x, 63, z).type = Material.AIR
        val before = player.location
        assertFalse(vine.tick(player), "never drag across an unsupported gap")
        assertEquals(Vector(0.0, -0.18, 0.0), player.velocity)
        assertEquals(before, player.location)
        vine.close(); view.close()
        assertEquals(listOf(player), world.entities)
    }
}
