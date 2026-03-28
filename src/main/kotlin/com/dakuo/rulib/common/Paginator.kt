package com.dakuo.rulib.common

import org.bukkit.entity.Player
import taboolib.common.platform.function.adaptPlayer
import taboolib.module.chat.Components
import kotlin.math.ceil
import kotlin.math.max

/**
 * 聊天分页器
 *
 * 通用列表分页 + 聊天可点击翻页导航。
 *
 * 使用示例:
 * // 创建分页器
 * val pager = Paginator(shopItems, pageSize = 10)
 *
 * // 纯数据分页
 * val page1 = pager.getPage(1)     // 获取第1页数据
 * pager.totalPages                  // 总页数
 * pager.hasNext(1)                  // 是否有下一页
 *
 * // 聊天发送（带可点击 [上一页] [下一页]）
 * pager.send(player, page = 1, command = "/shop list {page}") { item, index ->
 *     "§e${index + 1}. §f${item.name} §7- §a${item.price}金币"
 * }
 *
 * // 带标题头
 * pager.send(player, page = 1, header = "§e§l=== 商店列表 ===", command = "/shop list {page}") { item, index ->
 *     "§e${index + 1}. §f${item.name}"
 * }
 *
 * // 在命令中使用
 * command.literal("list") {
 *     execute<Player> { sender, _, arg ->
 *         val page = arg.toIntOrNull() ?: 1
 *         pager.send(sender, page, command = "/mycommand list {page}") { item, i ->
 *             "§7${i + 1}. §f${item}"
 *         }
 *     }
 * }
 */
class Paginator<T>(
    val items: List<T>,
    val pageSize: Int = 10
) {
    /**
     * 总页数
     */
    val totalPages: Int = max(1, ceil(items.size.toDouble() / pageSize).toInt())

    /**
     * 获取指定页的数据（1-based）
     */
    fun getPage(page: Int): List<T> {
        val p = page.coerceIn(1, totalPages)
        val from = (p - 1) * pageSize
        val to = (from + pageSize).coerceAtMost(items.size)
        return if (from >= items.size) emptyList() else items.subList(from, to)
    }

    /**
     * 是否有下一页
     */
    fun hasNext(page: Int): Boolean = page < totalPages

    /**
     * 是否有上一页
     */
    fun hasPrevious(page: Int): Boolean = page > 1

    /**
     * 发送分页消息到玩家聊天
     *
     * @param player 目标玩家
     * @param page 当前页码（1-based）
     * @param header 头部标题文本（可选）
     * @param command 翻页命令模板，用 {page} 作为页码占位符，如 "/shop list {page}"
     * @param formatter 每行内容的格式化函数，参数为 (item, globalIndex)
     */
    fun send(
        player: Player,
        page: Int,
        header: String? = null,
        command: String,
        formatter: (item: T, index: Int) -> String
    ) {
        val p = page.coerceIn(1, totalPages)
        val sender = adaptPlayer(player)

        // 头部
        if (header != null) {
            player.sendMessage(header)
        }

        // 内容
        val pageItems = getPage(p)
        val baseIndex = (p - 1) * pageSize
        pageItems.forEachIndexed { i, item ->
            player.sendMessage(formatter(item, baseIndex + i))
        }

        // 导航栏
        val nav = Components.empty()

        // [上一页]
        if (hasPrevious(p)) {
            nav.append("§e[上一页]")
                .clickRunCommand(command.replace("{page}", (p - 1).toString()))
                .hoverText("§7点击查看第 ${p - 1} 页")
        } else {
            nav.append("§7[上一页]")
        }

        nav.append(" §f第 ${p}/${totalPages} 页 ")

        // [下一页]
        if (hasNext(p)) {
            nav.append("§e[下一页]")
                .clickRunCommand(command.replace("{page}", (p + 1).toString()))
                .hoverText("§7点击查看第 ${p + 1} 页")
        } else {
            nav.append("§7[下一页]")
        }

        nav.sendTo(sender)
    }
}
