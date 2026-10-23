package com.sneakyvenustrap.encounter

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.io.File
import java.io.IOException
import java.util.Random

class LuresTest {
    private lateinit var server: ServerMock
    @BeforeEach fun setup() { server = MockBukkit.mock() }
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    private fun roll(value: Int = 0) = object : Random() {
        override fun nextInt(bound: Int): Int { assertEquals(20, bound); return value }
    }

    // MockBukkit cannot save player data. Save the actual attempt PDC through this narrow adapter.
    private fun diskPersistence(plugin: SneakyVenusTrap): (Player) -> Unit = { player ->
        val attempt = player.persistentDataContainer.get(NamespacedKey(plugin, "lure_attempt"), PersistentDataType.LONG)
        assertNotNull(attempt, "attempt must be recorded before persistence")
        val data = YamlConfiguration()
        data.set("attempt", attempt)
        data.save(File(plugin.dataFolder, "test-player-${player.uniqueId}.yml"))
    }

    @Test fun realConfiguredItemPaysRarelyWithPersistentPlayerCooldownAndGlobalCap() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        val player = server.addPlayer()
        val persist = diskPersistence(plugin)
        val lure = Lures(plugin, roll(), persist) { 1_000_000L }
        assertFalse(lure.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
        plugin.config.set("lure.item", ItemStack(Material.DIAMOND, 12))
        assertTrue(lure.claim(player))
        assertFalse(Lures(plugin, roll(), persist) { 1_000_001L }.claim(player))
        assertEquals(1, player.inventory.contents.filterNotNull().sumOf { it.amount })
        assertEquals(Material.DIAMOND, player.inventory.contents.filterNotNull().single().type)
        assertEquals(12, plugin.config.getItemStack("lure.item")!!.amount)
        repeat(4) { assertTrue(Lures(plugin, roll(), persist) { 1_000_000L }.claim(server.addPlayer())) }
        assertFalse(lure.claim(server.addPlayer()))
        val ledger = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lure-ledger.yml"))
        assertEquals(5, ledger.getInt("paid"))
        assertFalse(Lures(plugin, roll(), persist) { 1_000_001L }.claim(server.addPlayer()))
        assertTrue(Lures(plugin, roll(), persist) { 1_086_400L }.claim(server.addPlayer()))
    }

    @Test fun losingAttemptSurvivesPlayerAndServiceReconstruction() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        plugin.config.set("lure.item", ItemStack(Material.DIAMOND))
        val player = server.addPlayer()
        val persist = diskPersistence(plugin)
        val savedFile = File(plugin.dataFolder, "test-player-${player.uniqueId}.yml")
        val losingRoll = object : Random() {
            override fun nextInt(bound: Int): Int {
                assertEquals(20, bound)
                assertEquals(1_000_000L, YamlConfiguration.loadConfiguration(savedFile).getLong("attempt"))
                return 1
            }
        }
        assertFalse(Lures(plugin, losingRoll, persist) { 1_000_000L }.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
        // Reconstruct relevant saved PDC on a fresh player, not the original in-memory object.
        val restored = server.addPlayer()
        val saved = YamlConfiguration.loadConfiguration(savedFile)
        restored.persistentDataContainer.set(NamespacedKey(plugin, "lure_attempt"), PersistentDataType.LONG, saved.getLong("attempt"))
        assertFalse(Lures(plugin, roll(), persist) { 1_086_399L }.claim(restored))
        assertTrue(restored.inventory.contents.filterNotNull().isEmpty())
        assertTrue(Lures(plugin, roll(), persist) { 1_086_400L }.claim(restored))
        assertEquals(1, restored.inventory.contents.filterNotNull().single().amount)
    }

    @Test fun playerPersistenceFailureDeniesPayoutBeforeRoll() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        plugin.config.set("lure.item", ItemStack(Material.DIAMOND))
        val player = server.addPlayer()
        val neverRoll = object : Random() { override fun nextInt(bound: Int): Int = throw AssertionError("must save before roll") }
        val lure = Lures(plugin, neverRoll, { throw IOException("disk unavailable") }) { 1_000_000L }
        assertFalse(lure.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
        assertFalse(File(plugin.dataFolder, "lure-ledger.yml").exists())
    }

    @Test fun unreadableOrMalformedLedgerFailsClosed() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        plugin.config.set("lure.item", ItemStack(Material.DIAMOND))
        val ledger = File(plugin.dataFolder, "lure-ledger.yml")
        assertTrue(ledger.mkdir())
        val player = server.addPlayer()
        assertFalse(Lures(plugin, roll(), diskPersistence(plugin)) { 1_000_000L }.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
        assertTrue(ledger.delete())
        ledger.writeText("paid: [unterminated")
        assertFalse(Lures(plugin, roll(), diskPersistence(plugin)) { 1_000_000L }.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
    }

    @Test fun ledgerWriteFailureAfterDurableAttemptDeniesInventoryMutation() {
        val plugin = MockBukkit.load(SneakyVenusTrap::class.java)
        plugin.config.set("lure.item", ItemStack(Material.DIAMOND))
        val ledger = File(plugin.dataFolder, "lure-ledger.yml")
        val persist = diskPersistence(plugin)
        val player = server.addPlayer()
        val lure = Lures(plugin, roll(), { p -> persist(p); assertTrue(ledger.mkdir()) }) { 1_000_000L }
        assertFalse(lure.claim(player))
        assertTrue(player.inventory.contents.filterNotNull().isEmpty())
        assertEquals(1_000_000L, YamlConfiguration.loadConfiguration(
            File(plugin.dataFolder, "test-player-${player.uniqueId}.yml")
        ).getLong("attempt"))
        assertTrue(ledger.delete())
        assertFalse(Lures(plugin, roll(), persist) { 1_000_001L }.claim(player))
    }
}
