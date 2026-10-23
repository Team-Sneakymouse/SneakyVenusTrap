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
            .build()
}
