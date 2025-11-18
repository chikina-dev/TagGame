package com.chigayuki.minecraft.tagGame.normal

import org.bukkit.GameMode
import org.bukkit.entity.Player

class NormalTagPlayer(
    val player: Player
) {
    var state: NormalTagPlayerState = NormalTagPlayerState.RUNNER
    var remainTime: Int = REMAIN_TIME_DEFAULT
    var eliminated: Boolean = false
    var snowballCooldownUntil: Long = 0L

    fun setTagger() {
        state = NormalTagPlayerState.TAGGER
    }

    fun setRunner() {
        state = NormalTagPlayerState.RUNNER
    }

    fun setSpectator() {
        state = NormalTagPlayerState.SPECTATOR
        eliminated = true
        if (player.gameMode != GameMode.SPECTATOR) {
            player.gameMode = GameMode.SPECTATOR
        }
    }

    fun isAlive(): Boolean = !eliminated && (state == NormalTagPlayerState.RUNNER || state == NormalTagPlayerState.TAGGER)

    companion object {
        private const val REMAIN_TIME_DEFAULT = 120
    }
}