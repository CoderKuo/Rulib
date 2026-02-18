package com.dakuo.rulib.common.cmd

import com.dakuo.rulib.common.LogUtil
import org.bukkit.Bukkit
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.component.CommandComponent
import taboolib.platform.BukkitPlugin


fun CommandComponent.version() {
    exec<ProxyCommandSender> {
        val plugin = BukkitPlugin.getInstance()
        val header = "§7§l==================[ §6§l${plugin.name} §7§l]=================="
        val footer = "§7§l" + "=".repeat(header.length - 10)
        
        val messages = listOf(
            header,
            "",
            "§6服务器版本 §8» §f${Bukkit.getVersion()}",
            "§6插件版本 §8» §f${plugin.description.version}",
            "§6插件作者 §8» §f${plugin.description.authors.joinToString()}",
            "§6插件描述 §8» §f${plugin.description.description}",
            "§6调试模式 §8» §f${if (LogUtil.debug) "§a已开启" else "§c已关闭"}",
            "",
            footer
        )
        
        messages.forEach { sender.sendMessage(it) }
    }
}