package com.dakuo.rulib.common.item.matcher

import org.bukkit.inventory.ItemStack

class LoreMatcher(private val matchType: MatchType,private val ignoreColors:Boolean):ItemMatcherCondition {
    override fun matches(item: ItemStack, expression: String): Boolean {
        val lore = getItemLore(item)
        val expressions = expression.split(",") // 支持多个lore条件
        return expressions.all { exp -> matchesLore(lore, exp) }
    }


    private fun getItemLore(item: ItemStack): List<String> {
        val meta = item.itemMeta ?: return emptyList()
        return meta.lore ?: emptyList()
    }

    private fun matchesLore(lore: List<String>, expression: String): Boolean {
        return if (expression.contains("~")) {
            val keyword = expression.substringAfter("~")
            lore.any { it.contains(keyword, ignoreCase = true) }
        } else {
            lore.any { it == expression }
        }
    }
}