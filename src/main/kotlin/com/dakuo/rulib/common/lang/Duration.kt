package com.dakuo.rulib.common.lang

/**
 * 时间解析工具
 *
 * 支持格式: "1d2h30m10s" / "2h" / "30m10s" / "1d" / "90s"
 * 单位: d(天) h(小时) m(分钟) s(秒)
 *
 * 使用示例:
 * val dur = "1d2h30m10s".parseDuration()
 * dur.toMillis()          // 95410000
 * dur.toSeconds()         // 95410
 * dur.toFormatted()       // "1天2小时30分钟10秒"
 *
 * // 与 Cooldown 配合
 * val cd = Cooldown("5m30s".parseDuration().toMillis())
 */
class Duration private constructor(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) {

    /** 总毫秒数 */
    fun toMillis(): Long =
        ((days * 86400L) + (hours * 3600L) + (minutes * 60L) + seconds) * 1000L

    /** 总秒数 */
    fun toSeconds(): Long =
        (days * 86400L) + (hours * 3600L) + (minutes * 60L) + seconds

    /** 总分钟数（向下取整） */
    fun toMinutes(): Long = toSeconds() / 60

    /** 总小时数（向下取整） */
    fun toHours(): Long = toSeconds() / 3600

    /**
     * 格式化为中文可读字符串
     * 例如: "1天2小时30分钟10秒"
     * 值为 0 的单位会跳过
     */
    fun toFormatted(): String {
        val sb = StringBuilder()
        if (days > 0) sb.append("${days}天")
        if (hours > 0) sb.append("${hours}小时")
        if (minutes > 0) sb.append("${minutes}分钟")
        if (seconds > 0) sb.append("${seconds}秒")
        return sb.toString().ifEmpty { "0秒" }
    }

    override fun toString(): String = toFormatted()

    companion object {

        private val PATTERN = Regex("(\\d+)([dhms])", RegexOption.IGNORE_CASE)

        /**
         * 解析时间字符串
         * @param input 如 "1d2h30m10s"
         * @throws IllegalArgumentException 如果格式无效
         */
        fun parse(input: String): Duration {
            var d = 0; var h = 0; var m = 0; var s = 0
            var matched = false
            PATTERN.findAll(input.trim().lowercase()).forEach { result ->
                matched = true
                val value = result.groupValues[1].toInt()
                when (result.groupValues[2]) {
                    "d" -> d += value
                    "h" -> h += value
                    "m" -> m += value
                    "s" -> s += value
                }
            }
            require(matched) { "无效的时间格式: $input" }
            return Duration(d, h, m, s)
        }

        /**
         * 从毫秒数构建 Duration
         */
        fun ofMillis(millis: Long): Duration {
            var remaining = millis / 1000
            val d = (remaining / 86400).toInt(); remaining %= 86400
            val h = (remaining / 3600).toInt(); remaining %= 3600
            val m = (remaining / 60).toInt(); remaining %= 60
            val s = remaining.toInt()
            return Duration(d, h, m, s)
        }

        /**
         * 从秒数构建 Duration
         */
        fun ofSeconds(seconds: Long): Duration = ofMillis(seconds * 1000)
    }
}

/**
 * 将字符串解析为 Duration
 * "1d2h30m10s".parseDuration()
 */
fun String.parseDuration(): Duration = Duration.parse(this)

/**
 * 将毫秒数格式化为友好中文时间
 * 95410000L.formatDuration() // "1天2小时30分钟10秒"
 */
fun Long.formatDuration(): String = Duration.ofMillis(this).toFormatted()
