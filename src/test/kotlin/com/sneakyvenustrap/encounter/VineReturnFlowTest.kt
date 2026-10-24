package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class VineReturnFlowTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    @org.junit.jupiter.api.BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun pulledVictimEscapesToPreDragAnchorAndNearbyPlayerRetainsOwnReturnLocation() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = TerrainWorld().also { it.name = "Darkvale"; server.addWorld(it) }
        val arena = server.addStomachWorld("vine-return-stomach")
        for (x in -3..18) for (z in -3..6) world.getBlockAt(x, 63, z).type = Material.STONE
        world.getChunkAt(0, 0).load(); world.getChunkAt(1, 0).load()
        val stomach = Stomachs(plugin) { arena }
        val encounters = Encounters(plugin, stomach)
        val victim = server.addPlayer()
        victim.teleport(Location(world, 14.0, 64.0, 1.5))
        val victimAnchor = stomach.prepareAnchor(victim)!!
        val bystander = server.addPlayer()
        bystander.teleport(Location(world, 3.5, 64.0, 3.5))
        bystander.gameMode = GameMode.CREATIVE
        val bystanderAnchor = stomach.prepareAnchor(bystander)!!
        assertTrue(encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        var sawPull = false
        var swallowed = false
        // MockBukkit does not integrate player physics. Apply only the velocity actually
        // issued by the encounter, over each five-tick interval, on this flat fixture.
        for (i in 0 until 120) {
            server.scheduler.performTicks(5)
            if (stomach.contains(victim.uniqueId)) { swallowed = true; break }
            if (victim.velocity.lengthSquared() > 0.00001) {
                sawPull = true
                victim.teleport(victim.location.add(victim.velocity.clone().multiply(5)))
            }
        }
        assertTrue(sawPull, "actual vine must catch and issue pull before head capture")
        assertTrue(swallowed, "pulled victim must be swallowed by the main-head scheduler")
        assertEquals(arena, victim.world)
        assertFalse(stomach.contains(bystander.uniqueId))
        repeat(3) { assertTrue(stomach.strike(victim, stomach.originalFlower(victim.uniqueId)!!)) }
        assertEquals(victimAnchor, victim.location, "escape restores the captured pre-drag anchor")
        victim.gameMode = GameMode.CREATIVE
        victim.velocity = org.bukkit.util.Vector()
        bystander.gameMode = GameMode.SURVIVAL
        for (i in 0 until 80) {
            server.scheduler.performTicks(5)
            if (stomach.contains(bystander.uniqueId)) break
        }
        assertTrue(stomach.contains(bystander.uniqueId), "nearby second player must exercise a separate swallow")
        repeat(3) { assertTrue(stomach.strike(bystander, stomach.originalFlower(bystander.uniqueId)!!)) }
        assertEquals(bystanderAnchor, bystander.location, "another player must never inherit the vine victim's anchor")
        assertNotEquals(victimAnchor, bystander.location)
        encounters.close()
        assertTrue(arena.entities.isEmpty())
        assertTrue(world.entities.filterNot { it is org.bukkit.entity.Player }.isEmpty())
    }
}
