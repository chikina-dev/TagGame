package com.chigayuki.minecraft.tagGame

import com.chigayuki.minecraft.tagGame.normal.NormalTagCommand
import com.chigayuki.minecraft.tagGame.normal.NormalTagGame
import com.chigayuki.minecraft.tagGame.normal.NormalTagListeners
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class TagGame : JavaPlugin() {
    lateinit var game: NormalTagGame
        private set

    override fun onEnable() {
        game = NormalTagGame(this)
        // command
        getCommand("tag")?.setExecutor(NormalTagCommand(this))
        // listeners
        server.pluginManager.registerEvents(NormalTagListeners(this), this)
        logger.info("TagGame enabled")
    }

    override fun onDisable() {
        // 安全停止
        if (::game.isInitialized) {
            game.stopGame(broadcast = false)
        }
        Bukkit.getScheduler().cancelTasks(this)
        logger.info("TagGame disabled")
    }
}
