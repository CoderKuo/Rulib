package com.dakuo.rulib.common.world

import com.dakuo.rulib.common.Cuboid
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import java.util.EnumSet
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.min

/**
 * 高效方块搜索工具
 *
 * 提供比 World.getBlockAt() 循环更高效的方块搜索：
 * - 按 Chunk 分组，每个 Chunk 只获取一次引用
 * - 尝试通过 NMS 检查 ChunkSection Palette，不包含目标材质则跳过整个 Section（4096 块免检）
 * - NMS 不可用时安静退化为 Bukkit API
 *
 * 使用示例:
 * // 搜索周围 16 格内的钻石矿
 * val diamonds = BlockSearch.find(player.location, 16, Material.DIAMOND_ORE)
 *
 * // 搜索 Cuboid 区域内的箱子
 * val chests = BlockSearch.find(myCuboid, Material.CHEST, Material.BARREL)
 *
 * // 矿脉搜索（连通的相同方块）
 * val vein = BlockSearch.findVein(clickedBlock.location, Material.IRON_ORE, 64)
 *
 * // 检查 Palette 优化是否可用
 * if (BlockSearch.paletteOptimization) { ... }
 */
object BlockSearch {

    /**
     * 是否启用了 NMS Palette 跳过优化
     *
     * 如果为 false，所有搜索使用 Bukkit API 逐块检查（仍按 Chunk 分组优化）
     */
    var paletteOptimization: Boolean = false
        private set

    // NMS 反射缓存
    private var nmsChunkMethod: java.lang.reflect.Method? = null
    private var nmsSectionsField: java.lang.reflect.Field? = null
    private var nmsPaletteMethod: java.lang.reflect.Method? = null

    init {
        try {
            initNmsAccess()
            paletteOptimization = true
        } catch (_: Throwable) {
            paletteOptimization = false
        }
    }

    /**
     * 球形范围内搜索指定材质的方块
     *
     * @param center 中心位置
     * @param radius 搜索半径（方块数）
     * @param materials 目标材质
     * @return 找到的方块列表
     */
    fun find(center: Location, radius: Int, vararg materials: Material): List<Block> {
        val world = center.world ?: return emptyList()
        val targetSet = EnumSet.noneOf(Material::class.java).apply { addAll(materials) }
        val result = mutableListOf<Block>()

        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ
        val radiusSq = radius * radius

        // 按 Chunk 分组处理
        val minCX = (cx - radius) shr 4
        val maxCX = (cx + radius) shr 4
        val minCZ = (cz - radius) shr 4
        val maxCZ = (cz + radius) shr 4

        for (chunkX in minCX..maxCX) {
            for (chunkZ in minCZ..maxCZ) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                val chunk = world.getChunkAt(chunkX, chunkZ)

                // 计算此 Chunk 内的搜索边界
                val blockMinX = max(chunkX shl 4, cx - radius)
                val blockMaxX = min((chunkX shl 4) + 15, cx + radius)
                val blockMinZ = max(chunkZ shl 4, cz - radius)
                val blockMaxZ = min((chunkZ shl 4) + 15, cz + radius)
                val blockMinY = max(world.minHeight, cy - radius)
                val blockMaxY = min(world.maxHeight - 1, cy + radius)

                searchChunk(chunk, targetSet, blockMinX, blockMaxX, blockMinY, blockMaxY, blockMinZ, blockMaxZ, result) { x, y, z ->
                    val dx = x - cx
                    val dy = y - cy
                    val dz = z - cz
                    dx * dx + dy * dy + dz * dz <= radiusSq
                }
            }
        }
        return result
    }

    /**
     * Cuboid 区域内搜索指定材质的方块
     *
     * @param cuboid 搜索区域
     * @param materials 目标材质
     * @return 找到的方块列表
     */
    fun find(cuboid: Cuboid, vararg materials: Material): List<Block> {
        val world = org.bukkit.Bukkit.getWorld(cuboid.world) ?: return emptyList()
        val targetSet = EnumSet.noneOf(Material::class.java).apply { addAll(materials) }
        val result = mutableListOf<Block>()

        val minCX = cuboid.minX shr 4
        val maxCX = cuboid.maxX shr 4
        val minCZ = cuboid.minZ shr 4
        val maxCZ = cuboid.maxZ shr 4

        for (chunkX in minCX..maxCX) {
            for (chunkZ in minCZ..maxCZ) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue
                val chunk = world.getChunkAt(chunkX, chunkZ)

                val blockMinX = max(chunkX shl 4, cuboid.minX)
                val blockMaxX = min((chunkX shl 4) + 15, cuboid.maxX)
                val blockMinZ = max(chunkZ shl 4, cuboid.minZ)
                val blockMaxZ = min((chunkZ shl 4) + 15, cuboid.maxZ)

                searchChunk(chunk, targetSet, blockMinX, blockMaxX, cuboid.minY, cuboid.maxY, blockMinZ, blockMaxZ, result)
            }
        }
        return result
    }

    /**
     * 连通方块搜索（矿脉搜索）
     *
     * 从起始位置开始 BFS，搜索所有相邻的相同材质方块。
     *
     * @param start 起始位置
     * @param material 目标材质
     * @param maxBlocks 最大方块数（防止无限扩展）
     * @return 连通的方块列表
     */
    fun findVein(start: Location, material: Material, maxBlocks: Int = 64): List<Block> {
        val world = start.world ?: return emptyList()
        val startBlock = world.getBlockAt(start)
        if (startBlock.type != material) return emptyList()

        val result = mutableListOf<Block>()
        val visited = HashSet<Long>()
        val queue = LinkedList<Block>()

        queue.add(startBlock)
        visited.add(blockKey(startBlock))

        val neighbors = intArrayOf(-1, 0, 1)

        while (queue.isNotEmpty() && result.size < maxBlocks) {
            val current = queue.poll()
            result.add(current)

            for (dx in neighbors) {
                for (dy in neighbors) {
                    for (dz in neighbors) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        // 仅检查面相邻（6方向），跳过对角
                        if ((dx != 0).toInt() + (dy != 0).toInt() + (dz != 0).toInt() > 1) continue

                        val nx = current.x + dx
                        val ny = current.y + dy
                        val nz = current.z + dz
                        val key = blockKey(nx, ny, nz)

                        if (key in visited) continue
                        visited.add(key)

                        val neighbor = world.getBlockAt(nx, ny, nz)
                        if (neighbor.type == material) {
                            queue.add(neighbor)
                        }
                    }
                }
            }
        }
        return result
    }

    // ===== 内部实现 =====

    /**
     * 在单个 Chunk 的指定范围内搜索方块
     *
     * 如果 Palette 优化可用，先按 Section（16×16×16）检查 Palette，跳过不含目标材质的 Section。
     */
    private fun searchChunk(
        chunk: Chunk,
        targets: EnumSet<Material>,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int,
        result: MutableList<Block>,
        extraFilter: ((x: Int, y: Int, z: Int) -> Boolean)? = null
    ) {
        val world = chunk.world

        // 按 Section（每 16 格高度一个）遍历
        val minSection = minY shr 4
        val maxSection = maxY shr 4

        for (sectionY in minSection..maxSection) {
            // Palette 优化：检查此 Section 是否包含目标材质
            if (paletteOptimization && !sectionContainsMaterial(chunk, sectionY, targets)) {
                continue // 跳过整个 Section（4096 块免检）
            }

            val sectionMinY = max(sectionY shl 4, minY)
            val sectionMaxY = min((sectionY shl 4) + 15, maxY)

            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    for (y in sectionMinY..sectionMaxY) {
                        if (extraFilter != null && !extraFilter(x, y, z)) continue
                        val block = world.getBlockAt(x, y, z)
                        if (block.type in targets) {
                            result.add(block)
                        }
                    }
                }
            }
        }
    }

    /**
     * 通过 NMS 检查 ChunkSection 的 Palette 是否包含目标材质
     */
    private fun sectionContainsMaterial(chunk: Chunk, sectionY: Int, targets: EnumSet<Material>): Boolean {
        return try {
            // 通过 NMS 获取 section，检查 palette
            // 如果反射失败，返回 true（不跳过，安全退化）
            checkPaletteNms(chunk, sectionY, targets)
        } catch (_: Throwable) {
            true // 无法检查，不跳过
        }
    }

    private fun checkPaletteNms(chunk: Chunk, sectionY: Int, targets: EnumSet<Material>): Boolean {
        val handleMethod = nmsChunkMethod ?: return true
        val sectionsField = nmsSectionsField ?: return true

        val nmsChunk = handleMethod.invoke(chunk)
        val sections = sectionsField.get(nmsChunk) as? Array<*> ?: return true

        // section 索引需要偏移（1.18+ 世界最低 Y 可能为负数）
        val minSectionIndex = chunk.world.minHeight shr 4
        val index = sectionY - minSectionIndex
        if (index < 0 || index >= sections.size) return false

        val section = sections[index] ?: return false

        // 尝试调用 hasType 或遍历 palette
        // 不同版本的 NMS 实现不同，这里尽力尝试
        return try {
            val paletteMethod = nmsPaletteMethod
            if (paletteMethod != null) {
                // 某些版本有 maybeHas 方法
                for (target in targets) {
                    val nmsBlock = materialToNmsBlock(target) ?: continue
                    val has = paletteMethod.invoke(section, nmsBlock) as? Boolean ?: true
                    if (has) return true
                }
                false
            } else {
                true // 无法获取 palette 方法，不跳过
            }
        } catch (_: Throwable) {
            true
        }
    }

    private fun materialToNmsBlock(material: Material): Any? {
        return try {
            // CraftBlockData.getState() → NMS BlockState
            val blockData = material.createBlockData()
            val getStateMethod = blockData.javaClass.getMethod("getState")
            getStateMethod.invoke(blockData)
        } catch (_: Throwable) {
            null
        }
    }

    private fun initNmsAccess() {
        // 获取 CraftChunk.getHandle() → NMS LevelChunk
        val craftChunkClass = Class.forName(
            org.bukkit.Bukkit.getServer().javaClass.`package`.name
                .replace("org.bukkit.craftbukkit", "org.bukkit.craftbukkit")
                + ".CraftChunk"
        )
        // 尝试多个可能的方法名
        nmsChunkMethod = try {
            craftChunkClass.getMethod("getHandle")
        } catch (_: Throwable) {
            craftChunkClass.methods.find { it.name == "getHandle" && it.parameterCount == 0 }
        }

        if (nmsChunkMethod != null) {
            val nmsChunkClass = nmsChunkMethod!!.returnType
            // 获取 sections 字段
            nmsSectionsField = nmsChunkClass.declaredFields.find {
                it.type.isArray && it.type.componentType?.simpleName?.contains("Section") == true
            }?.apply { isAccessible = true }

            // 获取 section 的 palette 检查方法
            if (nmsSectionsField != null) {
                val sectionClass = nmsSectionsField!!.type.componentType
                nmsPaletteMethod = try {
                    sectionClass.getMethod("maybeHas", Any::class.java)
                } catch (_: Throwable) {
                    sectionClass.methods.find {
                        it.name == "maybeHas" || (it.name == "a" && it.parameterCount == 1)
                    }
                }
            }
        }
    }

    private fun blockKey(block: Block): Long = blockKey(block.x, block.y, block.z)

    private fun blockKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0
}
