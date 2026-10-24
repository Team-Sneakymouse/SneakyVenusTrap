package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.GameMode
import org.bukkit.entity.Display
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class AttackFlowTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun dodgingLockedAimAvoidsContactWithoutSpawningExtraMouths() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, 2.0, 64.0, -2.0))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        val ids = world.entities.filterIsInstance<Display>().map { it.uniqueId }.toSet()
        server.scheduler.performTicks(20)
        player.teleport(Location(world, -3.0, 64.0, -2.0))
        server.scheduler.performTicks(20)
        assertEquals(20.0, player.health)
        assertTrue(plugin.encounters.exposedAt(player.location), "a dodged snap must still open the counterattack window")
        val mouth = world.entities.filterIsInstance<org.bukkit.entity.Interaction>().single()
        assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
        assertEquals(390.0, plugin.encounters.healthAt(player.location), "dodging must permit a successful counterattack")
        assertFalse(plugin.encounters.stomachs.contains(player.uniqueId))
        assertEquals(1, world.entities.filterIsInstance<org.bukkit.entity.Interaction>().size)
        assertEquals(ids, world.entities.filterIsInstance<Display>().map { it.uniqueId }.toSet())
    }

    @Test fun creativeTargetCancelsWindupAndResetsPose() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, 2.0, 64.0, -2.0))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        server.scheduler.performTicks(5)
        val displays = world.entities.filterIsInstance<org.bukkit.entity.ItemDisplay>().filter {
            it.itemStack.itemMeta.itemModel?.key?.substringAfterLast('/') in setOf("body", "upperjaw", "lowerjaw", "leaf")
        }
        server.scheduler.performTicks(10)
        player.gameMode = GameMode.CREATIVE
        server.scheduler.performTicks(120)
        assertEquals(20.0, player.health)
        val recovered = displays.map { it.transformation }
        server.scheduler.performTicks(160)
        assertEquals(recovered, displays.map { it.transformation })
        assertEquals(1, world.entities.filterIsInstance<org.bukkit.entity.Interaction>().size)
    }

    @Test fun distinctTelegraphPrecedesExactlyOneEncounterDrivenTransfer() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        val arena = server.addStomachWorld("injected-stomach")
        world.getChunkAt(0, 0).load()
        for (x in -4..5) for (z in -5..4) world.getBlockAt(x, 63, z).type = org.bukkit.Material.STONE
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, 2.0, 64.0, -2.0))
        val stomach = Stomachs(plugin) { arena }
        val encounters = Encounters(plugin, stomach)
        var transfers = 0
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler fun teleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
                if (event.to.world == arena) transfers++
            }
        }, plugin)
        assertTrue(encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        server.scheduler.performTicks(95)
        assertEquals(world, player.world)
        val messages = generateSequence { player.nextMessage() }.toList()
        assertTrue(messages.isEmpty(), "combat uses visual/audio telegraphs, never routine chat")
        assertTrue(world.spawnedParticles.isEmpty(), "jaw opening and audio replace glitter cues")
        server.scheduler.performTicks(40)
        assertEquals(arena, player.world)
        assertTrue(stomach.contains(player.uniqueId))
        val jaws = world.entities.filterIsInstance<org.bukkit.entity.ItemDisplay>().filter {
            it.itemStack.itemMeta.itemModel.toString().endsWith("jaw")
        }
        val matrix = org.mockbukkit.mockbukkit.entity.DisplayMock::class.java.getDeclaredField("transformationMatrix").apply { isAccessible = true }
        val closed = jaws.map { org.joml.Matrix4f(matrix.get(it) as org.joml.Matrix4f) }
        server.scheduler.performTicks(10)
        assertEquals(closed, jaws.map { org.joml.Matrix4f(matrix.get(it) as org.joml.Matrix4f) }, "swallow retains exterior closed jaws after target world change")
        server.scheduler.performTicks(30)
        assertEquals(1, transfers)
        repeat(3) { stomach.strike(player, stomach.originalFlower(player.uniqueId)!!) }
        assertEquals(world, player.world)
        encounters.close()
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun nearPlayerReceivesAnimatedOrdinaryBiteWithoutSwallow() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.gameMode = GameMode.SURVIVAL
        player.teleport(Location(world, 2.0, 64.0, -2.0))
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        val displays = world.entities.filterIsInstance<Display>()
        val initial = displays.map { it.transformation }
        server.scheduler.performTicks(15)
        assertTrue(displays.indices.any { displays[it].transformation != initial[it] }, "owned head displays must actually transform during windup")
        assertEquals(20.0, player.health, "windup cannot hit")
        server.scheduler.performTicks(20)
        assertTrue(player.health < 20.0, "main-head contact must bite before satellite cycle")
        assertFalse(plugin.encounters.stomachs.contains(player.uniqueId))
        assertEquals(displays.map { it.uniqueId }.toSet(), world.entities.filterIsInstance<Display>().map { it.uniqueId }.toSet())
        plugin.encounters.close()
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }
}
