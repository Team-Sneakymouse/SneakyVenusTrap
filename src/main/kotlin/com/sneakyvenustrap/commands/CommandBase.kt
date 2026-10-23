package com.sneakyvenustrap.commands

import com.sneakyvenustrap.SneakyVenusTrap
import org.bukkit.command.Command

/**
 * Base class for plugin commands. Sets permission to
 * `{identifier}.command.{name}` by default.
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${SneakyVenusTrap.IDENTIFIER}.command.$name"
    }
}
