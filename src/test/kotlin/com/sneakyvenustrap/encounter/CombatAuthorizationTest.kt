package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class CombatAuthorizationTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    private fun exposed(): Triple<SneakyVenusTrap, org.bukkit.entity.Player, Interaction> {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        val origin = Location(world, 0.5, 64.0, 0.5)
        player.teleport(origin)
        assertTrue(plugin.encounters.spawn(origin))
        server.scheduler.performTicks(100)
        player.gameMode = GameMode.CREATIVE
        server.scheduler.performTicks(20)
        assertTrue(plugin.encounters.exposedAt(origin))
        return Triple(plugin, player, world.entities.filterIsInstance<Interaction>().first())
    }

    @Test fun highestPreCancellationPreventsDamage() {
        val (plugin, player, mouth) = exposed()
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST)
            fun deny(event: PrePlayerAttackEntityEvent) { event.isCancelled = true }
        }, plugin)
        server.pluginManager.callEvent(nativeDamage(player, mouth))
        val event = PrePlayerAttackEntityEvent(player, mouth, true)
        server.pluginManager.callEvent(event)
        server.scheduler.performTicks(1)
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
    }

    private fun nativeDamage(player: org.bukkit.entity.Player, mouth: Interaction) = EntityDamageByEntityEvent(
        player, mouth, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK)
            .withCausingEntity(player).withDirectEntity(player).build(), 1.0,
    )

    @Test fun defaultNonResponsiveInteractionStillDamagesOnlyAfterDispatch() {
        val (plugin, player, mouth) = exposed()
        val damage = nativeDamage(player, mouth)
        server.pluginManager.callEvent(damage)
        val pre = PrePlayerAttackEntityEvent(player, mouth, false)
        server.pluginManager.callEvent(pre)
        assertTrue(pre.isCancelled, "vanilla non-responsive Interaction default must not be uncancelled")
        assertFalse(damage.isCancelled, "observers must not mutate native damage events")
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
        server.scheduler.performTicks(1)
        assertEquals(390.0, plugin.encounters.healthAt(mouth.location))
    }

    @Test fun highestNativeProtectionDeniesDamage() {
        val (plugin, player, mouth) = exposed()
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST)
            fun deny(event: EntityDamageByEntityEvent) { event.isCancelled = true }
        }, plugin)
        server.pluginManager.callEvent(nativeDamage(player, mouth))
        server.pluginManager.callEvent(PrePlayerAttackEntityEvent(player, mouth, false))
        server.scheduler.performTicks(1)
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
    }

    @Test fun spectatorCannotAttack() {
        val (plugin, player, mouth) = exposed()
        player.gameMode = GameMode.SPECTATOR
        plugin.encounters.attack(player, mouth.uniqueId)
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
    }

    @Test fun deadPlayerCannotAttack() {
        val (plugin, player, mouth) = exposed()
        player.health = 0.0
        plugin.encounters.attack(player, mouth.uniqueId)
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
    }

    @Test fun standardDamageProtectionDeniesDirectAttack() {
        val (plugin, player, mouth) = exposed()
        var calls = 0
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST)
            fun deny(event: EntityDamageByEntityEvent) { calls++; event.isCancelled = true }
        }, plugin)
        assertTrue(plugin.encounters.attack(player, mouth.uniqueId))
        assertEquals(1, calls)
        assertEquals(400.0, plugin.encounters.healthAt(mouth.location))
    }
}
