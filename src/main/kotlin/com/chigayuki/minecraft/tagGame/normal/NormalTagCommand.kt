package com.chigayuki.minecraft.tagGame.normal

import com.chigayuki.minecraft.tagGame.TagGame
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class NormalTagCommand(
    private val plugin: TagGame
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /$label start")
            return true
        }
        when (args[0].lowercase()) {
            "start" -> {
                if (!sender.hasPermission("tag.start")) {
                    sender.sendMessage("権限がありません")
                    return true
                }
                if (sender !is Player) {
                    sender.sendMessage("プレイヤーのみ実行可能です")
                    return true
                }
                plugin.game.startGame(sender)
                return true
            }
            else -> sender.sendMessage("Usage: /$label start")
        }
        return true
    }
}