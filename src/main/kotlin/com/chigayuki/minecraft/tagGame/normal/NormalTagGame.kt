package com.chigayuki.minecraft.tagGame.normal

import com.chigayuki.minecraft.tagGame.TagGame
import com.chigayuki.minecraft.tagGame.core.*
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class NormalTagGame(
    private val plugin: TagGame
) {
    val gameName = "鬼ごっこ"

    // ドメインエンジン
    private var engine: TagEngine = TagEngine(emptyList())

    // Bukkit側の参照: UUID -> Player
    private val onlinePlayers: MutableMap<UUID, Player> = mutableMapOf()

    private var countdownTask: BukkitTask? = null
    private var compassTask: BukkitTask? = null

    val players: Map<UUID, NormalTagPlayer> get() =
        engine.players.mapValues { NormalTagPlayer(onlinePlayers[it.key]!!).apply {
            eliminated = it.value.eliminated
            state = when (it.value.state) {
                PlayerState.RUNNER -> NormalTagPlayerState.RUNNER
                PlayerState.TAGGER -> NormalTagPlayerState.TAGGER
                PlayerState.SPECTATOR -> NormalTagPlayerState.SPECTATOR
            }
            remainTime = it.value.remainTime
            snowballCooldownUntil = it.value.snowballCooldownUntil
        } }

    var currentTaggerId: UUID?
        get() = engine.currentTaggerId
        set(_) {}

    fun startGame(sender: Player) {
        // プレイヤー初期登録
        onlinePlayers.clear()
        Bukkit.getOnlinePlayers().forEach { p ->
            onlinePlayers[p.uniqueId] = p
        }
        engine = TagEngine(Bukkit.getOnlinePlayers().map { TagPlayer(it.uniqueId, it.name) })
        engine.onEvent = { evt -> handleEngineEvent(evt) }

        // クライアントセットアップ
        Bukkit.getOnlinePlayers().forEach { p ->
            p.gameMode = GameMode.SURVIVAL
            p.inventory.clear()
            p.activePotionEffects.forEach { pe -> p.removePotionEffect(pe.type) }
        }

        engine.startGame()
        startCountdownTask()
        startCompassTask()
        broadcast("ゲーム開始！")
    }

    fun stopGame(broadcast: Boolean = true) {
        engine.stopGame()
        cancelTasks()
        if (broadcast) {
            Bukkit.getOnlinePlayers().forEach { it.sendMessage("[$gameName] ゲーム終了") }
        }
        // 終了時の後処理: スペクテーター→クリエイティブ
        Bukkit.getOnlinePlayers().forEach { p ->
            if (p.gameMode == GameMode.SPECTATOR) p.gameMode = GameMode.CREATIVE
        }
    }

    fun addOrGetPlayer(player: Player): NormalTagPlayer {
        onlinePlayers[player.uniqueId] = player
        if (!engine.players.containsKey(player.uniqueId)) {
            engine.players[player.uniqueId] = TagPlayer(player.uniqueId, player.name)
        }
        return NormalTagPlayer(player)
    }

    fun removePlayer(uid: UUID, silent: Boolean = false) {
        engine.players.remove(uid)
        onlinePlayers.remove(uid)
        if (!silent) engine.maybeFinish()
    }

    fun handleJoin(player: Player) {
        onlinePlayers[player.uniqueId] = player
        when (engine.state) {
            GameState.READY -> addOrGetPlayer(player)
            GameState.PLAYING -> {
                addOrGetPlayer(player)
                engine.eliminate(player.uniqueId, "途中参加")
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("ゲーム進行中のため観戦になります")
            }
            GameState.FINISHED -> {
                addOrGetPlayer(player)
            }
        }
    }

    fun handleQuit(player: Player) {
        engine.handleQuit(player.uniqueId)
        // 鬼が抜けたらエンジン内でcurrentTaggerはnull化済み、tickで再割当
        engine.maybeFinish()
    }

    private fun handleEngineEvent(evt: TagEvent) {
        when (evt) {
            is TagEvent.GameStarted -> {
                // 初期の鬼割り当てはエンジンが行う
            }
            is TagEvent.TaggerAssigned -> {
                val p = onlinePlayers[evt.playerId] ?: return
                // 鬼の見た目/アイテム
                removeTaggerKitFromAll()
                giveTaggerKit(p)
                // 盲目+鈍足
                if (evt.applyImmunity) {
                    p.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true))
                    p.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, false, true))
                }
                p.playSound(p.location, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f)
                broadcast("${p.name} が鬼になりました！")
            }
            is TagEvent.TimerTick -> {
                // アクションバー更新（鬼だけ）
                onlinePlayers[evt.taggerId]?.sendActionBar("鬼タイマー: ${evt.remain}s")
            }
            is TagEvent.PlayerEliminated -> {
                val p = onlinePlayers[evt.playerId] ?: return
                p.inventory.clear()
                p.gameMode = GameMode.SPECTATOR
                p.sendTitle("脱落", evt.reason, 10, 40, 10)
                broadcast("${p.name} は脱落しました")
            }
            is TagEvent.TagTransferred -> {
                val from = onlinePlayers[evt.fromId]?.name ?: "?"
                val to = onlinePlayers[evt.toId]?.name ?: "?"
                broadcast("$from -> $to に鬼が移りました（${evt.cause}）")
            }
            is TagEvent.GameFinished -> {
                val winner = evt.winnerId?.let { onlinePlayers[it]?.name } ?: "該当なし"
                broadcast("勝者: $winner")
            }
        }
    }

    fun startCountdownTask() {
        countdownTask?.cancel()
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            engine.tickSecond()
        }, 20L, 20L)
    }

    fun startCompassTask() {
        compassTask?.cancel()
        compassTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val taggerId = engine.currentTaggerId ?: return@Runnable
            val tagger = onlinePlayers[taggerId] ?: return@Runnable
            val target = engine.alivePlayers(exclude = taggerId).randomOrNull() ?: return@Runnable
            if (!tagger.inventory.contains(Material.COMPASS)) {
                tagger.inventory.addItem(ItemStack(Material.COMPASS))
            }
            val targetPlayer = onlinePlayers[target.id] ?: return@Runnable
            tagger.compassTarget = targetPlayer.location
            tagger.sendMessage("コンパスが更新されました: ${target.name} の方向")
        }, 0L, 200L)
    }

    fun cancelTasks() {
        countdownTask?.cancel(); countdownTask = null
        compassTask?.cancel(); compassTask = null
    }

    fun onHitByStick(attacker: Player, victim: Player) {
        if (engine.currentTaggerId != attacker.uniqueId) return
        engine.transferTag(victim.uniqueId, TagTransferCause.STICK, checkCooldown = false)
    }

    fun onHitBySnowball(shooter: Player, victim: Player) {
        if (engine.currentTaggerId != shooter.uniqueId) return
        val ok = engine.transferTag(victim.uniqueId, TagTransferCause.SNOWBALL, checkCooldown = true)
        if (!ok) {
            // クールダウン中なら通知
            engine.players[shooter.uniqueId]?.let { tp ->
                val now = System.currentTimeMillis()
                if (now < tp.snowballCooldownUntil) {
                    val remain = ((tp.snowballCooldownUntil - now) + 999) / 1000
                    shooter.sendMessage("雪玉クールダウン中: ${remain}s")
                }
            }
            return
        }
        engine.setSnowballCooldown(shooter.uniqueId, 10)
    }

    private fun removeTaggerKitFromAll() {
        onlinePlayers.values.forEach { removeTaggerKit(it) }
    }

    private fun giveTaggerKit(p: Player) {
        if (!p.inventory.contains(Material.STICK)) p.inventory.addItem(ItemStack(Material.STICK))
        if (!p.inventory.contains(Material.SNOWBALL)) p.inventory.addItem(ItemStack(Material.SNOWBALL, 16))
        if (!p.inventory.contains(Material.COMPASS)) p.inventory.addItem(ItemStack(Material.COMPASS))
    }

    private fun removeTaggerKit(p: Player) {
        p.inventory.remove(Material.STICK)
        p.inventory.remove(Material.SNOWBALL)
        p.inventory.remove(Material.COMPASS)
    }

    private fun broadcast(msg: String) {
        Bukkit.getOnlinePlayers().forEach { it.sendMessage("[$gameName] $msg") }
    }
}