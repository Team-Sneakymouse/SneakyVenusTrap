package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Material
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class EncounterTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun configuredBribeConsumesExactlyOneItemAndCooldownCannotConsumeAgain() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        val player = server.addPlayer()
        player.teleport(Location(world, 0.5, 64.0, 0.5))
        player.isSneaking = true
        player.inventory.setItemInMainHand(org.bukkit.inventory.ItemStack(Material.DIAMOND, 3))
        assertTrue(plugin.encounters.spawn(player.location))
        val mouth = world.entities.filterIsInstance<org.bukkit.entity.Interaction>().first()
        assertTrue(plugin.encounters.interact(player, mouth.uniqueId))
        assertEquals(3, player.inventory.itemInMainHand.amount, "unconfigured bribes are disabled")
        plugin.config.set("bribe.item", org.bukkit.inventory.ItemStack(Material.DIAMOND))
        val interaction = org.bukkit.event.player.PlayerInteractEntityEvent(player, mouth, org.bukkit.inventory.EquipmentSlot.HAND)
        server.pluginManager.callEvent(interaction)
        assertTrue(interaction.isCancelled)
        assertEquals(2, player.inventory.itemInMainHand.amount)
        server.pluginManager.callEvent(org.bukkit.event.player.PlayerInteractEntityEvent(player, mouth, org.bukkit.inventory.EquipmentSlot.HAND))
        assertEquals(2, player.inventory.itemInMainHand.amount)
    }

    @Test fun attackEventIsWiredAndWorldUnloadRemovesOwnedEntities() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        val player = server.addPlayer()
        player.teleport(Location(world, 0.5, 64.0, 0.5))
        assertTrue(plugin.encounters.spawn(player.location))
        val mouth = world.entities.filterIsInstance<org.bukkit.entity.Interaction>().first()
        val attack = io.papermc.paper.event.player.PrePlayerAttackEntityEvent(player, mouth, true)
        server.pluginManager.callEvent(attack)
        assertFalse(attack.isCancelled, "the observer leaves native cancellation decisions to protection plugins")
        assertEquals(400.0, plugin.encounters.healthAt(player.location), "a pre-event alone must not mutate combat")
        server.pluginManager.callEvent(org.bukkit.event.world.WorldUnloadEvent(world))
        assertEquals(0, plugin.encounters.size)
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }

    @Test fun attacksOnExposedMouthDamageSharedPoolAndRemoveOnDeath() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 8.5, 64.0, 0.5))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        val mouth = world.entities.filterIsInstance<org.bukkit.entity.Interaction>().first()
        assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
        assertEquals(400.0, plugin.encounters.healthAt(player.location))
        player.teleport(Location(world, 0.5, 64.0, 0.5))
        server.scheduler.performTicks(30)
        assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
        assertEquals(390.0, plugin.encounters.healthAt(player.location))
        assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
        assertEquals(390.0, plugin.encounters.healthAt(player.location), "no attack spam")
    }

    @Test fun scheduledMainHeadContactExposesSharedHealthWithoutSatelliteMouths() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.gameMode = org.bukkit.GameMode.SURVIVAL
        world.getChunkAt(0, 0).load()
        player.teleport(Location(world, 0.5, 64.0, 0.5))
        assertTrue(plugin.encounters.spawn(player.location))
        server.scheduler.performTicks(30)
        assertTrue(plugin.encounters.exposedAt(player.location), "main-head contact opens a vulnerability window")
        assertEquals(1, world.entities.filterIsInstance<org.bukkit.entity.Interaction>().size, "only the main head has a mouth")
        plugin.encounters.close()
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }

    @Test fun manualSpawnBuildsToothedStationaryGardenAndRemoveOwnsEveryEntity() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getBlockAt(0, 63, 0).type = Material.STONE
        val at = Location(world, 0.5, 64.0, 0.5)
        val manager = plugin.encounters
        assertTrue(manager.spawn(at))
        assertEquals(1, manager.size)
        assertEquals(18, world.entities.filterIsInstance<org.bukkit.entity.ItemDisplay>().size, "single textured head, body, six leaves and six neck connectors")
        assertTrue(manager.removeNear(at))
        assertEquals(0, manager.size)
        assertTrue(world.entities.isEmpty())
        assertFalse(manager.removeNear(at))
    }
}
