package com.dakuo.rulib.common.item.matcher

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial

class MaterialMatcher(private val matchType: MatchType) :ItemMatcherCondition {
    override fun matches(item: ItemStack, expression: String): Boolean {
        val material = XMaterial.matchXMaterial(item)
        return when (matchType) {
            MatchType.EXACT -> matchesExact(material, expression)
            MatchType.PARTIAL -> matchesPartial(material, expression)
            MatchType.REGEX -> matchesRegex(material, expression)
        }
    }

    private fun matchesExact(material: XMaterial, expression: String): Boolean {
        return material.name == expression
    }

    private fun matchesPartial(material: XMaterial, expression: String): Boolean {
        return material.name.contains(expression, ignoreCase = true)
    }

    private fun matchesRegex(material: XMaterial, expression: String): Boolean {
        return material.name.matches(Regex(expression))
    }
}