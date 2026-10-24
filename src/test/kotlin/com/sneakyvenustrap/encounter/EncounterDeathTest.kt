package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class EncounterDeathTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun fortyVulnerableHitsKillEntireGardenAndItDoesNotRespawn() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        val origin = Location(world, 0.5, 64.0, 0.5)
        player.teleport(origin)
        assertTrue(plugin.encounters.spawn(origin))
        val mouth = world.entities.filterIsInstance<Interaction>().first()
        repeat(40) { hit ->
            // Contact exposes the only main head. Cancel the following recovery/attack
            // while striking, and wait out that window before admitting another target.
            player.gameMode = GameMode.CREATIVE
            server.scheduler.performTicks(50)
            player.health = 20.0
            player.gameMode = GameMode.SURVIVAL
            var waited = 0
            while (!plugin.encounters.exposedAt(origin) && waited++ < 30) server.scheduler.performTicks(5)
            player.gameMode = GameMode.CREATIVE
            server.scheduler.performTicks(5)
            assertTrue(plugin.encounters.exposedAt(origin))
            assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
            if (hit < 39) assertEquals(400.0 - (hit + 1) * 10.0, plugin.encounters.healthAt(origin))
        }
        assertEquals(0, plugin.encounters.size)
        assertTrue(world.entities.filterNot { it is Player }.isEmpty(), "all main-head displays and hitboxes removed")
        server.scheduler.performTicks(500)
        assertEquals(0, plugin.encounters.size, "only a developer can respawn the encounter")
        assertTrue(world.entities.filterNot { it is Player }.isEmpty())
    }
}
