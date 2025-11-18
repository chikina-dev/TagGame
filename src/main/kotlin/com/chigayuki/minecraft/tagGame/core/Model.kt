package com.chigayuki.minecraft.tagGame.core

import java.util.UUID

enum class GameState { READY, PLAYING, FINISHED }
enum class PlayerState { RUNNER, TAGGER, SPECTATOR }

data class TagPlayer(
    val id: UUID,
    var name: String,
    var state: PlayerState = PlayerState.RUNNER,
    var remainTime: Int = 120,
    var eliminated: Boolean = false,
    var snowballCooldownUntil: Long = 0L,
)

enum class TagTransferCause { STICK, SNOWBALL }

sealed class TagEvent {
    data object GameStarted : TagEvent()
    data class TaggerAssigned(val playerId: UUID, val applyImmunity: Boolean) : TagEvent()
    data class TimerTick(val taggerId: UUID, val remain: Int) : TagEvent()
    data class PlayerEliminated(val playerId: UUID, val reason: String) : TagEvent()
    data class TagTransferred(val fromId: UUID, val toId: UUID, val cause: TagTransferCause) : TagEvent()
    data class GameFinished(val winnerId: UUID?) : TagEvent()
}

