package com.dakuo.rulib.common.item.matcher

import org.bukkit.inventory.ItemStack

/**
 *  物品匹配表达式工具
 *  name:xxx,lore:xxx
 *
 */
class ItemTextMatcher {

    // 解析和匹配物品
    fun matches(item: ItemStack, expression: String): Boolean {
        val conditions = parseExpression(expression)

        // 检查各个条件是否都匹配
        return conditions.all { (condition, exp) -> condition.matches(item, exp) }
    }

    // 解析表达式，自动推断matchType
    private fun parseExpression(expression: String): List<Pair<ItemMatcherCondition, String>> {
        val conditions = mutableListOf<Pair<ItemMatcherCondition, String>>()

        // 解析表达式中的各个部分
        val parts = expression.split(",")
        for (part in parts) {
            when {
                part.startsWith("name:") -> {
                    val nameExp = part.substringAfter("name:")
                    conditions.add(NameMatcher(inferMatchType(nameExp), true) to nameExp)
                }
                part.startsWith("lore:") -> {
                    val loreExp = part.substringAfter("lore:")
                    conditions.add(LoreMatcher(inferMatchType(loreExp), true) to loreExp)
                }
                part.startsWith("nbt:") -> {
                    val nbtExp = part.substringAfter("nbt:")
                    conditions.add(NbtMatcher(inferMatchType(nbtExp)) to nbtExp)
                }
                part.startsWith("material:") || part.startsWith("type:") -> {
                    val materialExp = if (part.startsWith("type:")) part.substringAfter("type:") else part.substringAfter("material:")
                    conditions.add(MaterialMatcher(inferMatchType(materialExp)) to materialExp)
                }
            }
        }

        return conditions
    }

    // 根据表达式推断MatchType
    private fun inferMatchType(expression: String): MatchType {
        return when {
            expression.startsWith("/") && expression.endsWith("/") -> MatchType.REGEX // 正则匹配
            expression.contains("*") || expression.contains("?") -> MatchType.PARTIAL // 包含通配符，使用模糊匹配
            else -> MatchType.EXACT // 精确匹配
        }
    }


}