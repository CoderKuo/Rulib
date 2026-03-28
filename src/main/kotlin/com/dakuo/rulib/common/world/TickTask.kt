package com.dakuo.rulib.common.world

import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor

/**
 * 分帧任务调度器
 *
 * 将大量数据的处理拆分到多个 tick 执行，避免单 tick 卡顿。
 * 这是全服扫描/批量操作的核心基础设施。
 *
 * 使用示例:
 * // 每 tick 处理 100 个实体，共 5000 个
 * val task = TickTask.create(entityList) {
 *     perTick = 100
 *     process { entity -> entity.remove() }
 *     complete { count -> logger.info("清理完成: $count") }
 * }
 *
 * // 查询进度
 * task.progress     // 0.0 ~ 1.0
 * task.processed    // 已处理数量
 * task.total        // 总数量
 * task.isRunning    // 是否运行中
 *
 * // 取消任务
 * task.cancel()
 *
 * // 带错误处理
 * TickTask.create(chunks) {
 *     perTick = 50
 *     process { chunk -> scanChunk(chunk) }
 *     error { item, ex -> logger.warning("处理失败: $ex") }
 *     progress { done, total -> actionBar(player, "进度: $done/$total") }
 *     complete { count -> player.sendMessage("完成，共处理 $count 项") }
 * }
 */
class TickTask<T> private constructor(
    private val items: List<T>,
    private val itemsPerTick: Int,
    private val processAction: ((T) -> Unit)?,
    private val completeAction: ((Int) -> Unit)?,
    private val errorAction: ((T, Throwable) -> Unit)?,
    private val progressAction: ((processed: Int, total: Int) -> Unit)?
) {

    /** 总数量 */
    val total: Int = items.size

    /** 已处理数量 */
    @Volatile
    var processed: Int = 0
        private set

    /** 是否正在运行 */
    @Volatile
    var isRunning: Boolean = true
        private set

    /** 进度（0.0 ~ 1.0） */
    val progress: Double
        get() = if (total == 0) 1.0 else processed.toDouble() / total

    private var index = 0

    private val task: PlatformExecutor.PlatformTask = submit(period = 1L) {
        if (!isRunning) {
            cancel()
            return@submit
        }

        val end = minOf(index + itemsPerTick, items.size)

        for (i in index until end) {
            if (!isRunning) break
            val item = items[i]
            try {
                processAction?.invoke(item)
            } catch (ex: Throwable) {
                if (errorAction != null) {
                    errorAction.invoke(item, ex)
                } else {
                    ex.printStackTrace()
                }
            }
            processed++
        }

        index = end
        progressAction?.invoke(processed, total)

        if (index >= items.size) {
            isRunning = false
            cancel()
            completeAction?.invoke(processed)
        }
    }

    /**
     * 取消任务
     */
    fun cancel() {
        if (!isRunning) return
        isRunning = false
        task.cancel()
    }

    // ===== Builder =====

    class Builder<T>(val items: List<T>) {

        /** 每 tick 处理的数量（默认 50） */
        var perTick: Int = 50

        private var processAction: ((T) -> Unit)? = null
        private var completeAction: ((Int) -> Unit)? = null
        private var errorAction: ((T, Throwable) -> Unit)? = null
        private var progressAction: ((Int, Int) -> Unit)? = null

        /**
         * 每个元素的处理逻辑（主线程执行）
         */
        fun process(action: (T) -> Unit) {
            processAction = action
        }

        /**
         * 全部处理完成时的回调
         * @param action 参数为已处理总数
         */
        fun complete(action: (count: Int) -> Unit) {
            completeAction = action
        }

        /**
         * 单个元素处理出错时的回调（不设则打印堆栈）
         */
        fun error(action: (item: T, ex: Throwable) -> Unit) {
            errorAction = action
        }

        /**
         * 每 tick 结束时的进度回调
         */
        fun progress(action: (processed: Int, total: Int) -> Unit) {
            progressAction = action
        }

        internal fun build(): TickTask<T> {
            require(perTick > 0) { "perTick 必须大于 0" }
            return TickTask(items, perTick, processAction, completeAction, errorAction, progressAction)
        }
    }

    companion object {

        /**
         * 创建并启动分帧任务
         *
         * @param items 待处理的数据列表
         * @param block DSL 配置
         * @return 可查询进度/可取消的任务实例
         */
        fun <T> create(items: List<T>, block: Builder<T>.() -> Unit): TickTask<T> {
            return Builder(items).apply(block).build()
        }

        /**
         * 从 Iterable 创建（内部转为 List 快照）
         */
        fun <T> create(items: Iterable<T>, block: Builder<T>.() -> Unit): TickTask<T> {
            return create(items.toList(), block)
        }

        /**
         * 从 Array 创建
         */
        fun <T> create(items: Array<T>, block: Builder<T>.() -> Unit): TickTask<T> {
            return create(items.toList(), block)
        }
    }
}
