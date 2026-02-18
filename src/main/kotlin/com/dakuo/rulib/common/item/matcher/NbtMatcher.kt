package com.dakuo.rulib.common.item.matcher

import org.bukkit.inventory.ItemStack
import taboolib.module.nms.getItemTag

class NbtMatcher(private val matchType: MatchType) : ItemMatcherCondition {
    override fun matches(item: ItemStack, expression: String): Boolean {
        val (key, value) = expression.split(":")
        val itemTag = item.getItemTag() ?: return false
        return itemTag.keys.find {
            when (matchType) {
                MatchType.EXACT -> {
                    it == key && itemTag.get(it)?.asString() == value
                }

                MatchType.PARTIAL -> {
                    it.contains(key) && itemTag.get(it)?.asString()?.contains(value) == true
                }

                MatchType.REGEX -> {
                    it.matches(Regex(key)) && itemTag.get(it)?.asString()?.matches(Regex(value)) == true
                }
            }
        } != null
    }
}