package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class StomachTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    private fun failingPlayer(player: org.bukkit.entity.Player, fail: (String) -> Boolean): org.bukkit.entity.Player =
        java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.entity.Player::class.java)) { _, method, args ->
            if (fail(method.name)) throw IllegalStateException("Injected ${method.name} failure")
            try { method.invoke(player, *(args ?: emptyArray())) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        } as org.bukkit.entity.Player

    @Test fun postTeleportCueFailureEvacuatesBeforeDiscardingCaptive() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        player.inventory.addItem(org.bukkit.inventory.ItemStack(Material.DIAMOND, 3))
        val stomach = Stomachs(plugin) { arena }
        val failing = failingPlayer(player) { it == "playSound" }
        assertFalse(stomach.swallow(failing, "garden"))
        assertEquals(outside, player.world, "a cue exception must not strand the player")
        assertFalse(stomach.contains(player.uniqueId))
        assertTrue(arena.entities.isEmpty())
        assertEquals(3, player.inventory.contents.filterNotNull().sumOf { it.amount })
        assertEquals(Material.STONE, outside.getBlockAt(0, 63, 0).type)
    }

    @Test fun throwingReleaseRetainsJournalAndCanBeRetried() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        assertFalse(stomach.release(failingPlayer(player) { it == "teleport" }))
        assertTrue(stomach.contains(player.uniqueId))
        assertFalse(stomach.transferring)
        assertEquals(arena, player.world)
        assertNotNull(Stomachs(plugin) { arena }.returnForRespawn(player.uniqueId))
        assertTrue(stomach.release(player))
        assertEquals(outside, player.world)
        assertNull(Stomachs(plugin) { arena }.returnForRespawn(player.uniqueId))
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun shutdownRetainsFailedEvacuationAndRetriesWithoutLosingOtherCaptives() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val blocked = server.addPlayer()
        val other = server.addPlayer()
        listOf(blocked, other).forEach { it.teleport(Location(outside, 0.5, 64.0, 0.5)) }
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(blocked, "garden"))
        assertTrue(stomach.swallow(other, "garden"))
        var cancel = true
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun teleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
                if (cancel && event.player == blocked) event.isCancelled = true
            }
        }, plugin)
        stomach.releaseGarden("garden")
        assertTrue(stomach.contains(blocked.uniqueId))
        assertEquals(outside, other.world)
        stomach.close()
        assertTrue(stomach.contains(blocked.uniqueId), "shutdown must retain failed evacuation ownership")
        assertEquals(arena, blocked.world)
        assertNotNull(Stomachs(plugin) { arena }.returnForRespawn(blocked.uniqueId))
        assertFalse(arena.entities.isEmpty(), "do not dismantle an occupied room")
        cancel = false
        stomach.close()
        assertEquals(outside, blocked.world)
        assertFalse(stomach.contains(blocked.uniqueId))
        assertNull(Stomachs(plugin) { arena }.returnForRespawn(blocked.uniqueId))
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun journalWriteFailureRetainsRecoveryUntilCleanupCanRetry() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        val staging = java.io.File(plugin.dataFolder, "captives.yml.new")
        assertTrue(staging.mkdir()) // Real filesystem failure, not a mocked journal.
        assertFalse(stomach.release(player))
        assertEquals(outside, player.world)
        assertNotNull(stomach.returnForRespawn(player.uniqueId))
        assertNotNull(Stomachs(plugin) { arena }.returnForRespawn(player.uniqueId))
        assertTrue(staging.delete())
        assertTrue(stomach.release(player))
        assertNull(stomach.returnForRespawn(player.uniqueId))
        assertNull(Stomachs(plugin) { arena }.returnForRespawn(player.uniqueId))
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun deathPreservesItemsAndRespawnUsesDurableExteriorDestination() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        assertTrue(stomach.swallow(player, "garden"))
        val death = org.bukkit.event.entity.PlayerDeathEvent(player,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
            mutableListOf(org.bukkit.inventory.ItemStack(Material.DIAMOND)), 10, net.kyori.adventure.text.Component.empty(), false)
        server.pluginManager.callEvent(death)
        assertTrue(death.keepInventory)
        assertTrue(death.keepLevel)
        assertTrue(death.drops.isEmpty())
        val respawn = org.bukkit.event.player.PlayerRespawnEvent(player, arena.spawnLocation, false)
        server.pluginManager.callEvent(respawn)
        assertEquals(outside, respawn.respawnLocation.world)
        server.scheduler.performOneTick()
        assertFalse(stomach.contains(player.uniqueId))
    }

    @Test fun externalTeleportReleasesCaptiveAndRestartJournalRecoversOnJoin() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        val home = Location(outside, 0.5, 64.0, 0.5)
        player.teleport(home)
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        assertTrue(stomach.swallow(player, "garden"))
        player.teleport(home)
        assertFalse(stomach.contains(player.uniqueId))
        assertEquals(outside, player.world)
        // Simulate a crash journal on disk, without any in-memory captive.
        val yaml = org.bukkit.configuration.file.YamlConfiguration()
        yaml.set(player.uniqueId.toString(), home)
        yaml.save(java.io.File(plugin.dataFolder, "captives.yml"))
        val recovered = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, recovered), plugin)
        server.pluginManager.callEvent(org.bukkit.event.player.PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()))
        server.scheduler.performOneTick()
        assertFalse(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(plugin.dataFolder, "captives.yml")).contains(player.uniqueId.toString()))
    }

    @Test fun arenaProtectionAndQuitReleaseKeepRecoveryAndInventorySafe() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        assertTrue(stomach.swallow(player, "garden"))
        val breaking = org.bukkit.event.block.BlockBreakEvent(player.location.clone().add(0.0, -1.0, 0.0).block, player)
        server.pluginManager.callEvent(breaking)
        assertTrue(breaking.isCancelled)
        val interact = org.bukkit.event.player.PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
            org.bukkit.inventory.ItemStack(Material.LAVA_BUCKET), player.location.block, org.bukkit.block.BlockFace.UP)
        server.pluginManager.callEvent(interact)
        assertEquals(org.bukkit.event.Event.Result.DENY, interact.useItemInHand())
        server.pluginManager.callEvent(org.bukkit.event.world.ChunkUnloadEvent(player.location.chunk))
        assertFalse(stomach.contains(player.uniqueId))
        // Re-enter to exercise quit as a separate lifetime.
        assertTrue(stomach.swallow(player, "garden"))
        server.pluginManager.callEvent(org.bukkit.event.player.PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()))
        assertFalse(stomach.contains(player.uniqueId))
        assertEquals(outside, player.world)
        assertTrue(arena.entities.isEmpty())
        assertFalse(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(plugin.dataFolder, "captives.yml")).contains(player.uniqueId.toString()))
    }

    @Test fun pressureAndAllyConvulsionsEventuallyReleaseCaptivesAndCleanupIsIdempotent() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        repeat(15) { stomach.tick() }
        assertTrue(stomach.help("garden"))
        assertFalse(stomach.help("garden"), "ally rescue is rate limited")
        repeat(110) { stomach.tick() }
        assertFalse(stomach.contains(player.uniqueId))
        assertEquals(outside, player.world)
        stomach.close(); stomach.close()
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun captiveSolvesOriginalCryAndReturnsWithoutInventoryLossOrExteriorWrites() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        player.inventory.addItem(org.bukkit.inventory.ItemStack(Material.DIAMOND, 3))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        assertEquals(arena, player.world)
        assertTrue(stomach.contains(player.uniqueId))
        repeat(3) { stomach.strike(player, stomach.originalFlower(player.uniqueId)!!) }
        assertFalse(stomach.contains(player.uniqueId))
        assertEquals(outside, player.world)
        assertEquals(3, player.inventory.contents.filterNotNull().sumOf { it.amount })
        assertEquals(Material.STONE, outside.getBlockAt(0, 63, 0).type)
        assertTrue(arena.entities.isEmpty())
        stomach.close()
    }
}
