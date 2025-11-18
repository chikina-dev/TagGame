package com.chigayuki.minecraft.tagGame.normal

import com.chigayuki.minecraft.tagGame.TagGame
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class NormalTagListeners(
    private val plugin: TagGame
) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        plugin.game.handleJoin(e.player)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        plugin.game.handleQuit(e.player)
    }

    @EventHandler
    fun onDamageByEntity(e: EntityDamageByEntityEvent) {
        val victim = e.entity as? Player ?: return
        val damager = e.damager
        // 棒での殴打
        if (damager is Player) {
            val item = damager.inventory.itemInMainHand.type
            if (item == Material.STICK) {
                plugin.game.onHitByStick(damager, victim)
                e.isCancelled = true
                return
            }
        }
        // 雪玉によるヒット(EntityDamageByEntityでも来る)
        if (damager is Snowball) {
            val shooter = damager.shooter as? Player ?: return
            plugin.game.onHitBySnowball(shooter, victim)
            e.isCancelled = true
        }
    }

    // 互換: ProjectileHitEvent でも保険的に処理
    @EventHandler
    fun onProjectileHit(e: ProjectileHitEvent) {
        val proj: Projectile = e.entity
        if (proj !is Snowball) return
        val shooter = proj.shooter as? Player ?: return
        val victim = e.hitEntity as? Player ?: return
        plugin.game.onHitBySnowball(shooter, victim)
    }
}
