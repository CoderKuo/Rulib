package com.dakuo.rulib.common

import org.bukkit.entity.Player
import taboolib.platform.compat.replacePlaceholder
import java.util.concurrent.ConcurrentHashMap

/**
 * 占位符变量系统
 *
 * 使用示例:
 * // 注册占位符
 * Placeholder.register("player_name") { player -> player.name }
 * Placeholder.register("player_health") { player -> player.health.toInt().toString() }
 *
 * // 解析文本
 * val text = "你好 %player_name%，你的血量是 %player_health%"
 * val result = Placeholder.parse(player, text)
 * // "你好 Steve，你的血量是 20"
 *
 * // 扩展函数
 * "欢迎 %player_name%".parsePlaceholder(player)
 *
 * // 解析列表（适用于 Lore）
 * listOf("等级: %level%", "金币: %coins%").parsePlaceholder(player)
 *
 * // 如果服务端安装了 PlaceholderAPI，会自动桥接
 * // 内部未注册的 %xxx% 占位符会交给 PAPI 处理
 */
object Placeholder {

    private val handlers = ConcurrentHashMap<String, (Player) -> String?>()

    /**
     * 注册占位符
     * @param key 占位符名称（不含 % 符号）
     * @param handler 处理函数，参数为玩家，返回替换值
     */
    fun register(key: String, handler: (Player) -> String?) {
        handlers[key] = handler
    }

    /**
     * 批量注册占位符
     */
    fun register(vararg pairs: Pair<String, (Player) -> String?>) {
        pairs.forEach { (key, handler) -> handlers[key] = handler }
    }

    /**
     * 注销占位符
     */
    fun unregister(key: String) {
        handlers.remove(key)
    }

    /**
     * 解析文本中的占位符
     * 先用内部注册的占位符替换，再尝试 PlaceholderAPI（如果存在）
     */
    fun parse(player: Player, text: String): String {
        if (!text.contains('%')) return text

        // 先替换内部注册的占位符，再交给 TabooLib 的 PAPI 桥接处理剩余的
        var result = parseInternal(player, text)

        if (result.contains('%')) {
            result = result.replacePlaceholder(player)
        }

        return result
    }

    /**
     * 解析文本列表
     */
    fun parse(player: Player, texts: List<String>): List<String> {
        return texts.map { parse(player, it) }
    }

    private fun parseInternal(player: Player, text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '%') {
                val end = text.indexOf('%', i + 1)
                if (end == -1) {
                    sb.append(text, i, text.length)
                    break
                }
                val key = text.substring(i + 1, end)
                val handler = handlers[key]
                if (handler != null) {
                    sb.append(handler(player) ?: "")
                } else {
                    sb.append(text, i, end + 1)
                }
                i = end + 1
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

}

/**
 * 对字符串进行占位符解析
 */
fun String.parsePlaceholder(player: Player): String = Placeholder.parse(player, this)

/**
 * 对字符串列表进行占位符解析
 */
fun List<String>.parsePlaceholder(player: Player): List<String> = Placeholder.parse(player, this)
