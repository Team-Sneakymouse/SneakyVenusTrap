package com.emptyplugin.commands

import com.emptyplugin.EmptyPlugin
import org.bukkit.command.Command

/**
 * Base class for plugin commands. Sets permission to
 * `{identifier}.command.{name}` by default.
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${EmptyPlugin.IDENTIFIER}.command.$name"
    }
}
