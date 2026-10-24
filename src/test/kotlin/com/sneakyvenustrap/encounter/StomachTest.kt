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

    @Test fun paperCrossWorldVehicleGateRemovesNameplateOnSwallowAndReturn() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val display = outside.spawn(player.location, org.bukkit.entity.TextDisplay::class.java)
        assertTrue(player.addPassenger(display))
        // CraftPlayer.teleport0 rejects cross-world vehicles BEFORE firing any teleport event.
        val paper = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.entity.Player::class.java)) { _, method, args ->
            if (method.name == "teleport" && args?.firstOrNull() is Location &&
                (args[0] as Location).world != player.world && player.passengers.isNotEmpty()) false
            else try { method.invoke(player, *(args ?: emptyArray())) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        } as org.bukkit.entity.Player
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(paper, "garden"))
        assertEquals(arena, player.world)
        assertFalse(display.isValid)
        val replacement = arena.spawn(player.location, org.bukkit.entity.TextDisplay::class.java)
        player.addPassenger(replacement)
        assertTrue(stomach.release(paper))
        assertEquals(outside, player.world)
        assertFalse(replacement.isValid)
    }

    @Test fun cancelledTransferKeepsPlayerOutsideAfterNameplateRemoval() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val display = outside.spawn(player.location, org.bukkit.entity.TextDisplay::class.java)
        player.addPassenger(display)
        server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler fun teleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
                if (event.to.world == arena) event.isCancelled = true
            }
        }, plugin)
        val stomach = Stomachs(plugin) { arena }
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertFalse(display.isValid)
        assertNull(stomach.returnForRespawn(player.uniqueId))
    }

    @Test fun livingPassengerIsRefusedWithoutDismountingOrDeletingIt() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        val passenger = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        passenger.teleport(player.location)
        player.addPassenger(passenger)
        val stomach = Stomachs(plugin) { arena }
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertEquals(player, passenger.vehicle)
        assertTrue(passenger.isValid)
    }

    @Test fun echoesAreSequencedAndStaleCuesStopAfterRelease() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        var sounds = 0
        val observed = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.entity.Player::class.java)) { _, method, args ->
            if (method.name == "playSound" && args?.getOrNull(1) == "entity.ghast.scream") sounds++
            try { method.invoke(player, *(args ?: emptyArray())) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        } as org.bukkit.entity.Player
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(observed, "garden"))
        assertEquals(1, sounds, "only the original sounds immediately")
        server.scheduler.performTicks(16)
        assertEquals(2, sounds, "first echo follows the original, not simultaneous")
        assertTrue(stomach.release(observed))
        server.scheduler.performTicks(80)
        assertEquals(2, sounds, "released captive cannot receive stale echo callbacks")
    }

    @Test fun oldRoundEchoesCannotPlayAfterAKnotStartsAnotherRound() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        var sounds = 0
        val observed = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.entity.Player::class.java)) { _, method, args ->
            if (method.name == "playSound" && args?.getOrNull(1) == "entity.ghast.scream") sounds++
            try { method.invoke(player, *(args ?: emptyArray())) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        } as org.bukkit.entity.Player
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(observed, "garden"))
        server.scheduler.performTicks(5)
        assertTrue(stomach.strike(observed, stomach.originalFlower(player.uniqueId)!!))
        assertEquals(2, sounds, "new round original follows the first original")
        server.scheduler.performTicks(11)
        assertEquals(2, sounds, "old round first echo is fenced while the same captive remains")
        server.scheduler.performTicks(48)
        assertEquals(5, sounds, "only the three new round echoes may play")
        assertTrue(stomach.contains(player.uniqueId))
        stomach.release(player)
    }

    @Test fun arenaConstructionFailureCancelsWithoutMovingOrJournallingPlayer() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("broken-arena")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val broken = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.bukkit.World::class.java)) { _, method, args ->
            if (method.name == "getBlockAt") throw IllegalStateException("Injected arena construction failure")
            try { method.invoke(arena, *(args ?: emptyArray())) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
        } as org.bukkit.World
        val stomach = Stomachs(plugin) { broken }
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertFalse(stomach.contains(player.uniqueId))
        assertNull(stomach.returnForRespawn(player.uniqueId))
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun airborneCaptureKeepsSafeExteriorAnchor() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 67.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"), "bounded downward search must find the floor below an airborne victim")
        assertEquals(arena, player.world)
        assertTrue(stomach.release(player))
        assertEquals(64.0, player.location.y)
    }

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

    @Test fun puzzleUsesOneHintAndProgressBarInsteadOfStrikeChat() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        assertEquals(1, generateSequence { player.nextMessage() }.toList().size)
        val bar = stomach.progressBar(player.uniqueId)!!
        assertEquals(listOf(player), bar.players)
        stomach.tick()
        assertTrue(bar.progress > 0)
        stomach.strike(player, stomach.originalFlower(player.uniqueId)!!)
        assertTrue(bar.title.contains("knots 1/3"))
        repeat(2) { stomach.strike(player, stomach.originalFlower(player.uniqueId)!!) }
        assertTrue(bar.players.isEmpty(), "release removes the owned progress UI")
        assertTrue(generateSequence { player.nextMessage() }.toList().isEmpty(), "knots and escape use sound, particles and progress UI")
        assertEquals(outside, player.world)
    }

    @Test fun cancelledAndCrossRoomAttemptsDoNotReleaseAndDiagnosticsExplainTheActualReturn() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        val logs = mutableListOf<String>()
        val handler = object : java.util.logging.Handler() {
            override fun publish(record: java.util.logging.LogRecord) { logs += record.message }
            override fun flush() {}
            override fun close() {}
        }
        plugin.logger.addHandler(handler)
        try {
            assertTrue(stomach.swallow(player, "garden"))
            val cancelled = org.bukkit.event.player.PlayerTeleportEvent(player, player.location, outside.spawnLocation,
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
            cancelled.isCancelled = true
            server.pluginManager.callEvent(cancelled)
            assertTrue(cancelled.isCancelled)
            assertTrue(stomach.contains(player.uniqueId))
            val crossRoom = org.bukkit.event.player.PlayerTeleportEvent(player, player.location, player.location.clone().add(32.0, 0.0, 0.0))
            server.pluginManager.callEvent(crossRoom)
            assertTrue(crossRoom.isCancelled)
            assertTrue(stomach.contains(player.uniqueId))
            assertTrue(logs.any { it.contains("phase=teleport-observe") && it.contains("owned=false") && it.contains("cancelledAtMonitor=true") && it.contains("cause=PLUGIN") && it.contains("from=") && it.contains("to=") })
            assertTrue(stomach.release(player, Stomachs.ReleaseReason.ESCAPE))
            assertTrue(logs.any { it.contains("phase=release-request reason=ESCAPE") && it.contains("heldMillis=") })
            assertTrue(logs.any { it.contains("mode=return reason=ESCAPE") && it.contains("outcome=success") })
        } finally { plugin.logger.removeHandler(handler); stomach.close() }
    }

    @Test fun sameRoomFollowupTeleportDoesNotInstantlyReturnANewCaptive() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val outside = server.addSimpleWorld("Darkvale")
        val arena = server.addSimpleWorld("stomach-test")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        val player = server.addPlayer()
        player.teleport(Location(outside, 0.5, 64.0, 0.5))
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        assertTrue(stomach.swallow(player, "garden"))
        // A follow-up plugin teleport/position correction is not a puzzle escape.
        assertTrue(player.teleport(player.location.clone().add(0.1, 0.0, 0.0)))
        assertTrue(stomach.contains(player.uniqueId))
        assertEquals(arena, player.world)
        assertNotNull(stomach.returnForRespawn(player.uniqueId))
        repeat(3) { stomach.strike(player, stomach.originalFlower(player.uniqueId)!!) }
        assertEquals(outside, player.world)
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
