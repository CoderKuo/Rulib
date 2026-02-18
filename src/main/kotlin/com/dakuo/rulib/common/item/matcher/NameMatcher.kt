package com.dakuo.rulib.common.item.matcher

import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack

class NameMatcher(private val matchType: MatchType, private val ignoreColors: Boolean) : ItemMatcherCondition {
    override fun matches(item: ItemStack, expression: String): Boolean {
        val itemName = getItemName(item)
        return when (matchType) {
            MatchType.EXACT -> matchesExact(itemName, expression)
            MatchType.PARTIAL -> matchesPartial(itemName, expression)
            MatchType.REGEX -> matchesRegex(itemName, expression)
        }
    }

    private fun getItemName(item: ItemStack): String {
        val meta = item.itemMeta ?: return ""
        return meta.displayName ?: ""
    }

    private fun matchesExact(target: String, expression: String): Boolean {
        return removeColors(target) == expression
    }

    private fun matchesPartial(target: String, expression: String): Boolean {
        return removeColors(target).contains(expression, ignoreCase = true)
    }

    private fun matchesRegex(target: String, expression: String): Boolean {
        return removeColors(target).matches(Regex(expression))
    }

    private fun removeColors(text: String): String {
        return ChatColor.stripColor(text) ?: text
    }
}