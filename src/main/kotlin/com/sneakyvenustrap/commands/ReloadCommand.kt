package com.sneakyvenustrap.commands

import com.sneakyvenustrap.SneakyVenusTrap
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

class ReloadCommand(private val plugin: SneakyVenusTrap) {

    fun create(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(SneakyVenusTrap.IDENTIFIER)
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission("${SneakyVenusTrap.IDENTIFIER}.command.reload") }
                    .executes { context ->
                        plugin.reloadPlugin()
                        context.source.sender.sendMessage("${plugin.name} configuration reloaded.")
                        1
                    }
            )
            .then(Commands.literal("spawn").requires { it.sender.isOp }.executes { context ->
                val player = context.source.sender as? org.bukkit.entity.Player
                val block = player?.getTargetBlockExact(30)
                if (block == null) {
                    context.source.sender.sendMessage("Look at a solid block within 30 blocks (players only).")
                    0
                } else {
                    val spawned = plugin.encounters.spawn(block.location.add(0.5, 1.0, 0.5))
                    player.sendMessage(if (spawned) "She has taken root." else "Garden limit or another encounter is too close.")
                    if (spawned) 1 else 0
                }
            })
            .then(Commands.literal("remove").requires { it.sender.isOp }.executes { context ->
                val player = context.source.sender as? org.bukkit.entity.Player
                val block = player?.getTargetBlockExact(30)
                val removed = block != null && plugin.encounters.removeNear(block.location)
                context.source.sender.sendMessage(if (removed) "Garden removed; captives released." else "Look at the ground beneath an encounter within 30 blocks.")
                if (removed) 1 else 0
            })
            .build()
}
