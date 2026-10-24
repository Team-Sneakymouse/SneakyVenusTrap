package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.mockbukkit.mockbukkit.MockBukkit

class TexturedPlantTest {
    private lateinit var server: org.mockbukkit.mockbukkit.ServerMock
    @org.junit.jupiter.api.BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    @Test fun renderedNeckEndsAtTheActualJawHingeAndSharesItsContactCenterEvenDuringRecoil() {
        val world = server.addSimpleWorld("Darkvale")
        val origin = Location(world, 0.5, 64.0, 0.5)
        val view = GardenView(origin, true)
        view.build()
        val parts = view.entities.filterIsInstance<ItemDisplay>()
        val ids = parts.map { it.uniqueId }
        fun part(name: String) = parts.single { it.itemStack.itemMeta.itemModel.toString() == "dev:venus_trap/$name" }
        // MockBukkit 4.117 getTransformation conjugates the same quaternion into BOTH rotations.
        // Read the actual matrix stored by its real setter, rather than reconstruct that broken getter.
        val matrixField = org.mockbukkit.mockbukkit.entity.DisplayMock::class.java.getDeclaredField("transformationMatrix").apply { isAccessible = true }
        fun position(display: ItemDisplay, point: org.joml.Vector3f): org.joml.Vector3f =
            org.joml.Matrix4f(matrixField.get(display) as org.joml.Matrix4f).transformPosition(point)
                .add(display.location.x.toFloat(), display.location.y.toFloat(), display.location.z.toFloat())
        for ((x, z) in listOf(-4.0 to -2.0, 5.0 to 4.0, 2.0 to -5.0)) {
            val attack = com.sneakyvenustrap.service.PlantAttack()
            attack.start()
            repeat(6) { attack.tick(true) }
            view.pose(Location(world, x, 65.0, z), attack)
            view.hurt(true)
            val hinge = position(part("upperjaw"), org.joml.Vector3f(0f, 0f, 5f / 16f))
            val lowerHinge = position(part("lowerjaw"), org.joml.Vector3f(0f, 0f, 5f / 16f))
            assertTrue(hinge.distance(lowerHinge) < 0.0001, "upper=$hinge lower=$lowerHinge")
            var previous = position(part("body"), org.joml.Vector3f(-0.0956f / 2, 0.85f / 2, 0f))
            for (segment in parts.filter { it.itemStack.itemMeta.itemModel.toString() == "dev:venus_trap/vine_segment" }) {
                assertTrue(previous.distance(position(segment, org.joml.Vector3f())) < 0.0001)
                previous = position(segment, org.joml.Vector3f(0f, 10.2f / 16f, 0f))
            }
            assertTrue(previous.distance(hinge) < 0.0001)
            assertTrue(hinge.distance(org.joml.Vector3f(view.contactCenter.x.toFloat(), view.contactCenter.y.toFloat(), view.contactCenter.z.toFloat())) < 0.0001)
            assertTrue(org.joml.Vector3f(2f).distance(part("upperjaw").transformation.scale) < 0.0001)
        }
        assertEquals(ids, view.entities.filterIsInstance<ItemDisplay>().map { it.uniqueId })
        view.close()
        assertTrue(world.entities.isEmpty())
    }

    @Test fun defaultPlantUsesFifteenStableModelPartsAndOneMainMouth() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val world = server.addSimpleWorld("Darkvale")
        world.getChunkAt(0, 0).load()
        assertTrue(plugin.encounters.spawn(Location(world, 0.5, 64.0, 0.5)))
        val parts = world.entities.filterIsInstance<ItemDisplay>()
        assertEquals(18, parts.size)
        assertEquals(9, parts.count { it.itemStack.itemMeta.itemModel.toString() == "dev:venus_trap/vine_segment" })
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
