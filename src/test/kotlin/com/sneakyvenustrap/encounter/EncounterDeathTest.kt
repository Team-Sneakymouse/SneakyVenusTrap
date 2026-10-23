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
            // Audible target selection happens before eruption; creative mode prevents
            // damage/swallow during this combat-only test without bypassing attacks.
            player.gameMode = GameMode.SURVIVAL
            server.scheduler.performTicks(if (hit == 0) 100 else 80)
            player.gameMode = GameMode.CREATIVE
            server.scheduler.performTicks(20)
            assertTrue(plugin.encounters.exposedAt(origin))
            assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
            if (hit < 39) assertEquals(400.0 - (hit + 1) * 10.0, plugin.encounters.healthAt(origin))
        }
        assertEquals(0, plugin.encounters.size)
        assertTrue(world.entities.filterNot { it is Player }.isEmpty(), "all main/bud displays and hitboxes removed")
        server.scheduler.performTicks(500)
        assertEquals(0, plugin.encounters.size, "only a developer can respawn the encounter")
        assertTrue(world.entities.filterNot { it is Player }.isEmpty())
    }
}
