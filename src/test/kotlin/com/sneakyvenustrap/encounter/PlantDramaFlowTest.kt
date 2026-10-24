package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.GameMode
import org.bukkit.entity.Display
import org.bukkit.entity.Interaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class PlantDramaFlowTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun ragePeelsExistingLeavesWithoutAddingEntitiesAndKeepsWarnings() {
        val world = server.addSimpleWorld("Darkvale")
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        view.build()
        view.buildBundles()
        val attack = com.sneakyvenustrap.service.PlantAttack()
        view.pose(null, attack)
        val leaves = view.entities.filterIsInstance<org.bukkit.entity.ItemDisplay>().filter { it.itemStack.itemMeta.itemModel.toString().endsWith("/leaf") }
        val before = leaves.map { it.transformation }
        val state = com.sneakyvenustrap.service.PlantDrama()
        state.tick(0.5)
        repeat(4) { view.drama(state, emptyList()) }
        assertTrue(leaves.indices.any { before[it] != leaves[it].transformation }, "rage must visibly peel existing leaves")
        assertEquals(19, view.entities.size)
        state.tick(1.0)
        assertTrue(state.raging)
        attack.start(true, state.raging)
        assertEquals(50, attack.windupTicks)
        assertEquals(35, attack.recoveryTicks)
        view.close()
        assertTrue(world.entities.isEmpty())
    }

    @Test fun lanesWarnApplyBoundedHitsKeepOpenEscapeAndReuseDisplays() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, -2.5, 64.0, 0.5))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        // Keep ordinary combat out of the setup; pruning is driven by the real encounter clock.
        player.gameMode = GameMode.CREATIVE
        server.scheduler.performTicks(235)
        player.gameMode = GameMode.SURVIVAL
        server.scheduler.performTicks(5)
        assertEquals(30, world.entities.filterIsInstance<Display>().size)
        assertEquals(1, world.entities.filterIsInstance<Interaction>().size)
        val ids = world.entities.filterIsInstance<Display>().map { it.uniqueId }
        server.scheduler.performTicks(15)
        assertEquals(20.0, player.health, "full warning is harmless")
        server.scheduler.performTicks(5)
        assertEquals(18.0, player.health)
        assertTrue(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS))
        server.scheduler.performTicks(15)
        assertEquals(18.0, player.health, "lane hit cooldown must prevent repeated five-tick damage")
        player.teleport(Location(world, 2.0, 64.0, 0.5))
        server.scheduler.performTicks(65)
        assertEquals(18.0, player.health, "central escape is safe and no inhale overlaps hazards")
        assertEquals(ids, world.entities.filterIsInstance<Display>().map { it.uniqueId })
        assertFalse(plugin.encounters.stomachs.contains(player.uniqueId))
        assertTrue(plugin.encounters.removeNear(player.location))
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }

    @Test fun baitAbsorbsBeforeDamageAndHoldsExposedContactForSixtyTicks() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, -1.5, 64.0, 2.5))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        server.scheduler.performTicks(30)
        assertEquals(20.0, player.health, "bundle must absorb before player damage")
        val mouth = world.entities.filterIsInstance<Interaction>().single()
        val contact = mouth.location.clone()
        val ids = world.entities.filterIsInstance<Display>().map { it.uniqueId }
        player.gameMode = GameMode.CREATIVE
        server.scheduler.performTicks(55)
        assertEquals(contact, mouth.location, "invalid target cannot interrupt bundle stagger")
        assertTrue(plugin.encounters.exposedAt(player.location))
        assertEquals(ids, world.entities.filterIsInstance<Display>().map { it.uniqueId })
        server.scheduler.performTicks(5)
        assertNotEquals(contact, mouth.location, "expired stagger releases the pose even if the target became invalid")
        plugin.encounters.close()
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }
}
