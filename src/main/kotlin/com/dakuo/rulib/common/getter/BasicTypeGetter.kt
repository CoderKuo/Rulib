package com.dakuo.rulib.common.getter

import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

interface BasicTypeGetter<K> {
    /**
     * 获取Object属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getObj(key: K): Any?

    /**
     * 获取字符串型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getStr(key: K): String?

    /**
     * 获取int型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getInt(key: K): Int?

    /**
     * 获取short型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getShort(key: K): Short?

    /**
     * 获取boolean型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getBool(key: K): Boolean?

    /**
     * 获取long型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getLong(key: K): Long?

    /**
     * 获取char型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getChar(key: K): Char?

    /**
     * 获取float型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getFloat(key: K): Float?

    /**
     * 获取double型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getDouble(key: K): Double?

    /**
     * 获取byte型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getByte(key: K): Byte?

    /**
     * 获取BigDecimal型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getBigDecimal(key: K): BigDecimal?

    /**
     * 获取BigInteger型属性值
     *
     * @param key 属性名
     * @return 属性值
     */
    fun getBigInteger(key: K): BigInteger?

    /**
     * 获得Enum类型的值
     *
     * @param clazz Enum的Class
     * @param key KEY
     * @return Enum类型的值，无则返回Null
     */
    fun <E : Enum<E>> getEnum(clazz: Class<E>, key: K): E?

    /**
     * 获取Date类型值
     *
     * @param key 属性名
     * @return Date类型属性值
     */
    fun getDate(key: K): Date?
}