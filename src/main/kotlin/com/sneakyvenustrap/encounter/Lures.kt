package com.sneakyvenustrap.encounter

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Random

/** Disabled unless a genuine serialized server ItemStack is configured. No currency/console command fiction. */
class Lures(
    private val plugin: JavaPlugin,
    private val random: Random,
    private val persistPlayer: (Player) -> Unit = { it.saveData() },
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val file = File(plugin.dataFolder, "lure-ledger.yml")
    private val attemptKey = NamespacedKey(plugin, "lure_attempt")
    fun claim(player: Player): Boolean {
        val item = plugin.config.getItemStack("lure.item")?.clone()?.also { it.amount = 1 } ?: return false
        if (item.type.isAir || player.inventory.firstEmpty() < 0) return false
        val now = clock()
        val last = player.persistentDataContainer.get(attemptKey, PersistentDataType.LONG)
        if (last != null && now - last < 86400) return false
        // Strict loading: Bukkit's loadConfiguration convenience method swallows disk/parse failures.
        // Read on each claim so an older service instance cannot overwrite a newer cap reservation.
        val ledger = YamlConfiguration()
        try {
            if (file.exists()) ledger.load(file)
        } catch (error: Exception) {
            plugin.logger.warning("Lure ledger unavailable; reward denied: ${error.message}")
            return false
        }
        val day = now / 86400
        val paid = if (ledger.getLong("day", -1) == day) ledger.getInt("paid") else 0
        if (paid >= 5) return false
        // Persist attempts before rolling; reloads/restarts cannot give another attempt.
        player.persistentDataContainer.set(attemptKey, PersistentDataType.LONG, now)
        try {
            persistPlayer(player)
        } catch (error: Exception) {
            plugin.logger.warning("Lure attempt could not be saved; reward denied: ${error.message}")
            return false
        }
        if (random.nextInt(20) != 0) return false
        ledger.set("day", day)
        ledger.set("paid", paid + 1)
        try {
            ledger.save(file) // Reserve the global cap before inventory mutation; fail closed on disk failure.
        } catch (error: Exception) {
            plugin.logger.warning("Lure cap could not be saved; reward denied: ${error.message}")
            return false
        }
        return player.inventory.addItem(item).isEmpty()
    }
}
