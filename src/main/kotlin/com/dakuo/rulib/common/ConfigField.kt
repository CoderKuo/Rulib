package com.dakuo.rulib.common

import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getStringColored
import taboolib.module.configuration.util.getStringListColored
import kotlin.reflect.KProperty

class ConfigField<T>(
    private val bindConfig: (() -> Configuration),
    private val initializer: Configuration.() -> T
) {
    @Volatile
    private var _value: Any? = UNINITIALIZED_VALUE

    companion object {
        private val UNINITIALIZED_VALUE = Any()
    }

    private fun getValue(): T {
        if (_value === UNINITIALIZED_VALUE) {
            synchronized(this) {
                if (_value === UNINITIALIZED_VALUE) {
                    _value = initializer(bindConfig().also {
                        it.onReload {
                            reload(it)
                        }
                    })
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    fun isInitialized(): Boolean = _value !== UNINITIALIZED_VALUE

    private fun reload(config: Configuration) {
        synchronized(this) {
            _value = initializer(config)
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ConfigField<T> = this

    operator fun invoke(): T = getValue()
}

fun <T> configField(bindConfig: () -> Configuration, initializer: Configuration.() -> T): ConfigField<T> {

    return ConfigField(bindConfig, initializer)
}

fun configFieldString(key: String, colored: Boolean = false, config: () -> Configuration): ConfigField<String?> {
    return ConfigField(config) {
        if (colored) getStringColored(key) else getString(key)
    }
}

fun configFieldStringList(
    key: String,
    colored: Boolean = false,
    config: () -> Configuration
): ConfigField<List<String>> {
    return ConfigField(config) {
        if (colored) getStringListColored(key) else getStringList(key)
    }
}

fun configFieldBoolean(
    key: String,
    def: Boolean = false,
    config: () -> Configuration
): ConfigField<Boolean> {
    return ConfigField(config) {
        getBoolean(key, def)
    }
}