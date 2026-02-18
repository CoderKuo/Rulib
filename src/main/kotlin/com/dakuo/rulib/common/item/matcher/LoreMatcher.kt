package com.dakuo.rulib.common.item.matcher

import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

class LoreMatcher(private val matchType: MatchType, private val ignoreColors: Boolean) : ItemMatcherCondition {
    override fun matches(item: ItemStack, expression: String): Boolean {
        val lore = getItemLore(item)
        return lore.any { line -> matchesLine(line, expression) }
    }

    private fun getItemLore(item: ItemStack): List<String> {
        val meta = item.itemMeta ?: return emptyList()
        return meta.lore ?: emptyList()
    }

    private fun matchesLine(line: String, expression: String): Boolean {
        val target = if (ignoreColors) ChatColor.stripColor(line) ?: line else line
        return when (matchType) {
            MatchType.EXACT -> target == expression
            MatchType.PARTIAL -> target.contains(expression, ignoreCase = true)
            MatchType.REGEX -> target.matches(Regex(expression))
        }
    }
}