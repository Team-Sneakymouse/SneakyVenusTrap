package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import com.sneakyvenustrap.service.PlantAttack
import com.sneakyvenustrap.service.PlantDrama
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class PlaytestCorrectionsTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun biteTracksEarlyMotionThenFreezesOnlyFinalDodgeWindow() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 2.0, 64.0, -2.0))
        plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5))
        server.scheduler.performTicks(10)
        player.teleport(Location(world, -2.0, 64.0, -2.0))
        server.scheduler.performTicks(10)
        val mouth = world.entities.filterIsInstance<org.bukkit.entity.Interaction>().single()
        // Contact must head to the new target, not the original stationary snapshot.
        server.scheduler.performTicks(10)
        assertTrue(mouth.location.x < 0.0)
        assertTrue(player.health < 20.0)
        plugin.encounters.close()
    }

    @Test fun capturedVineHasVisibleChestCoilsAndOwnedBoundedMovementRestraint() {
        val world = TerrainWorld().also { server.addWorld(it) }
        world.getChunkAt(0, 0).load(); world.getChunkAt(1, 0).load()
        val player = server.addPlayer()
        player.teleport(Location(world, 24.0, 64.0, 2.0))
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        val route = VineTerrain.route(Location(world, 2.0, 64.0, 2.0), player.location)!!
        val speed = player.getAttribute(Attribute.MOVEMENT_SPEED)!!
        val foreign = org.bukkit.attribute.AttributeModifier(org.bukkit.NamespacedKey("other", "boots"),
            0.2, org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR)
        speed.addModifier(foreign)
        val original = speed.value
        val vine = TunnellingVine(view, player.uniqueId, route) { true }
        repeat(25) { assertTrue(vine.tick(player)) }
        assertEquals(TunnellingVine.Phase.PULL, vine.phase)
        assertEquals(0.0, speed.value, 0.000001, "capture must prevent walking against the pull")
        val coils = vine.entities.filterIsInstance<ItemDisplay>().filter { it.location.y >= 64.4 && it.location.distanceSquared(player.location) < 4.0 }
        assertTrue(coils.size >= 8, "a real helix must wrap the victim, not merely an eruption at the old endpoint")
        repeat(25) { assertTrue(vine.tick(player), "capture must outlive the former 220-tick lifetime") }
        var timedOut = false
        repeat(130) { if (!timedOut) timedOut = !vine.tick(player) }
        assertTrue(timedOut, "bounded restraint must release even when physics makes no progress")
        vine.close()
        assertEquals(original, speed.value)
        assertTrue(speed.modifiers.any { it.key == foreign.key }, "never overwrite foreign speed modifiers")
        assertTrue(vine.entities.isEmpty())
        assertEquals(listOf(player), world.entities)
    }

    @Test fun nearBodyVinesPersistWithStableIdsAndNoGlitterOrLaneDamage() {
        val world = server.addSimpleWorld("Darkvale")
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        view.build(); view.buildBundles()
        val state = PlantDrama()
        view.drama(state, emptyList())
        val vines = view.entities.filterIsInstance<ItemDisplay>().filter { it.itemStack.itemMeta.itemModel.toString().endsWith("/vine_segment") }
        assertTrue(vines.size >= 24, "three large persistent articulated vines plus the neck")
        val ids = view.entities.map { it.uniqueId }
        val before = vines.map { it.transformation }
        repeat(100) { state.tick(1.0); view.drama(state, emptyList()) }
        assertEquals(ids, view.entities.map { it.uniqueId })
        assertTrue(vines.indices.any { vines[it].transformation != before[it] })
        assertTrue(view.entities.size <= 44)
        assertTrue(world.spawnedParticles.isEmpty(), "no random compost/soul/dust fountains")
        view.close()
        assertTrue(world.entities.isEmpty())
    }
}
