package com.dakuo.rulib.common.lang

import java.text.DecimalFormat

/**
 * 数字格式化工具
 *
 * 使用示例:
 * // 短格式（中文）
 * 1234567.0.formatShort()          // "123.5万"
 * 150000000.0.formatShort()        // "1.5亿"
 *
 * // 短格式（英文）
 * 1234567.0.formatShort(false)     // "1.2M"
 *
 * // 千位分隔
 * 1234567.formatGrouped()          // "1,234,567"
 * 1234567.89.formatGrouped()       // "1,234,567.89"
 *
 * // 百分比
 * 0.756.formatPercent()            // "75.6%"
 * 0.5.formatPercent(0)             // "50%"
 *
 * // 序号
 * 3.toOrdinal()                    // "第3"
 *
 * // 罗马数字（Minecraft 附魔等级常用）
 * 5.toRoman()                      // "V"
 * 4.toRoman()                      // "IV"
 */
object NumberFormat {

    private val groupedFormat = DecimalFormat("#,##0.##")

    private val romanValues = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    private val romanSymbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

    /**
     * 短格式化
     * @param chinese true 使用中文单位（万/亿），false 使用英文单位（K/M/B）
     */
    fun formatShort(value: Double, chinese: Boolean = true): String {
        if (chinese) {
            return when {
                value >= 1_0000_0000 -> trimTrailingZero(value / 1_0000_0000) + "亿"
                value >= 1_0000 -> trimTrailingZero(value / 1_0000) + "万"
                value <= -1_0000_0000 -> trimTrailingZero(value / 1_0000_0000) + "亿"
                value <= -1_0000 -> trimTrailingZero(value / 1_0000) + "万"
                else -> trimTrailingZero(value)
            }
        } else {
            return when {
                value >= 1_000_000_000 -> trimTrailingZero(value / 1_000_000_000) + "B"
                value >= 1_000_000 -> trimTrailingZero(value / 1_000_000) + "M"
                value >= 1_000 -> trimTrailingZero(value / 1_000) + "K"
                value <= -1_000_000_000 -> trimTrailingZero(value / 1_000_000_000) + "B"
                value <= -1_000_000 -> trimTrailingZero(value / 1_000_000) + "M"
                value <= -1_000 -> trimTrailingZero(value / 1_000) + "K"
                else -> trimTrailingZero(value)
            }
        }
    }

    /**
     * 千位分隔格式
     */
    fun formatGrouped(value: Double): String {
        return groupedFormat.format(value)
    }

    /**
     * 百分比格式
     * @param digits 小数位数
     */
    fun formatPercent(value: Double, digits: Int = 1): String {
        val percent = value * 100
        return if (digits <= 0) {
            "${percent.toLong()}%"
        } else {
            val formatted = String.format("%.${digits}f", percent)
            trimTrailingZero(formatted) + "%"
        }
    }

    /**
     * 中文序号
     */
    fun formatOrdinal(value: Int): String = "第$value"

    /**
     * 罗马数字（1-3999）
     */
    fun toRoman(value: Int): String {
        require(value in 1..3999) { "罗马数字仅支持 1-3999，输入: $value" }
        val sb = StringBuilder()
        var remaining = value
        for (i in romanValues.indices) {
            while (remaining >= romanValues[i]) {
                sb.append(romanSymbols[i])
                remaining -= romanValues[i]
            }
        }
        return sb.toString()
    }

    private fun trimTrailingZero(value: Double): String {
        return String.format("%.1f", value).removeSuffix(".0").let {
            if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it
        }
    }

    private fun trimTrailingZero(str: String): String {
        if (!str.contains('.')) return str
        return str.trimEnd('0').trimEnd('.')
    }
}

// ===== 扩展函数 =====

/**
 * 短格式化数字
 * @param chinese true 使用中文单位（万/亿），false 使用英文（K/M/B）
 */
fun Number.formatShort(chinese: Boolean = true): String = NumberFormat.formatShort(this.toDouble(), chinese)

/**
 * 千位分隔格式
 */
fun Number.formatGrouped(): String = NumberFormat.formatGrouped(this.toDouble())

/**
 * 百分比格式（0.75 → "75%"）
 */
fun Number.formatPercent(digits: Int = 1): String = NumberFormat.formatPercent(this.toDouble(), digits)

/**
 * 中文序号（3 → "第3"）
 */
fun Int.toOrdinal(): String = NumberFormat.formatOrdinal(this)

/**
 * 罗马数字（5 → "V"）
 */
fun Int.toRoman(): String = NumberFormat.toRoman(this)
