package com.dakuo.rulib.common.item.matcher

import org.bukkit.inventory.ItemStack

interface ItemMatcherCondition {

    fun matches(item: ItemStack, expression: String): Boolean

}