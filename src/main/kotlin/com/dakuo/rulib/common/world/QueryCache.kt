package com.dakuo.rulib.common.world

import taboolib.common.platform.function.submit

/**
 * 同 Tick 查询结果缓存
 *
 * MC 主线程每 tick 内，世界状态不变（实体不移动、方块不变化）。
 * 多次对同一区域的查询可以复用结果，避免重复遍历。
 *
 * 每 tick 开始时自动清空缓存，无需手动管理。
 *
 * 使用示例:
 * // 缓存实体查询结果（同一 tick 内相同参数不重复搜索）
 * val entities = QueryCache.getOrPut("nearby:${chunkX}:${chunkZ}") {
 *     chunk.entities.toList()
 * }
 *
 * // 缓存任意计算结果
 * val result = QueryCache.getOrPut(myKey) { expensiveComputation() }
 *
 * // 手动清空（通常不需要，每 tick 自动清空）
 * QueryCache.clear()
 */
object QueryCache {

    private var cache = HashMap<Any, Any?>(64)
    private var initialized = false

    /**
     * 获取缓存值，不存在时执行 compute 并缓存结果
     *
     * @param key 缓存键（任意类型，需正确实现 hashCode/equals）
     * @param compute 计算函数（仅在缓存未命中时调用）
     * @return 缓存或新计算的值
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrPut(key: Any, compute: () -> T): T {
        ensureInitialized()
        val existing = cache[key]
        if (existing != null || cache.containsKey(key)) {
            return existing as T
        }
        val value = compute()
        cache[key] = value
        return value
    }

    /**
     * 检查缓存中是否存在指定键
     */
    fun contains(key: Any): Boolean {
        return cache.containsKey(key)
    }

    /**
     * 手动放入缓存
     */
    fun put(key: Any, value: Any?) {
        ensureInitialized()
        cache[key] = value
    }

    /**
     * 获取缓存值（可能为 null）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: Any): T? {
        return cache[key] as T?
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        cache.clear()
    }

    /**
     * 当前缓存条目数
     */
    fun size(): Int = cache.size

    private fun ensureInitialized() {
        if (initialized) return
        initialized = true
        // 每 tick 清空缓存（主线程调度，period=1 表示每 tick）
        submit(period = 1L) {
            if (cache.isNotEmpty()) {
                cache.clear()
            }
        }
    }
}
