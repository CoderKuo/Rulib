package com.dakuo.rulib.common.menu

import org.bukkit.entity.HumanEntity
import org.bukkit.event.inventory.InventoryClickEvent
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.ClickType


fun ClickEvent.onLClick(consumer: InventoryClickEvent.(player: HumanEntity) -> Unit): ClickEvent {
    if (clickType == ClickType.CLICK) {
        val event = clickEvent()
        if (event.isLeftClick && !event.isShiftClick) consumer(event, event.whoClicked)
    }
    return this
}

fun ClickEvent.onSLClick(consumer: InventoryClickEvent.(player: HumanEntity) -> Unit): ClickEvent {
    if (clickType == ClickType.CLICK) {
        val event = clickEvent()
        if (event.isLeftClick && event.isShiftClick) consumer(event, event.whoClicked)
    }
    return this
}

fun ClickEvent.onRClick(consumer: InventoryClickEvent.(player: HumanEntity) -> Unit): ClickEvent {
    if (clickType == ClickType.CLICK) {
        val event = clickEvent()
        if (event.isRightClick && !event.isShiftClick) consumer(event, event.whoClicked)
    }
    return this
}


fun ClickEvent.onSRClick(consumer: InventoryClickEvent.(player: HumanEntity) -> Unit): ClickEvent {
    if (clickType == ClickType.CLICK) {
        val event = clickEvent()
        if (event.isRightClick && event.isShiftClick) consumer(event, event.whoClicked)
    }
    return this
}

