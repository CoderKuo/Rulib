package com.dakuo.rulib.common

import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.mergeTo

/**
 * 配置文件自动更新工具
 *
 * 将 JAR 中的默认配置与用户配置合并：补充新增的键和注释，不覆盖用户已有的值。
 *
 * 使用示例:
 * // 在插件启用时调用
 * ConfigUpdater.update(config, "config.yml")
 *
 * // 指定加载类（跨模块场景）
 * ConfigUpdater.update(config, "config.yml", MyPlugin::class.java)
 *
 * // 工作原理:
 * // 1. 从 JAR 中读取默认 config.yml（包含所有键和注释）
 * // 2. 用 mergeTo(overwrite=false) 将新键补充到用户配置（已有值不动）
 * // 3. 将默认配置的注释同步到用户配置（用户没有注释的键才补充）
 * // 4. 保存文件
 *
 * // 典型用法 - 配合版本号做增量迁移:
 * val version = config.getInt("config-version", 1)
 * if (version < CURRENT_VERSION) {
 *     ConfigUpdater.update(config, "config.yml")
 *     config["config-version"] = CURRENT_VERSION
 *     config.saveToFile()
 * }
 *
 * // 最简用法 - 每次启动都调用（幂等，已有值不会被覆盖）:
 * ConfigUpdater.update(config, "config.yml")
 */
object ConfigUpdater {

    /**
     * 自动更新配置文件
     *
     * @param config 用户当前的配置对象
     * @param resource JAR 中的资源文件路径（如 "config.yml"）
     * @param clazz 用于定位 JAR 资源的类（默认为 ConfigUpdater 自身）
     * @return true 表示有新增内容并已保存，false 表示无变化
     */
    @JvmOverloads
    fun update(config: Configuration, resource: String, clazz: Class<*> = ConfigUpdater::class.java): Boolean {
        val stream = clazz.classLoader.getResourceAsStream(resource) ?: return false
        val defaults = Configuration.loadFromInputStream(stream)
        val keysBefore = config.getKeys(true).size

        // 合并新键（不覆盖已有值）
        defaults.mergeTo(config, overwrite = false)

        // 同步注释
        syncComments(defaults, config)

        val keysAfter = config.getKeys(true).size
        if (keysAfter > keysBefore) {
            config.saveToFile()
            return true
        }

        // 即使键数不变，注释可能有更新，也保存一次
        config.saveToFile()
        return false
    }

    /**
     * 将源配置的注释同步到目标配置（仅补充，不覆盖已有注释）
     */
    private fun syncComments(source: Configuration, target: Configuration) {
        for (key in source.getKeys(true)) {
            val comments = source.getComments(key)
            if (comments.isNotEmpty() && target.getComments(key).isEmpty()) {
                target.setComments(key, comments)
            }
        }
    }
}
