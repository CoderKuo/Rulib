package com.dakuo.rulib.common.lang

import com.dakuo.rulib.common.getter.BasicTypeGetter
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

class Dict private constructor(
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
    loadFactor: Float = DEFAULT_LOAD_FACTOR,
    private var caseInsensitive: Boolean = false
) : LinkedHashMap<String, Any>(initialCapacity, loadFactor), BasicTypeGetter<String> {

    companion object {
        private const val DEFAULT_LOAD_FACTOR = 0.75f
        private const val DEFAULT_INITIAL_CAPACITY = 1 shl 4 // aka 16

        /**
         * 创建Dict
         */
        fun create(): Dict = Dict()

        /**
         * 从Map创建Dict
         */
        fun of(map: Map<String, Any>?): Dict = Dict(map)

        /**
         * 从键值对创建Dict
         */
        fun of(vararg pairs: Pair<String, Any>): Dict = Dict().apply { 
            putAll(pairs.toMap())
        }
    }

    constructor() : this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, false)

    constructor(caseInsensitive: Boolean) : this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, caseInsensitive)

    constructor(initialCapacity: Int) : this(initialCapacity, DEFAULT_LOAD_FACTOR, false)

    constructor(initialCapacity: Int, caseInsensitive: Boolean) : this(initialCapacity, DEFAULT_LOAD_FACTOR, caseInsensitive)

    constructor(initialCapacity: Int, loadFactor: Float) : this(initialCapacity, loadFactor, false)

    constructor(m: Map<String, Any>?) : this() {
        if (m != null) {
            putAll(m)
        }
    }

    /**
     * 设置列,支持链式调用
     */
    operator fun set(attr: String, value: Any?) {
        if (value != null) put(attr, value)
    }

    /**
     * 设置列,支持链式调用
     */
    infix fun to(pair: Pair<String, Any?>) = apply {
        pair.second?.let { put(pair.first, it) }
    }

    /**
     * 批量设置列,支持链式调用
     */
    fun setAll(map: Map<String, Any?>) = apply {
        map.forEach { (k, v) -> if (v != null) put(k, v) }
    }

    /**
     * 设置列，当键或值为null时忽略,支持链式调用
     */
    fun setIgnoreNull(attr: String?, value: Any?) = apply {
        if (attr != null && value != null) {
            put(attr, value)
        }
    }

    /**
     * 为多个key设置相同的value,支持链式调用
     */
    fun putKeys(value: Any, vararg keys: String) = apply {
        keys.forEach { key -> put(key, value) }
    }

    override fun getObj(key: String): Any? = get(key)

    override fun getStr(key: String): String? = get(key)?.toString()

    override fun getInt(key: String): Int? = get(key)?.toString()?.toIntOrNull()

    override fun getShort(key: String): Short? = get(key)?.toString()?.toShortOrNull()

    override fun getBool(key: String): Boolean? = when (get(key)?.toString()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    override fun getLong(key: String): Long? = get(key)?.toString()?.toLongOrNull()

    override fun getChar(key: String): Char? = get(key)?.toString()?.firstOrNull()

    override fun getFloat(key: String): Float? = get(key)?.toString()?.toFloatOrNull()

    override fun getDouble(key: String): Double? = get(key)?.toString()?.toDoubleOrNull()

    override fun getByte(key: String): Byte? = get(key)?.toString()?.toByteOrNull()

    override fun getBigDecimal(key: String): BigDecimal? = get(key)?.toString()?.let { str ->
        try {
            BigDecimal(str)
        } catch (e: Exception) {
            null
        }
    }

    override fun getBigInteger(key: String): BigInteger? = get(key)?.toString()?.let { str ->
        try {
            BigInteger(str)
        } catch (e: Exception) {
            null
        }
    }

    override fun <E : Enum<E>> getEnum(clazz: Class<E>, key: String): E? = get(key)?.toString()?.let { value ->
        try {
            java.lang.Enum.valueOf(clazz, value)
        } catch (e: Exception) {
            null
        }
    }

    override fun getDate(key: String): Date? = when (val value = get(key)) {
        is Date -> value
        is Long -> Date(value)
        is String -> try {
            Date(value.toLong())
        } catch (e: Exception) {
            null
        }
        else -> null
    }

    override fun containsKey(key: String): Boolean = super.containsKey(customKey(key))

    override fun get(key: String): Any? = super.get(customKey(key))

    override fun put(key: String, value: Any): Any? = super.put(customKey(key), value)

    private fun customKey(key: String): String = 
        if (caseInsensitive && key.isNotEmpty()) key.lowercase() else key

    // 运算符重载支持
    operator fun plus(other: Dict) = Dict(this).apply { putAll(other) }
    
    operator fun plus(pair: Pair<String, Any>) = Dict(this).apply { put(pair.first, pair.second) }
}