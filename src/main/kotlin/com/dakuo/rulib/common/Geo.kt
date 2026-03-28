package com.dakuo.rulib.common

import org.bukkit.Bukkit
import org.bukkit.Location
import kotlin.math.max
import kotlin.math.min

/**
 * 区域检测工具
 *
 * 提供 Cuboid（立方体）和 Sphere（球体）两种区域类型。
 *
 * 使用示例:
 * // 立方体区域
 * val region = Cuboid(loc1, loc2)
 * if (region.contains(player.location)) {
 *     player.sendMessage("你在区域内")
 * }
 * region.expand(5)           // 向外扩展5格
 * region.volume()            // 体积（方块数）
 * region.forEachBlock { w, x, y, z -> /* 遍历方块 */ }
 *
 * // 球形区域
 * val sphere = Sphere(center, 10.0)
 * sphere.contains(player.location)
 *
 * // 序列化/反序列化（适合配置文件存储）
 * val str = region.serialize()        // "world:0,0,0:10,10,10"
 * val loaded = Cuboid.deserialize(str)
 *
 * // 扩展函数
 * player.location.inRegion(region)
 * player.location.inRegion(sphere)
 */

/**
 * 立方体区域（方块级精度）
 */
class Cuboid(
    val world: String,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int
) {

    /**
     * 通过两个 Location 构建（自动取 min/max）
     */
    constructor(loc1: Location, loc2: Location) : this(
        world = loc1.world?.name ?: "",
        minX = min(loc1.blockX, loc2.blockX),
        minY = min(loc1.blockY, loc2.blockY),
        minZ = min(loc1.blockZ, loc2.blockZ),
        maxX = max(loc1.blockX, loc2.blockX),
        maxY = max(loc1.blockY, loc2.blockY),
        maxZ = max(loc1.blockZ, loc2.blockZ)
    )

    /**
     * 判断位置是否在区域内
     */
    fun contains(loc: Location): Boolean {
        return loc.world?.name == world && contains(loc.blockX, loc.blockY, loc.blockZ)
    }

    /**
     * 判断坐标是否在区域内
     */
    fun contains(x: Int, y: Int, z: Int): Boolean {
        return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }

    /**
     * 向外扩展
     */
    fun expand(amount: Int): Cuboid {
        return Cuboid(world, minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount)
    }

    /**
     * 平移
     */
    fun shift(x: Int, y: Int, z: Int): Cuboid {
        return Cuboid(world, minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z)
    }

    /**
     * 中心点
     */
    fun center(): Location {
        return Location(
            Bukkit.getWorld(world),
            (minX + maxX) / 2.0 + 0.5,
            (minY + maxY) / 2.0 + 0.5,
            (minZ + maxZ) / 2.0 + 0.5
        )
    }

    /**
     * 体积（方块数）
     */
    fun volume(): Int {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
    }

    /**
     * 遍历区域内所有方块坐标
     */
    fun forEachBlock(action: (world: String, x: Int, y: Int, z: Int) -> Unit) {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    action(world, x, y, z)
                }
            }
        }
    }

    /**
     * 序列化为字符串
     * 格式: "world:x1,y1,z1:x2,y2,z2"
     */
    fun serialize(): String = "$world:$minX,$minY,$minZ:$maxX,$maxY,$maxZ"

    override fun toString(): String = "Cuboid($world: $minX,$minY,$minZ -> $maxX,$maxY,$maxZ)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cuboid) return false
        return world == other.world && minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ
    }

    override fun hashCode(): Int {
        var result = world.hashCode()
        result = 31 * result + minX
        result = 31 * result + minY
        result = 31 * result + minZ
        result = 31 * result + maxX
        result = 31 * result + maxY
        result = 31 * result + maxZ
        return result
    }

    companion object {

        /**
         * 从字符串反序列化
         * 格式: "world:x1,y1,z1:x2,y2,z2"
         */
        fun deserialize(str: String): Cuboid {
            val parts = str.split(":")
            require(parts.size == 3) { "无效的 Cuboid 格式: $str" }
            val min = parts[1].split(",").map { it.toInt() }
            val max = parts[2].split(",").map { it.toInt() }
            require(min.size == 3 && max.size == 3) { "无效的坐标格式: $str" }
            return Cuboid(parts[0], min[0], min[1], min[2], max[0], max[1], max[2])
        }
    }
}

/**
 * 球形区域
 */
class Sphere(
    val world: String,
    val centerX: Double, val centerY: Double, val centerZ: Double,
    val radius: Double
) {

    /**
     * 通过 Location 和半径构建
     */
    constructor(center: Location, radius: Double) : this(
        world = center.world?.name ?: "",
        centerX = center.x,
        centerY = center.y,
        centerZ = center.z,
        radius = radius
    )

    private val radiusSq = radius * radius

    /**
     * 判断位置是否在球体内（使用距离平方，避免开方）
     */
    fun contains(loc: Location): Boolean {
        return loc.world?.name == world && contains(loc.x, loc.y, loc.z)
    }

    /**
     * 判断坐标是否在球体内
     */
    fun contains(x: Double, y: Double, z: Double): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        val dz = z - centerZ
        return dx * dx + dy * dy + dz * dz <= radiusSq
    }

    /**
     * 扩展半径
     */
    fun expand(amount: Double): Sphere {
        return Sphere(world, centerX, centerY, centerZ, radius + amount)
    }

    /**
     * 转为外接立方体
     */
    fun toBoundingCuboid(): Cuboid {
        val r = radius.toInt()
        val cx = centerX.toInt()
        val cy = centerY.toInt()
        val cz = centerZ.toInt()
        return Cuboid(world, cx - r, cy - r, cz - r, cx + r, cy + r, cz + r)
    }

    /**
     * 中心点
     */
    fun center(): Location {
        return Location(Bukkit.getWorld(world), centerX, centerY, centerZ)
    }

    /**
     * 序列化为字符串
     * 格式: "world:x,y,z:radius"
     */
    fun serialize(): String = "$world:$centerX,$centerY,$centerZ:$radius"

    override fun toString(): String = "Sphere($world: $centerX,$centerY,$centerZ r=$radius)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sphere) return false
        return world == other.world && centerX == other.centerX && centerY == other.centerY
                && centerZ == other.centerZ && radius == other.radius
    }

    override fun hashCode(): Int {
        var result = world.hashCode()
        result = 31 * result + centerX.hashCode()
        result = 31 * result + centerY.hashCode()
        result = 31 * result + centerZ.hashCode()
        result = 31 * result + radius.hashCode()
        return result
    }

    companion object {

        /**
         * 从字符串反序列化
         * 格式: "world:x,y,z:radius"
         */
        fun deserialize(str: String): Sphere {
            val parts = str.split(":")
            require(parts.size == 3) { "无效的 Sphere 格式: $str" }
            val coords = parts[1].split(",").map { it.toDouble() }
            require(coords.size == 3) { "无效的坐标格式: $str" }
            return Sphere(parts[0], coords[0], coords[1], coords[2], parts[2].toDouble())
        }
    }
}

// ===== 扩展函数 =====

/**
 * 判断位置是否在立方体区域内
 */
fun Location.inRegion(cuboid: Cuboid): Boolean = cuboid.contains(this)

/**
 * 判断位置是否在球形区域内
 */
fun Location.inRegion(sphere: Sphere): Boolean = sphere.contains(this)
