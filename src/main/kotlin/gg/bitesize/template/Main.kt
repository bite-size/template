package gg.bitesize.template

import gg.bitesize.template.managers.CommandManager
import gg.bitesize.template.managers.ConfigManager
import gg.bitesize.template.managers.CooldownManager
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {

    companion object {
        lateinit var INSTANCE: Main
    }

    override fun onLoad() {
        INSTANCE = this
    }

    override fun onEnable() {
        ConfigManager.loadConfigs()

        CommandManager.registerAll()
        CommandManager.syncCommands()
    }

    override fun onDisable() {
        ConfigManager.saveConfigs()

        CommandManager.shutdown()

        CooldownManager.shutdown()
    }

}