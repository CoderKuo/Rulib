package com.dakuo.rulib.common

import com.dakuo.rulib.common.cmd.reload.Reload
import com.dakuo.rulib.common.lang.StrUtils
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.module.chat.colored
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.platform.BukkitPlugin

object LogUtil {

    @Config("env.yml")
    lateinit var conifg: Configuration

    var debug: Boolean = false

    @Reload
    @Awake(LifeCycle.LOAD)
    fun load() {
        conifg.reload()
        debug = conifg.getBoolean("debug")
        if (debug) {
            debug("[debug] 调试模式已开启")
        }
    }

}

// debug("你好 {name}","name" to "xxx")
fun debug(message: Any?, vararg args: Pair<String, Any?>) {
    if (LogUtil.debug) {
        console().sendMessage("[${BukkitPlugin.getInstance().name}] [调试] >> "+StrUtils.format(message.toString(), *args).colored())
    }
}

fun info(message: Any?, vararg args: Pair<String, Any?>) {
    console().sendMessage(StrUtils.format(message.toString(), *args).colored())
}