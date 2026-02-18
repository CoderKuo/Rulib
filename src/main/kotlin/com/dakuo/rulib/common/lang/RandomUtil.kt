package com.dakuo.rulib.common.lang

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ThreadLocalRandom

object RandomUtil {

    /**
     * 用于随机选的数字
     */
    const val BASE_NUMBER = "0123456789"

    /**
     * 用于随机选的字符
     */
    const val BASE_CHAR = "abcdefghijklmnopqrstuvwxyz"

    /**
     * 用于随机选的字符和数字（小写）
     */
    const val BASE_CHAR_NUMBER_LOWER = BASE_CHAR + BASE_NUMBER

    /**
     * 用于随机选的字符和数字（包括大写和小写字母）
     */
    val BASE_CHAR_NUMBER = BASE_CHAR.uppercase() + BASE_CHAR_NUMBER_LOWER

    /**
     * 获取随机数生成器对象
     * ThreadLocalRandom是JDK 7之后提供并发产生随机数，能够解决多个线程发生的竞争争夺。
     *
     * 注意：此方法返回的ThreadLocalRandom不可以在多线程环境下共享对象，否则有重复随机数问题。
     * 见：https://www.jianshu.com/p/89dfe990295c
     *
     * @return ThreadLocalRandom
     */
    fun getRandom(): ThreadLocalRandom = ThreadLocalRandom.current()

    /**
     * 创建SecureRandom，类提供加密的强随机数生成器 (RNG)
     *
     * @param seed 自定义随机种子
     * @return SecureRandom
     */
    fun createSecureRandom(seed: ByteArray?): SecureRandom =
        if (seed == null) SecureRandom() else SecureRandom(seed)

    /**
     * 获取SHA1PRNG的SecureRandom，类提供加密的强随机数生成器 (RNG)
     * 注意：此方法获取的是伪随机序列发生器PRNG（pseudo-random number generator）
     *
     * 相关说明见：https://stackoverflow.com/questions/137212/how-to-solve-slow-java-securerandom
     *
     * @return SecureRandom
     */
    fun getSecureRandom(): SecureRandom = getSecureRandom(null)

    /**
     * 获取SHA1PRNG的SecureRandom，类提供加密的强随机数生成器 (RNG)
     * 注意：此方法获取的是伪随机序列发生器PRNG（pseudo-random number generator）
     *
     * 相关说明见：https://stackoverflow.com/questions/137212/how-to-solve-slow-java-securerandom
     *
     * @param seed 随机数种子
     * @return SecureRandom
     */
    fun getSecureRandom(seed: ByteArray?): SecureRandom = createSecureRandom(seed)

    /**
     * 获取SHA1PRNG的SecureRandom，类提供加密的强随机数生成器 (RNG)
     * 注意：此方法获取的是伪随机序列发生器PRNG（pseudo-random number generator）,在Linux下噪声生成时可能造成较长时间停顿。
     * see: http://ifeve.com/jvm-random-and-entropy-source/
     *
     * 相关说明见：https://stackoverflow.com/questions/137212/how-to-solve-slow-java-securerandom
     *
     * @param seed 随机数种子
     * @return SecureRandom
     */
    fun getSHA1PRNGRandom(seed: ByteArray?): SecureRandom {
        val random = try {
            SecureRandom.getInstance("SHA1PRNG")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        seed?.let { random.setSeed(it) }
        return random
    }

    /**
     * 获取algorithms/providers中提供的强安全随机生成器
     * 注意：此方法可能造成阻塞或性能问题
     *
     * @return SecureRandom
     */
    fun getSecureRandomStrong(): SecureRandom = try {
        SecureRandom.getInstanceStrong()
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    }

    /**
     * 获取随机数产生器
     *
     * @param isSecure 是否为强随机数生成器 (RNG)
     * @return Random
     */
    fun getRandom(isSecure: Boolean): Random = if (isSecure) getSecureRandom() else getRandom()

    /**
     * 获得随机Boolean值
     *
     * @return true or false
     */
    fun randomBoolean(): Boolean = randomInt(2) == 0

    /**
     * 随机bytes
     *
     * @param length 长度
     * @return bytes
     */
    fun randomBytes(length: Int): ByteArray = ByteArray(length).apply {
        getRandom().nextBytes(this)
    }

    /**
     * 获得随机数int值
     *
     * @return 随机数
     */
    fun randomInt(): Int = getRandom().nextInt()

    /**
     * 获得指定范围内的随机数 [0,limit)
     *
     * @param limitExclude 限制随机数的范围，不包括这个数
     * @return 随机数
     */
    fun randomInt(limitExclude: Int): Int = getRandom().nextInt(limitExclude)

    /**
     * 获得指定范围内的随机数
     *
     * @param minInclude 最小数（包含）
     * @param maxExclude 最大数（不包含）
     * @return 随机数
     */
    fun randomInt(minInclude: Int, maxExclude: Int): Int =
        randomInt(minInclude, maxExclude, true, false)

    /**
     * 获得指定范围内的随机数
     *
     * @param min 最小数
     * @param max 最大数
     * @param includeMin 是否包含最小值
     * @param includeMax 是否包含最大值
     * @return 随机数
     */
    fun randomInt(min: Int, max: Int, includeMin: Boolean, includeMax: Boolean): Int {
        var minVal = min
        var maxVal = max
        if (!includeMin) {
            minVal++
        }
        if (includeMax) {
            maxVal++
        }
        return getRandom().nextInt(minVal, maxVal)
    }

    /**
     * 创建指定长度的随机索引
     *
     * @param length 长度
     * @return 随机索引
     */
    fun randomInts(length: Int): IntArray {
        val range = IntArray(length) { it }
        for (i in 0 until length) {
            val random = randomInt(i, length)
            val temp = range[i]
            range[i] = range[random]
            range[random] = temp
        }
        return range
    }

    /**
     * 获得随机数
     *
     * @return 随机数
     */
    fun randomLong(): Long = getRandom().nextLong()

    /**
     * 获得指定范围内的随机数 [0,limit)
     *
     * @param limitExclude 限制随机数的范围，不包括这个数
     * @return 随机数
     */
    fun randomLong(limitExclude: Long): Long = getRandom().nextLong(limitExclude)

    /**
     * 获得指定范围内的随机数[min, max)
     *
     * @param minInclude 最小数（包含）
     * @param maxExclude 最大数（不包含）
     * @return 随机数
     */
    fun randomLong(minInclude: Long, maxExclude: Long): Long =
        randomLong(minInclude, maxExclude, true, false)

    /**
     * 获得指定范围内的随机数
     *
     * @param min 最小数
     * @param max 最大数
     * @param includeMin 是否包含最小值
     * @param includeMax 是否包含最大值
     * @return 随机数
     */
    fun randomLong(min: Long, max: Long, includeMin: Boolean, includeMax: Boolean): Long {
        var minVal = min
        var maxVal = max
        if (!includeMin) {
            minVal++
        }
        if (includeMax) {
            maxVal++
        }
        return getRandom().nextLong(minVal, maxVal)
    }

    /**
     * 获得随机数[0, 1)
     *
     * @return 随机数
     */
    fun randomFloat(): Float = getRandom().nextFloat()

    /**
     * 获得指定范围内的随机数 [0,limit)
     *
     * @param limitExclude 限制随机数的范围，不包括这个数
     * @return 随机数
     */
    fun randomFloat(limitExclude: Float): Float = randomFloat(0f, limitExclude)

    /**
     * 获得指定范围内的随机数[min, max)
     *
     * @param minInclude 最小数（包含）
     * @param maxExclude 最大数（不包含）
     * @return 随机数
     */
    fun randomFloat(minInclude: Float, maxExclude: Float): Float {
        if (minInclude == maxExclude) {
            return minInclude
        }
        return minInclude + ((maxExclude - minInclude) * getRandom().nextFloat())
    }

    /**
     * 获得指定范围内的随机数
     *
     * @param minInclude 最小数（包含）
     * @param maxExclude 最大数（不包含）
     * @return 随机数
     */
    fun randomDouble(minInclude: Double, maxExclude: Double): Double =
        getRandom().nextDouble(minInclude, maxExclude)

    /**
     * 获得随机数[0, 1)
     *
     * @return 随机数
     */
    fun randomDouble(): Double = getRandom().nextDouble()

    /**
     * 获得指定范围内的随机数 [0,limit)
     *
     * @param limit 限制随机数的范围，不包括这个数
     * @return 随机数
     */
    fun randomDouble(limit: Double): Double = getRandom().nextDouble(limit)

    /**
     * 随机获得列表中的元素
     *
     * @param list 列表
     * @return 随机元素
     */
    fun <T> randomEle(list: List<T>): T = randomEle(list, list.size)

    /**
     * 随机获得列表中的元素
     *
     * @param list 列表
     * @param limit 限制列表的前N项
     * @return 随机元素
     */
    fun <T> randomEle(list: List<T>, limit: Int): T {
        val actualLimit = if (list.size < limit) list.size else limit
        return list[randomInt(actualLimit)]
    }

    /**
     * 随机获得数组中的元素
     *
     * @param array 列表
     * @return 随机元素
     */
    fun <T> randomEle(array: Array<T>): T = randomEle(array, array.size)

    /**
     * 随机获得数组中的元素
     *
     * @param array 列表
     * @param limit 限制列表的前N项
     * @return 随机元素
     */
    fun <T> randomEle(array: Array<T>, limit: Int): T {
        val actualLimit = if (array.size < limit) array.size else limit
        return array[randomInt(actualLimit)]
    }

    /**
     * 获得一个随机的字符串（只包含数字和字符）
     *
     * @param length 字符串的长度
     * @return 随机字符串
     */
    fun randomString(length: Int): String = randomString(BASE_CHAR_NUMBER, length)

    /**
     * 获得一个随机的字符串（只包含数字和大写字符）
     *
     * @param length 字符串的长度
     * @return 随机字符串
     */
    fun randomStringUpper(length: Int): String = randomString(BASE_CHAR_NUMBER, length).uppercase()

    /**
     * 获得一个只包含数字的字符串
     *
     * @param length 字符串的长度
     * @return 随机字符串
     */
    fun randomNumbers(length: Int): String = randomString(BASE_NUMBER, length)

    /**
     * 获得一个随机的字符串
     *
     * @param baseString 随机字符选取的样本
     * @param length 字符串的长度
     * @return 随机字符串
     */
    fun randomString(baseString: String, length: Int): String {
        if (baseString.isEmpty()) {
            return ""
        }
        val actualLength = if (length < 1) 1 else length

        return buildString(actualLength) {
            repeat(actualLength) {
                val number = randomInt(baseString.length)
                append(baseString[number])
            }
        }
    }

    /**
     * 随机汉字（'\u4E00'-'\u9FFF'）
     *
     * @return 随机的汉字字符
     */
    fun randomChinese(): Char = randomInt(0x4E00, 0x9FFF).toChar()

    /**
     * 随机数字，数字为0~9单个数字
     *
     * @return 随机数字字符
     */
    fun randomNumber(): Char = randomChar(BASE_NUMBER)

    /**
     * 随机字母或数字，小写
     *
     * @return 随机字符
     */
    fun randomChar(): Char = randomChar(BASE_CHAR_NUMBER)

    /**
     * 随机字符
     *
     * @param baseString 随机字符选取的样本
     * @return 随机字符
     */
    fun randomChar(baseString: String): Char = baseString[randomInt(baseString.length)]

    /**
     * 带有权重的随机生成器
     *
     * @param weightObjs 带有权重的对象列表
     * @return [WeightRandom]
     */
    fun <T> weightRandom(vararg weightObjs: Pair<T, Number>): WeightRandom<T> = 
        WeightRandom<T>().apply {
            weightObjs.forEach { (obj, weight) ->
                add(obj, weight.toDouble())
            }
        }

    /**
     * 带有权重的随机生成器
     * 
     * @param weightObjs 带有权重的对象列表
     * @return [WeightRandom]
     */
    fun <T> weightRandom(weightObjs: Map<T, Number>): WeightRandom<T> =
        WeightRandom<T>().apply {
            weightObjs.forEach { (obj, weight) ->
                add(obj, weight.toDouble())
            }
        }

}