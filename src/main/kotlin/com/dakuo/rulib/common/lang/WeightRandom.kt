package com.dakuo.rulib.common.lang

import java.util.*

/**
 * 权重随机算法实现<br>
 * <p>
 * 平时，经常会遇到权重随机算法，从不同权重的N个元素中随机选择一个，并使得总体选择结果是按照权重分布的。如广告投放、负载均衡等。
 * </p>
 * <p>
 * 如有4个元素A、B、C、D，权重分别为1、2、3、4，随机结果中A:B:C:D的比例要为1:2:3:4。<br>
 * </p>
 * 总体思路：累加每个元素的权重A(1)-B(3)-C(6)-D(10)，则4个元素的的权重管辖区间分别为[0,1)、[1,3)、[3,6)、[6,10)。<br>
 * 然后随机出一个[0,10)之间的随机数。落在哪个区间，则该区间之后的元素即为按权重命中的元素。<br>
 *
 * <p>
 * 参考博客：https://www.cnblogs.com/waterystone/p/5708063.html
 * <p>
 *
 * @param T 权重随机获取的对象类型
 */
class WeightRandom<T> : java.io.Serializable {
    private val weightMap: TreeMap<Double, T> = TreeMap()

    companion object {
        private const val serialVersionUID = -8244697995702786499L

        /**
         * 创建权重随机获取器
         */
        fun <T> create(): WeightRandom<T> = WeightRandom()
    }

    /**
     * 构造
     */
    constructor()

    /**
     * 构造
     *
     * @param weightObjs 带有权重的对象列表
     */
    constructor(weightObjs: Iterable<WeightObj<T>>) {
        weightObjs.forEach { add(it) }
    }

    
    /**
     * 增加对象
     *
     * @param obj 对象
     * @param weight 权重
     * @return this
     */
    fun add(obj: T, weight: Double): WeightRandom<T> = add(WeightObj(obj, weight))

    /**
     * 增加对象权重
     *
     * @param weightObj 权重对象
     * @return this
     */
    fun add(weightObj: WeightObj<T>?): WeightRandom<T> {
        if (weightObj != null) {
            val weight = weightObj.weight
            if (weight > 0) {
                val lastWeight = if (weightMap.isEmpty()) 0.0 else weightMap.lastKey()
                weightMap[weight + lastWeight] = weightObj.obj // 权重累加
            }
        }
        return this
    }

    /**
     * 清空权重表
     *
     * @return this
     */
    fun clear(): WeightRandom<T> {
        weightMap.clear()
        return this
    }

    /**
     * 下一个随机对象
     *
     * @return 随机对象
     */
    fun next(): T? {
        if (weightMap.isEmpty()) {
            return null
        }
        val random = RandomUtil.getRandom()
        val randomWeight = weightMap.lastKey() * random.nextDouble()
        val tailMap = weightMap.tailMap(randomWeight, false)
        return weightMap[tailMap.firstKey()]
    }

    /**
     * 带有权重的对象包装
     */
    class WeightObj<T>(
        var obj: T,
        val weight: Double
    ) {
        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + (obj?.hashCode() ?: 0)
            val temp = java.lang.Double.doubleToLongBits(weight)
            result = prime * result + (temp xor (temp ushr 32)).toInt()
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (javaClass != other.javaClass) return false

            other as WeightObj<*>

            if (obj == null) {
                if (other.obj != null) return false
            } else if (obj != other.obj) return false

            return java.lang.Double.doubleToLongBits(weight) == java.lang.Double.doubleToLongBits(other.weight)
        }
    }
}