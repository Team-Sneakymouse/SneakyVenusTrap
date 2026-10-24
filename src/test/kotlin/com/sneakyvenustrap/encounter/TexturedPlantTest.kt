package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.mockbukkit.mockbukkit.MockBukkit

class TexturedPlantTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun defaultPlantUsesNineStableModelPartsAndOneMainMouth() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        val parts = world.entities.filterIsInstance<ItemDisplay>()
        assertEquals(9, parts.size)
        val keys = parts.map { it.itemStack.itemMeta.itemModel.toString() }
        assertEquals(6, keys.count { it == "dev:venus_trap/leaf" })
        for (key in listOf("body", "upperjaw", "lowerjaw")) assertEquals(1, keys.count { it == "dev:venus_trap/$key" })
        assertTrue(parts.all { it.itemDisplayTransform == ItemDisplay.ItemDisplayTransform.NONE && !it.isPersistent })
        val ids = parts.map { it.uniqueId }
        server.scheduler.performTicks(100)
        assertEquals(ids, world.entities.filterIsInstance<ItemDisplay>().map { it.uniqueId })
        assertEquals(1, world.entities.filterIsInstance<org.bukkit.entity.Interaction>().size)
        plugin.encounters.close()
        assertTrue(world.entities.isEmpty())
    }
}
