package gg.bitesize.template.managers

import gg.bitesize.template.Main.Companion.INSTANCE
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

object ConfigManager {

    // Read through the plugin each time: reloadConfig() replaces the FileConfiguration instance
    private val config: FileConfiguration get() = INSTANCE.config

    private lateinit var messagesFile: File
    lateinit var messages: FileConfiguration
        private set

    private const val MESSAGE_NOT_FOUND = "<#F7567C>Message not found. Check <gray>messages.yml<#F7567C>."

    fun loadConfigs() {
        INSTANCE.saveDefaultConfig()

        messagesFile = File(INSTANCE.dataFolder, "messages.yml")
        if (!messagesFile.exists()) {
            INSTANCE.saveResource("messages.yml", false)
        }
        loadMessages()
    }

    fun reloadConfigs() {
        INSTANCE.reloadConfig()

        loadMessages()
    }

    private fun loadMessages() {
        messages = YamlConfiguration.loadConfiguration(messagesFile)

        // Fall back to the bundled file, so keys added in updates work without regenerating messages.yml
        INSTANCE.getResource("messages.yml")?.reader(Charsets.UTF_8)?.use { reader ->
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader))
        }

        FormatManager.reload()
    }

    fun getString(path: String, default: String = ""): String {
        return config.getString(path) ?: default
    }

    fun getInt(path: String, default: Int = 0): Int {
        return config.getInt(path, default)
    }

    fun getBoolean(path: String, default: Boolean = false): Boolean {
        return config.getBoolean(path, default)
    }

    fun getDouble(path: String, default: Double = 0.0): Double {
        return config.getDouble(path, default)
    }

    fun getList(path: String): List<String> {
        return config.getStringList(path)
    }

    fun getPrefix(): String {
        return messages.getString("prefix") ?: ""
    }

    fun getErrorPrefix(): String {
        return messages.getString("error-prefix") ?: ""
    }

    fun getMessage(path: String, default: String = MESSAGE_NOT_FOUND): String {
        return messages.getString(path) ?: default
    }

    fun getMessageList(path: String): List<String> {
        return messages.getStringList(path)
    }

}
