package com.dakuo.rulib.common

import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.ProxyListener
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.registerBukkitListener
import taboolib.module.configuration.Configuration
import java.io.Closeable
import java.io.File

fun getMainConfig() = Configuration.loadFromFile(File(getDataFolder(), "config.yml"))


inline fun <reified T> registerListener(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = true,
    noinline func: Closeable.(T) -> Unit
): ProxyListener {
    return registerBukkitListener(T::class.java, priority, ignoreCancelled, func)
}
