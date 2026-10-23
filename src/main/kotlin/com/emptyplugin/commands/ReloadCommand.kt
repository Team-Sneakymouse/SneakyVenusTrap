package com.emptyplugin.commands

import com.emptyplugin.EmptyPlugin
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

class ReloadCommand(private val plugin: EmptyPlugin) {

    fun create(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(EmptyPlugin.IDENTIFIER)
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission("${EmptyPlugin.IDENTIFIER}.command.reload") }
                    .executes { context ->
                        plugin.reloadPlugin()
                        context.source.sender.sendMessage("${plugin.name} configuration reloaded.")
                        1
                    }
            )
            .build()
}
