package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Interaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit

class PlantFeedbackTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    @org.junit.jupiter.api.BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun authorizedHealthDamageRecoilsAndEmitsSapThenResetsWithoutRespawn() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 1.0, 64.0, 1.0))
        plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5))
        server.scheduler.performTicks(30)
        player.gameMode = GameMode.CREATIVE
        server.scheduler.performTicks(5)
        val parts = world.entities.filterIsInstance<ItemDisplay>().filter {
            it.itemStack.itemMeta.itemModel?.key?.substringAfterLast('/') in setOf("body", "upperjaw", "lowerjaw", "leaf")
        }
        val before = parts.map { it.transformation }
        val mouth = world.entities.filterIsInstance<Interaction>().single()
        val particleCount = world.spawnedParticles.size
        plugin.encounters.attack(player, mouth.uniqueId)
        assertEquals(390.0, plugin.encounters.healthAt(player.location))
        assertTrue(parts.indices.any { before[it] != parts[it].transformation }, "real health damage must visibly recoil immediately")
        assertTrue(world.spawnedParticles.size > particleCount, "localized sap, not text only")
        server.scheduler.performTicks(120)
        val rested = parts.map { it.transformation }
        server.scheduler.performTicks(160)
        assertEquals(rested, parts.map { it.transformation })
        assertEquals(parts.map { it.uniqueId }, world.entities.filterIsInstance<ItemDisplay>().filter {
            it.itemStack.itemMeta.itemModel?.key?.substringAfterLast('/') in setOf("body", "upperjaw", "lowerjaw", "leaf")
        }.map { it.uniqueId })
    }
}
