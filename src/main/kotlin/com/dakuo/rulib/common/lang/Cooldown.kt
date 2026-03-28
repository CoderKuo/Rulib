package com.dakuo.rulib.common.lang

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 冷却管理器
 *
 * 使用方式一：实例化（预配置冷却时间）
 * val cd = Cooldown(3000L) // 3秒冷却
 * if (cd.check(player.uniqueId)) {
 *     player.sendMessage("冷却中...")
 *     return
 * }
 * cd.set(player.uniqueId)
 * // 执行逻辑...
 *
 * 使用方式二：配合 Duration
 * val cd = Cooldown("5m30s".parseDuration().toMillis())
 *
 * 使用方式三：全局静态方法（检查+自动设置，一行搞定）
 * if (Cooldown.check(player.uniqueId, "skill_fire", 5000L)) {
 *     player.sendMessage("冷却中，剩余 ${Cooldown.remaining(player.uniqueId, "skill_fire").formatDuration()}")
 *     return
 * }
 * // 执行逻辑...（冷却已自动设置）
 */
class Cooldown(private val duration: Long) {

    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    /**
     * 检查是否在冷却中
     * @return true 表示冷却中，不可执行
     */
    fun check(uuid: UUID): Boolean {
        val expireTime = cooldowns[uuid] ?: return false
        if (System.currentTimeMillis() >= expireTime) {
            cooldowns.remove(uuid)
            return false
        }
        return true
    }

    /**
     * 设置冷却（使用构造时的 duration）
     */
    fun set(uuid: UUID) {
        cooldowns[uuid] = System.currentTimeMillis() + duration
    }

    /**
     * 设置自定义时长的冷却
     */
    fun set(uuid: UUID, customDuration: Long) {
        cooldowns[uuid] = System.currentTimeMillis() + customDuration
    }

    /**
     * 获取剩余冷却时间（毫秒），无冷却返回 0
     */
    fun remaining(uuid: UUID): Long {
        val expireTime = cooldowns[uuid] ?: return 0L
        val remain = expireTime - System.currentTimeMillis()
        if (remain <= 0) {
            cooldowns.remove(uuid)
            return 0L
        }
        return remain
    }

    /**
     * 重置冷却
     */
    fun reset(uuid: UUID) {
        cooldowns.remove(uuid)
    }

    /**
     * 清理所有已过期数据
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value <= now }
    }

    companion object {

        private val globalCooldowns = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Long>>()

        /**
         * 全局静态冷却检查
         *
         * 如果在冷却中返回 true（不做任何操作）
         * 如果不在冷却中返回 false 并自动设置冷却
         *
         * 典型用法:
         * if (Cooldown.check(player.uniqueId, "my_skill", 3000L)) {
         *     player.sendMessage("冷却中...")
         *     return
         * }
         * // 不在冷却中，冷却已自动设置，执行逻辑...
         */
        fun check(uuid: UUID, key: String, duration: Long): Boolean {
            val map = globalCooldowns.computeIfAbsent(key) { ConcurrentHashMap() }
            val expireTime = map[uuid]
            val now = System.currentTimeMillis()
            if (expireTime != null && now < expireTime) {
                return true
            }
            map[uuid] = now + duration
            return false
        }

        /**
         * 获取全局冷却剩余时间（毫秒）
         */
        fun remaining(uuid: UUID, key: String): Long {
            val map = globalCooldowns[key] ?: return 0L
            val expireTime = map[uuid] ?: return 0L
            val remain = expireTime - System.currentTimeMillis()
            if (remain <= 0) {
                map.remove(uuid)
                return 0L
            }
            return remain
        }

        /**
         * 重置全局冷却
         */
        fun reset(uuid: UUID, key: String) {
            globalCooldowns[key]?.remove(uuid)
        }

        /**
         * 清理所有全局冷却的过期数据
         */
        fun cleanup() {
            val now = System.currentTimeMillis()
            globalCooldowns.values.forEach { map ->
                map.entries.removeIf { it.value <= now }
            }
            globalCooldowns.entries.removeIf { it.value.isEmpty() }
        }
    }
}
