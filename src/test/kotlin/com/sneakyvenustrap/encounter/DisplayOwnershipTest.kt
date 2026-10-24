package com.sneakyvenustrap.encounter

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.world.WorldMock
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException

class DisplayOwnershipTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    @org.junit.jupiter.api.BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    private fun failsCleanly(mode: String) {
        val world = object : WorldMock() {
            var displays = 0
            override fun <T : Entity> spawn(at: Location, type: Class<T>): T {
                if (type != ItemDisplay::class.java) return super.spawn(at, type)
                displays++
                if (mode == "spawn" && displays == 7) throw IllegalStateException("injected spawn")
                val raw = super.spawn(at, type)
                val ordinal = displays
                @Suppress("UNCHECKED_CAST")
                return Proxy.newProxyInstance(type.classLoader, arrayOf<Class<*>>(type)) { _, method, args ->
                    if ((mode == "config" && ordinal == 7 && method.name == "setPersistent") ||
                        (mode == "pose" && method.name == "setTransformation")) {
                        throw IllegalStateException("injected $mode")
                    }
                    try { method.invoke(raw, *(args ?: emptyArray())) }
                    catch (failure: InvocationTargetException) { throw failure.cause!! }
                } as T
            }
        }
        server.addWorld(world)
        val view = GardenView(Location(world, 0.5, 64.0, 0.5), true)
        assertThrows(IllegalStateException::class.java) { view.build() }
        assertTrue(world.entities.isEmpty(), "$mode failure leaked ${world.entities.size} entities")
        assertTrue(view.entities.isEmpty())
    }

    private fun dramaFailure(removalDuringTick: Boolean) {
        val plugin = MockBukkit.load(com.sneakyvenustrap.SneakyVenusTrap::class.java)
        val world = object : WorldMock() {
            var count = 0
            override fun <T : Entity> spawn(at: Location, type: Class<T>): T {
                if (type == ItemDisplay::class.java && ++count == if (removalDuringTick) 37 else 17)
                    throw IllegalStateException("injected drama display failure")
                return super.spawn(at, type)
            }
        }.also { it.name = "drama-failure"; server.addWorld(it); it.getChunkAt(0, 0).load() }
        val at = Location(world, 0.5, 64.0, 0.5)
        assertThrows(IllegalStateException::class.java) { plugin.encounters.spawn(at) }
        assertEquals(0, plugin.encounters.size)
        assertTrue(world.entities.filter { it !is org.bukkit.entity.Player }.isEmpty())
    }

    @Test fun bundleSpawnFailureRemovesWholeGarden() = dramaFailure(false)
    @Test fun latePersistentTentacleSpawnFailureRemovesAllEarlierParts() = dramaFailure(true)

    @Test fun lateSpawnFailureRemovesEarlierParts() = failsCleanly("spawn")
    @Test fun configurationFailureRemovesRawAndEarlierParts() = failsCleanly("config")
    @Test fun initialPoseFailureRemovesAllParts() = failsCleanly("pose")
}
