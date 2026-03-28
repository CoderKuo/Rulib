package com.dakuo.rulib.common.lang

import taboolib.common.platform.function.submit

/**
 * 倒计时工具
 *
 * 基于 TabooLib 调度器的游戏倒计时 DSL，支持每秒回调、完成/取消回调。
 *
 * 使用示例:
 * // 基本用法 - 10秒倒计时
 * val cd = Countdown.start(10) {
 *     onTick { sec -> broadcast("开始倒计时: ${sec}秒") }
 *     onFinish { startGame() }
 * }
 *
 * // 取消倒计时
 * cd.cancel()
 *
 * // 带取消回调
 * Countdown.start(60) {
 *     onTick { sec ->
 *         if (sec <= 5) broadcast("§c${sec}秒！")
 *     }
 *     onFinish { broadcast("时间到！") }
 *     onCancel { broadcast("倒计时已取消") }
 * }
 *
 * // 异步模式（不占用主线程）
 * Countdown.start(30) {
 *     async()
 *     onTick { sec -> /* 异步线程 */ }
 *     onFinish { /* 异步线程 */ }
 * }
 *
 * // 查询状态
 * cd.isRunning     // 是否正在运行
 * cd.remaining     // 剩余秒数
 */
class Countdown private constructor(
    seconds: Int,
    private val tickAction: ((Int) -> Unit)?,
    private val finishAction: (() -> Unit)?,
    private val cancelAction: (() -> Unit)?,
    async: Boolean
) {
    @Volatile
    var remaining: Int = seconds
        private set

    @Volatile
    var isRunning: Boolean = true
        private set

    private val task = submit(async = async, period = 20L) {
        if (!isRunning) {
            cancel()
            return@submit
        }
        tickAction?.invoke(remaining)
        if (remaining <= 0) {
            isRunning = false
            cancel()
            finishAction?.invoke()
        } else {
            remaining--
        }
    }

    /**
     * 取消倒计时
     */
    fun cancel() {
        if (!isRunning) return
        isRunning = false
        task.cancel()
        cancelAction?.invoke()
    }

    class Builder(val seconds: Int) {
        private var tickAction: ((Int) -> Unit)? = null
        private var finishAction: (() -> Unit)? = null
        private var cancelAction: (() -> Unit)? = null
        private var async: Boolean = false

        /**
         * 每秒回调，参数为剩余秒数
         */
        fun onTick(action: (remaining: Int) -> Unit) {
            tickAction = action
        }

        /**
         * 倒计时完成回调
         */
        fun onFinish(action: () -> Unit) {
            finishAction = action
        }

        /**
         * 倒计时被取消回调
         */
        fun onCancel(action: () -> Unit) {
            cancelAction = action
        }

        /**
         * 异步执行（不占用主线程）
         */
        fun async(value: Boolean = true) {
            async = value
        }

        internal fun build(): Countdown {
            return Countdown(seconds, tickAction, finishAction, cancelAction, async)
        }
    }

    companion object {

        /**
         * 启动倒计时
         * @param seconds 倒计时秒数
         * @param block DSL 配置
         * @return 可取消的 Countdown 实例
         */
        fun start(seconds: Int, block: Builder.() -> Unit): Countdown {
            require(seconds > 0) { "倒计时秒数必须大于 0" }
            return Builder(seconds).apply(block).build()
        }
    }
}
