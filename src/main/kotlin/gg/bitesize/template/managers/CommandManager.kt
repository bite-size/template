package gg.bitesize.template.managers

import gg.bitesize.template.Main.Companion.INSTANCE
import gg.bitesize.template.commands.BaseCommand
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap

object CommandManager {

    // Spigot doesn't expose the command map in its API, so both are resolved reflectively.
    // CraftServer#getCommandMap is public on Spigot, Paper, and Purpur.
    private val commandMap: CommandMap = Bukkit.getServer().let { server ->
        server.javaClass.getMethod("getCommandMap").invoke(server) as CommandMap
    }
    private val knownCommands: MutableMap<String, Command> = resolveKnownCommands(commandMap)
    private val registeredCommands = mutableSetOf<BaseCommand>()

    private val fallbackPrefix get() = INSTANCE.name.lowercase()

    @Suppress("UNCHECKED_CAST")
    private fun resolveKnownCommands(map: CommandMap): MutableMap<String, Command> {
        // Paper and forks expose a getter; Spigot only has the SimpleCommandMap field
        runCatching {
            return map.javaClass.getMethod("getKnownCommands").invoke(map) as MutableMap<String, Command>
        }

        val field = generateSequence<Class<*>>(map.javaClass) { it.superclass }
            .firstNotNullOfOrNull { type -> runCatching { type.getDeclaredField("knownCommands") }.getOrNull() }
            ?: error("Could not locate the server's known commands.")

        field.isAccessible = true
        return field.get(map) as MutableMap<String, Command>
    }

    fun registerAll() {

    }

    fun register(command: BaseCommand) {
        commandMap.register(fallbackPrefix, command)
        registeredCommands.add(command)
    }

    fun syncCommands() {
        Bukkit.getServer().onlinePlayers.forEach { player ->
            player.updateCommands()
        }
    }

    fun shutdown() {
        registeredCommands.forEach { command ->
            command.unregister(commandMap)

            knownCommands.remove(command.name)
            knownCommands.remove("$fallbackPrefix:${command.name}")
            command.aliases.forEach { alias ->
                knownCommands.remove(alias)
                knownCommands.remove("$fallbackPrefix:$alias")
            }
        }

        registeredCommands.clear()

        INSTANCE.logger.info("Unregistered commands.")
    }

}
