package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

class StomachChunkOwnershipTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: SneakyVenusTrap
    private lateinit var arena: StomachWorldMock
    private lateinit var outside: org.bukkit.World
    @BeforeEach fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        outside = server.addSimpleWorld("Darkvale")
        outside.getBlockAt(0, 63, 0).type = Material.STONE
        arena = server.addStomachWorld("stomach-test")
    }
    @AfterEach fun cleanup() { MockBukkit.unmock() }
    private fun player() = server.addPlayer().also { it.teleport(Location(outside, 0.5, 64.0, 0.5)) }

    @Test fun ticketExistsBeforeTheFirstRoomWriteAndBeforeTheDurableTransfer() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        var reads = 0
        arena.onBlockRead = { x, _, z ->
            reads++
            assertTrue(plugin in arena.getPluginChunkTickets(x shr 4, z shr 4))
        }
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun transfer(event: PlayerTeleportEvent) {
                if (event.to.world == arena) {
                    assertTrue(arena.tickets.isNotEmpty())
                    assertNotNull(Stomachs(plugin) { arena }.returnForRespawn(player.uniqueId))
                }
            }
        }, plugin)
        assertTrue(stomach.swallow(player, "garden"))
        assertTrue(reads >= 486)
        arena.onBlockRead = null
        assertTrue(stomach.release(player))
        assertTrue(arena.tickets.isEmpty())
    }

    @Test fun ticketAcquisitionFailureDoesNotWriteARoomOrMoveOrJournalThePlayer() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        arena.failTicket = true
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertTrue(arena.tickets.isEmpty())
        assertTrue(arena.entities.isEmpty())
        assertNull(stomach.returnForRespawn(player.uniqueId))
        arena.failTicket = false
        assertTrue(stomach.swallow(player, "garden"), "failed preparation must free its slot")
        stomach.close()
        assertTrue(arena.tickets.isEmpty())
    }

    @Test fun journalFailureBeforeTeleportDismantlesPreparedRoomAndReleasesTicket() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        val staging = java.io.File(plugin.dataFolder, "captives.yml.new")
        assertTrue(staging.mkdir())
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertTrue(arena.tickets.isEmpty())
        assertTrue(arena.entities.isEmpty())
        assertEquals(Material.AIR, arena.getBlockAt(8, 64, 8).type)
        assertNull(stomach.returnForRespawn(player.uniqueId))
        assertTrue(staging.delete())
        assertTrue(stomach.swallow(player, "garden"))
        stomach.close()
    }

    @Test fun cancelledReturnAndJournalRetryKeepTicketUntilConfirmedCleanup() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        assertTrue(stomach.swallow(player, "garden"))
        var cancel = true
        server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun returning(event: PlayerTeleportEvent) {
                if (event.to.world == outside && cancel) event.isCancelled = true
            }
        }, plugin)
        stomach.close()
        assertTrue(stomach.contains(player.uniqueId))
        assertEquals(setOf(0 to 0), arena.tickets.keys)
        server.pluginManager.callEvent(ChunkUnloadEvent(player.location.chunk))
        assertTrue(stomach.contains(player.uniqueId))
        cancel = false
        val staging = java.io.File(plugin.dataFolder, "captives.yml.new")
        assertTrue(staging.mkdir())
        assertFalse(stomach.release(player))
        assertEquals(outside, player.world)
        assertTrue(stomach.contains(player.uniqueId))
        assertEquals(setOf(0 to 0), arena.tickets.keys)
        assertTrue(staging.delete())
        stomach.close()
        assertFalse(stomach.contains(player.uniqueId))
        assertTrue(arena.tickets.isEmpty())
        assertTrue(arena.entities.isEmpty())
    }

    @Test fun ticketRemovalFailureAfterSuccessfulReturnRetainsRoomUntilRetry() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        assertTrue(stomach.swallow(player, "garden"))
        arena.failRemoveTicket = true
        assertFalse(stomach.release(player))
        assertEquals(outside, player.world, "the player already returned before teardown failed")
        assertNull(stomach.returnForRespawn(player.uniqueId), "successful return already cleared the journal")
        assertTrue(stomach.contains(player.uniqueId), "retain room ownership for cleanup retry")
        assertEquals(setOf(0 to 0), arena.tickets.keys)
        arena.failRemoveTicket = false
        assertTrue(stomach.release(player))
        assertFalse(stomach.contains(player.uniqueId))
        assertTrue(arena.tickets.isEmpty())
        assertTrue(arena.entities.isEmpty())
        assertEquals(Material.AIR, arena.getBlockAt(8, 64, 8).type)
        assertTrue(stomach.swallow(player, "garden"), "confirmed cleanup frees the room slot")
        assertEquals(8.5, player.location.x)
        stomach.close()
        assertTrue(arena.tickets.isEmpty())
    }

    @Test fun abandonedConstructionCleanupFailureCanBeRetriedByClose() {
        val player = player()
        val stomach = Stomachs(plugin) { arena }
        arena.onBlockRead = { _, _, _ -> throw IllegalStateException("Injected construction failure") }
        arena.failRemoveTicket = true
        assertFalse(stomach.swallow(player, "garden"))
        assertEquals(outside, player.world)
        assertFalse(stomach.contains(player.uniqueId))
        assertEquals(setOf(0 to 0), arena.tickets.keys)
        arena.onBlockRead = null
        arena.failRemoveTicket = false
        stomach.close()
        assertTrue(arena.tickets.isEmpty(), "close retries abandoned prepared rooms as well as captives")
        assertTrue(stomach.swallow(player, "garden"))
        stomach.close()
    }

    @Test fun sixteenRoomsOwnOnlyTheirFullFootprintsAndAnUnrelatedUnloadCannotEvacuateThem() {
        val stomach = Stomachs(plugin) { arena }
        server.pluginManager.registerEvents(StomachEvents(plugin, stomach), plugin)
        val captives = List(16) { player() }
        captives.forEach { assertTrue(stomach.swallow(it, "garden")) }
        val expected = captives.flatMap { player ->
            val at = player.location
            (((at.blockX - 4) shr 4)..((at.blockX + 4) shr 4)).flatMap { x ->
                (((at.blockZ - 4) shr 4)..((at.blockZ + 4) shr 4)).map { z -> x to z }
            }
        }.toSet()
        assertEquals(expected, arena.tickets.keys)
        assertEquals(16, arena.tickets.size)
        assertFalse(stomach.swallow(player(), "garden"))
        server.pluginManager.callEvent(ChunkUnloadEvent(arena.getChunkAt(99, 99)))
        captives.forEach { assertTrue(stomach.contains(it.uniqueId)) }
        val stale = ChunkUnloadEvent(captives.first().location.chunk)
        stomach.close()
        server.pluginManager.callEvent(stale)
        assertTrue(arena.tickets.isEmpty())
        assertTrue(arena.entities.isEmpty())
        captives.forEach { assertEquals(outside, it.world) }
    }
}
