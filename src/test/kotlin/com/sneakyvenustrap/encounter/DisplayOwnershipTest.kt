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
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    private fun failsCleanly(mode: String) {
        val server = MockBukkit.mock()
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

    @Test fun lateSpawnFailureRemovesEarlierParts() = failsCleanly("spawn")
    @Test fun configurationFailureRemovesRawAndEarlierParts() = failsCleanly("config")
    @Test fun initialPoseFailureRemovesAllParts() = failsCleanly("pose")
}
