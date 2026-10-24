package com.sneakyvenustrap.encounter

import org.bukkit.WorldCreator
import org.bukkit.plugin.Plugin
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

/** MockBukkit 4.117 throws for ALL plugin ticket APIs. Model only those missing APIs.
 * Real room writes, entities, player teleports, event listeners and journal remain under test.
 */
internal class StomachWorldMock(name: String) : WorldMock(WorldCreator(name)) {
    val tickets = mutableMapOf<Pair<Int, Int>, MutableSet<Plugin>>()
    var failTicket = false
    var failRemoveTicket = false
    var onBlockRead: ((Int, Int, Int) -> Unit)? = null
    // DisplayMock's billboard setter also throws; supply a real TextDisplayMock with only
    // that missing getter/setter implemented (not a proxy: passengers require EntityMock).
    override fun <T : org.bukkit.entity.Entity> spawn(at: org.bukkit.Location, type: Class<T>): T {
        if (type != org.bukkit.entity.TextDisplay::class.java) return super.spawn(at, type)
        val server = requireNotNull(org.mockbukkit.mockbukkit.MockBukkit.getMock())
        val raw = object : org.mockbukkit.mockbukkit.entity.TextDisplayMock(server, java.util.UUID.randomUUID()) {
            private var mode = org.bukkit.entity.Display.Billboard.FIXED
            override fun setBillboard(billboard: org.bukkit.entity.Display.Billboard) { mode = billboard }
            override fun getBillboard() = mode
        }
        raw.setLocation(at)
        raw.setSpawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM)
        server.registerEntity(raw)
        return type.cast(raw)
    }
    override fun getBlockAt(x: Int, y: Int, z: Int): org.mockbukkit.mockbukkit.block.BlockMock {
        onBlockRead?.invoke(x, y, z)
        return super.getBlockAt(x, y, z)
    }
    override fun addPluginChunkTicket(x: Int, z: Int, plugin: Plugin): Boolean {
        check(!failTicket) { "Injected ticket failure" }
        check(plugin.isEnabled) { "Plugin must be enabled" }
        loadChunk(x, z)
        return tickets.getOrPut(x to z) { mutableSetOf() }.add(plugin)
    }
    override fun removePluginChunkTicket(x: Int, z: Int, plugin: Plugin): Boolean {
        check(!failRemoveTicket) { "Injected ticket removal failure" }
        val set = tickets[x to z] ?: return false
        val removed = set.remove(plugin)
        if (set.isEmpty()) tickets.remove(x to z)
        return removed
    }
    override fun getPluginChunkTickets(x: Int, z: Int): Collection<Plugin> = tickets[x to z]?.toList() ?: emptyList()
}

internal fun ServerMock.addStomachWorld(name: String): StomachWorldMock =
    StomachWorldMock(name).also { addWorld(it) }
