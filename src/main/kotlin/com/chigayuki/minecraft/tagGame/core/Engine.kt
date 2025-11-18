package com.chigayuki.minecraft.tagGame.core

import java.util.UUID
import kotlin.random.Random

class TagEngine(
    players: Collection<TagPlayer>,
    private val timeSourceMillis: () -> Long = { System.currentTimeMillis() },
) {
    val players = players.associateBy { it.id }.toMutableMap()
    var state: GameState = GameState.READY
        private set

    var currentTaggerId: UUID? = null
        private set

    private var immuneUntilMillis: Long = 0L

    // Event sink set by adapter
    var onEvent: (TagEvent) -> Unit = {}

    fun startGame() {
        check(state != GameState.PLAYING) { "already playing" }
        state = GameState.PLAYING
        onEvent(TagEvent.GameStarted)
        // reset players
        players.values.forEach {
            it.eliminated = false
            it.remainTime = 120
            it.state = PlayerState.RUNNER
            it.snowballCooldownUntil = 0L
        }
        // assign first tagger
        assignNewTagger(applyImmunity = true)
    }

    fun stopGame(): UUID? {
        if (state != GameState.PLAYING) return null
        val winner = alivePlayers().singleOrNull()?.id
        state = GameState.FINISHED
        onEvent(TagEvent.GameFinished(winner))
        return winner
    }

    fun alivePlayers(exclude: UUID? = null): List<TagPlayer> =
        players.values.filter { !it.eliminated && (it.state == PlayerState.RUNNER || it.state == PlayerState.TAGGER) && it.id != exclude }

    fun assignNewTagger(applyImmunity: Boolean) {
        val target = alivePlayers().ifEmpty { return }.let { it[Random.nextInt(it.size)] }
        setTagger(target.id, applyImmunity)
    }

    fun setTagger(playerId: UUID, applyImmunity: Boolean) {
        // clear previous
        currentTaggerId?.let { prevId ->
            players[prevId]?.let { prev ->
                if (!prev.eliminated) prev.state = PlayerState.RUNNER
            }
        }
        // set new
        val np = players[playerId] ?: return
        np.state = PlayerState.TAGGER
        currentTaggerId = playerId
        if (applyImmunity) {
            immuneUntilMillis = timeSourceMillis() + 5_000
        } else immuneUntilMillis = 0L
        onEvent(TagEvent.TaggerAssigned(playerId, applyImmunity))
    }

    fun tickSecond() {
        if (state != GameState.PLAYING) return
        val taggerId = currentTaggerId ?: return
        if (timeSourceMillis() < immuneUntilMillis) return
        val tagger = players[taggerId] ?: return
        if (tagger.remainTime <= 0) {
            eliminate(taggerId, "タイマーが0になりました！")
            assignNewTagger(applyImmunity = true)
            maybeFinish()
            return
        }
        tagger.remainTime -= 1
        onEvent(TagEvent.TimerTick(taggerId, tagger.remainTime))
    }

    fun transferTag(toId: UUID, cause: TagTransferCause, checkCooldown: Boolean = true): Boolean {
        val fromId = currentTaggerId ?: return false
        if (toId == fromId) return false
        val fromP = players[fromId] ?: return false
        val toP = players[toId] ?: return false
        if (toP.eliminated || toP.state == PlayerState.SPECTATOR) return false
        if (checkCooldown) {
            val now = timeSourceMillis()
            if (now < fromP.snowballCooldownUntil) return false
        }
        players[fromId]?.state = PlayerState.RUNNER
        players[toId]?.state = PlayerState.TAGGER
        currentTaggerId = toId
        immuneUntilMillis = timeSourceMillis() + 5_000
        onEvent(TagEvent.TagTransferred(fromId, toId, cause))
        onEvent(TagEvent.TaggerAssigned(toId, applyImmunity = true))
        return true
    }

    fun setSnowballCooldown(playerId: UUID, seconds: Long) {
        players[playerId]?.snowballCooldownUntil = timeSourceMillis() + seconds * 1000
    }

    fun eliminate(playerId: UUID, reason: String) {
        val p = players[playerId] ?: return
        p.eliminated = true
        p.state = PlayerState.SPECTATOR
        if (currentTaggerId == playerId) currentTaggerId = null
        onEvent(TagEvent.PlayerEliminated(playerId, reason))
    }

    fun handleQuit(playerId: UUID) {
        val p = players[playerId] ?: return
        eliminate(playerId, "退出")
    }

    fun maybeFinish() {
        val alive = alivePlayers()
        if (alive.size <= 1) {
            onEvent(TagEvent.GameFinished(alive.firstOrNull()?.id))
            state = GameState.FINISHED
        }
    }
}

