package com.dakuo.rulib.common

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.function.submitAsync
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家数据缓存层
 *
 * 自动在玩家进服时异步加载数据，退服时异步保存并清理缓存。
 *
 * 使用示例:
 * data class PlayerData(var coins: Double = 0.0, var level: Int = 1)
 *
 * val cache = PlayerCache(
 *     load = { uuid ->
 *         userTable.selectOne(
 *             where = "uuid = ?",
 *             mapper = { rs -> PlayerData(rs.getDouble("coins"), rs.getInt("level")) },
 *             params = arrayOf(uuid.toString())
 *         ) ?: PlayerData()
 *     },
 *     save = { uuid, data ->
 *         userTable.insertOrUpdate(
 *             data = mapOf("uuid" to uuid.toString(), "coins" to data.coins, "level" to data.level),
 *             keys = listOf("uuid")
 *         )
 *     }
 * )
 *
 * // 获取/修改数据
 * cache[player].coins += 100.0
 *
 * // 手动保存
 * cache.save(player)
 *
 * // 插件 onDisable 时同步保存
 * cache.saveAllAndClear()
 */
class PlayerCache<T>(
    private val load: (UUID) -> T,
    private val save: (UUID, T) -> Unit
) {
    private val data = ConcurrentHashMap<UUID, T>()

    init {
        registerListener<PlayerJoinEvent> { event ->
            val uuid = event.player.uniqueId
            submitAsync {
                runCatching {
                    val loaded = load(uuid)
                    data[uuid] = loaded
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
        registerListener<PlayerQuitEvent> { event ->
            val uuid = event.player.uniqueId
            val playerData = data.remove(uuid) ?: return@registerListener
            submitAsync {
                runCatching {
                    save(uuid, playerData)
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    /**
     * 通过 Player 获取缓存数据
     * 如果数据尚未异步加载完成，会同步加载（兜底）
     */
    operator fun get(player: Player): T = get(player.uniqueId)

    /**
     * 通过 UUID 获取缓存数据
     * 如果数据尚未异步加载完成，会同步加载（兜底）
     */
    operator fun get(uuid: UUID): T {
        return data.computeIfAbsent(uuid) { load(it) }
    }

    /**
     * 检查是否已有缓存数据
     */
    fun has(uuid: UUID): Boolean = data.containsKey(uuid)

    /**
     * 手动保存指定玩家数据（异步）
     */
    fun save(player: Player) = save(player.uniqueId)

    /**
     * 手动保存指定玩家数据（异步）
     */
    fun save(uuid: UUID) {
        val playerData = data[uuid] ?: return
        submitAsync {
            runCatching {
                save(uuid, playerData)
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    /**
     * 保存所有在线玩家数据（异步）
     */
    fun saveAll() {
        data.forEach { (uuid, playerData) ->
            submitAsync {
                runCatching {
                    save(uuid, playerData)
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    /**
     * 保存所有数据并清空缓存（同步，用于插件关闭时调用）
     */
    fun saveAllAndClear() {
        data.forEach { (uuid, playerData) ->
            runCatching {
                save(uuid, playerData)
            }.onFailure {
                it.printStackTrace()
            }
        }
        data.clear()
    }

    /**
     * 获取当前缓存的数据量
     */
    fun size(): Int = data.size
}
