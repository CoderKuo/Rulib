package com.dakuo.rulib.common.world

import com.dakuo.rulib.common.registerListener
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.EnumMap

/**
 * 实体空间索引
 *
 * 维护按 Chunk 分桶 + 按 EntityType 分桶的双索引，支持高效的范围查询。
 * 自动监听实体生成/移除/Chunk 加载卸载事件维护索引。
 *
 * 相比 World.getNearbyEntities() 的优势：
 * - 按 Chunk 桶查询，只遍历相关 Chunk 内的实体
 * - 按类型索引，O(1) 拿到全服某类型的所有实体
 * - 结合 QueryCache 同 tick 不重复查询
 * - 距离计算使用 distanceSquared 避免开方
 *
 * 使用示例:
 * // 初始化（插件 onEnable 时调用一次）
 * EntityIndex.initialize()
 *
 * // 球形范围查询
 * val nearby = EntityIndex.nearby(player.location, 10.0)
 *
 * // 带类型过滤
 * val zombies = EntityIndex.nearby(player.location, 15.0, EntityType.ZOMBIE)
 *
 * // 全服某类型实体（O(1)）
 * val allItems = EntityIndex.allOfType(world, EntityType.DROPPED_ITEM)
 *
 * // Chunk 级查询
 * val inChunk = EntityIndex.inChunk(chunk)
 * val playersInChunk = EntityIndex.inChunk(chunk, EntityType.PLAYER)
 */
object EntityIndex {

    // Chunk 桶索引：ChunkKey → 实体集合
    private val chunkIndex = HashMap<Long, MutableSet<Entity>>(256)

    // 类型索引：World → EntityType → 实体集合
    private val typeIndex = HashMap<String, EnumMap<EntityType, MutableSet<Entity>>>()

    private var initialized = false

    /**
     * 初始化索引（插件 onEnable 时调用）
     *
     * 扫描所有已加载世界的实体建立初始索引，并注册事件监听维护索引。
     * 重复调用安全（幂等）。
     */
    fun initialize() {
        if (initialized) return
        initialized = true

        // 注册事件
        registerListener<EntitySpawnEvent> { event ->
            addEntity(event.entity)
        }

        registerListener<EntityDeathEvent> { event ->
            removeEntity(event.entity)
        }

        registerListener<ChunkLoadEvent> { event ->
            for (entity in event.chunk.entities) {
                addEntity(entity)
            }
        }

        registerListener<ChunkUnloadEvent> { event ->
            val key = chunkKey(event.chunk)
            val entities = chunkIndex.remove(key) ?: return@registerListener
            for (entity in entities) {
                removeFromTypeIndex(entity)
            }
        }

        // 构建初始索引
        org.bukkit.Bukkit.getWorlds().forEach { world ->
            for (chunk in world.loadedChunks) {
                for (entity in chunk.entities) {
                    addEntity(entity)
                }
            }
        }
    }

    /**
     * 球形范围查询
     *
     * 仅遍历覆盖的 Chunk 桶内的实体，使用 distanceSquared 避免开方。
     *
     * @param center 中心点
     * @param radius 半径
     * @return 范围内的实体列表
     */
    fun nearby(center: Location, radius: Double): List<Entity> {
        val world = center.world ?: return emptyList()
        val radiusSq = radius * radius
        val result = mutableListOf<Entity>()

        val minCX = ((center.x - radius).toInt()) shr 4
        val maxCX = ((center.x + radius).toInt()) shr 4
        val minCZ = ((center.z - radius).toInt()) shr 4
        val maxCZ = ((center.z + radius).toInt()) shr 4

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val key = chunkKey(world.name, cx, cz)
                val entities = chunkIndex[key] ?: continue
                for (entity in entities) {
                    if (!entity.isValid) continue
                    if (entity.location.distanceSquared(center) <= radiusSq) {
                        result.add(entity)
                    }
                }
            }
        }
        return result
    }

    /**
     * 球形范围查询（带类型过滤）
     */
    fun nearby(center: Location, radius: Double, type: EntityType): List<Entity> {
        return nearby(center, radius).filter { it.type == type }
    }

    /**
     * 球形范围查询（带缓存）
     *
     * 同一 tick 内，相同的 center+radius 查询会返回缓存结果。
     */
    fun cachedNearby(center: Location, radius: Double): List<Entity> {
        val cacheKey = NearbyKey(center.world?.name ?: "", center.blockX, center.blockY, center.blockZ, radius)
        return QueryCache.getOrPut(cacheKey) {
            nearby(center, radius)
        }
    }

    /**
     * 全服某类型的所有实体（O(1) 获取集合引用）
     *
     * @param world 目标世界
     * @param type 实体类型
     * @return 该世界该类型的所有实体（只读视图）
     */
    fun allOfType(world: World, type: EntityType): Set<Entity> {
        return typeIndex[world.name]?.get(type) ?: emptySet()
    }

    /**
     * 全服某类型的所有实体（所有世界）
     */
    fun allOfType(type: EntityType): List<Entity> {
        return typeIndex.values.flatMap { it[type] ?: emptySet() }
    }

    /**
     * 单 Chunk 内的所有实体
     */
    fun inChunk(chunk: Chunk): Set<Entity> {
        return chunkIndex[chunkKey(chunk)] ?: emptySet()
    }

    /**
     * 单 Chunk 内指定类型的实体
     */
    fun inChunk(chunk: Chunk, type: EntityType): List<Entity> {
        return (chunkIndex[chunkKey(chunk)] ?: emptySet()).filter { it.type == type }
    }

    /**
     * 索引中的总实体数（调试用）
     */
    fun size(): Int = chunkIndex.values.sumOf { it.size }

    /**
     * 清空索引（插件 onDisable 时调用）
     */
    fun clear() {
        chunkIndex.clear()
        typeIndex.clear()
    }

    // ===== 内部方法 =====

    private fun addEntity(entity: Entity) {
        val chunk = entity.location.chunk
        val key = chunkKey(chunk)
        chunkIndex.getOrPut(key) { mutableSetOf() }.add(entity)

        val worldName = entity.world.name
        typeIndex.getOrPut(worldName) { EnumMap(EntityType::class.java) }
            .getOrPut(entity.type) { mutableSetOf() }
            .add(entity)
    }

    private fun removeEntity(entity: Entity) {
        val chunk = entity.location.chunk
        val key = chunkKey(chunk)
        chunkIndex[key]?.remove(entity)
        removeFromTypeIndex(entity)
    }

    private fun removeFromTypeIndex(entity: Entity) {
        typeIndex[entity.world.name]?.get(entity.type)?.remove(entity)
    }

    private fun chunkKey(chunk: Chunk): Long = chunkKey(chunk.world.name, chunk.x, chunk.z)

    private fun chunkKey(world: String, cx: Int, cz: Int): Long {
        // 将 world hash + chunk 坐标编码为 long
        return (world.hashCode().toLong() shl 32) or ((cx.toLong() and 0xFFFF) shl 16) or (cz.toLong() and 0xFFFF)
    }

    // 查询缓存键
    private data class NearbyKey(val world: String, val x: Int, val y: Int, val z: Int, val radius: Double)
}
