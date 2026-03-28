package com.dakuo.rulib.common.world

import com.dakuo.rulib.common.Cuboid
import org.bukkit.Chunk
import org.bukkit.World

/**
 * Chunk 级分帧迭代器
 *
 * 对 World 的已加载 Chunk 进行分帧遍历，每 tick 处理 N 个 Chunk。
 * 启动时对 Chunk 列表做快照，遍历过程中新加载/卸载的 Chunk 不影响本轮。
 *
 * 使用示例:
 * // 遍历全服所有已加载 Chunk
 * ChunkWalker.walk(world) {
 *     perTick = 50
 *     process { chunk ->
 *         // 用户自己决定在每个 Chunk 里做什么
 *         for (entity in chunk.entities) { ... }
 *         for (tile in chunk.tileEntities) { ... }
 *     }
 *     complete { count -> logger.info("遍历了 $count 个区块") }
 * }
 *
 * // 遍历指定区域覆盖的 Chunk
 * ChunkWalker.walk(world, myCuboid) {
 *     perTick = 20
 *     process { chunk ->
 *         chunk.tileEntities
 *             .filterIsInstance<Container>()
 *             .forEach { container -> ... }
 *     }
 * }
 *
 * // 取消遍历
 * val walker = ChunkWalker.walk(world) { ... }
 * walker.cancel()
 *
 * // 查询进度
 * walker.progress    // 0.0 ~ 1.0
 * walker.processed   // 已处理 Chunk 数
 */
object ChunkWalker {

    /**
     * 遍历指定世界的所有已加载 Chunk
     *
     * @param world 目标世界
     * @param block DSL 配置
     * @return 可取消/可查询进度的 TickTask
     */
    fun walk(world: World, block: TickTask.Builder<Chunk>.() -> Unit): TickTask<Chunk> {
        val chunks = world.loadedChunks.toList()
        return TickTask.create(chunks, block)
    }

    /**
     * 遍历指定世界中特定区域覆盖的已加载 Chunk
     *
     * 仅选择与 Cuboid 区域重叠的 Chunk，跳过区域外的 Chunk。
     *
     * @param world 目标世界
     * @param region 限定区域
     * @param block DSL 配置
     */
    fun walk(world: World, region: Cuboid, block: TickTask.Builder<Chunk>.() -> Unit): TickTask<Chunk> {
        val minCX = region.minX shr 4
        val maxCX = region.maxX shr 4
        val minCZ = region.minZ shr 4
        val maxCZ = region.maxZ shr 4

        val chunks = world.loadedChunks.filter { chunk ->
            chunk.x in minCX..maxCX && chunk.z in minCZ..maxCZ
        }
        return TickTask.create(chunks, block)
    }

    /**
     * 遍历多个世界的所有已加载 Chunk
     *
     * @param worlds 目标世界列表
     * @param block DSL 配置
     */
    fun walkAll(worlds: List<World>, block: TickTask.Builder<Chunk>.() -> Unit): TickTask<Chunk> {
        val chunks = worlds.flatMap { it.loadedChunks.toList() }
        return TickTask.create(chunks, block)
    }
}
