package com.dakuo.rulib.common.database

// ===== 枚举 =====

enum class Direction { ASC, DESC }
enum class JoinType(val sql: String) {
    INNER("INNER JOIN"),
    LEFT("LEFT JOIN"),
    RIGHT("RIGHT JOIN"),
    CROSS("CROSS JOIN")
}

// ===== Condition 密封类 =====

sealed class Condition {
    abstract fun toSql(): String
    abstract fun getParams(): List<Any?>

    // 基本比较
    class Eq(val column: String, val value: Any?) : Condition() {
        override fun toSql() = if (value == null) "$column IS NULL" else "$column = ?"
        override fun getParams() = if (value == null) emptyList() else listOf(value)
    }

    class Ne(val column: String, val value: Any?) : Condition() {
        override fun toSql() = if (value == null) "$column IS NOT NULL" else "$column != ?"
        override fun getParams() = if (value == null) emptyList() else listOf(value)
    }

    class Gt(val column: String, val value: Any?) : Condition() {
        override fun toSql() = "$column > ?"
        override fun getParams() = listOf(value)
    }

    class Gte(val column: String, val value: Any?) : Condition() {
        override fun toSql() = "$column >= ?"
        override fun getParams() = listOf(value)
    }

    class Lt(val column: String, val value: Any?) : Condition() {
        override fun toSql() = "$column < ?"
        override fun getParams() = listOf(value)
    }

    class Lte(val column: String, val value: Any?) : Condition() {
        override fun toSql() = "$column <= ?"
        override fun getParams() = listOf(value)
    }

    // 模式匹配
    class Like(val column: String, val pattern: String) : Condition() {
        override fun toSql() = "$column LIKE ?"
        override fun getParams() = listOf<Any?>(pattern)
    }

    class NotLike(val column: String, val pattern: String) : Condition() {
        override fun toSql() = "$column NOT LIKE ?"
        override fun getParams() = listOf<Any?>(pattern)
    }

    class Between(val column: String, val low: Any?, val high: Any?) : Condition() {
        override fun toSql() = "$column BETWEEN ? AND ?"
        override fun getParams() = listOf(low, high)
    }

    // 集合
    class InList(val column: String, val values: List<Any?>) : Condition() {
        override fun toSql() = "$column IN (${values.joinToString(",") { "?" }})"
        override fun getParams() = values
    }

    class NotInList(val column: String, val values: List<Any?>) : Condition() {
        override fun toSql() = "$column NOT IN (${values.joinToString(",") { "?" }})"
        override fun getParams() = values
    }

    // 子查询
    class InSubquery(val column: String, val sub: QueryBuilder) : Condition() {
        override fun toSql() = "$column IN (${sub.toSql()})"
        override fun getParams() = sub.getParams()
    }

    class NotInSubquery(val column: String, val sub: QueryBuilder) : Condition() {
        override fun toSql() = "$column NOT IN (${sub.toSql()})"
        override fun getParams() = sub.getParams()
    }

    class Exists(val sub: QueryBuilder) : Condition() {
        override fun toSql() = "EXISTS (${sub.toSql()})"
        override fun getParams() = sub.getParams()
    }

    class NotExists(val sub: QueryBuilder) : Condition() {
        override fun toSql() = "NOT EXISTS (${sub.toSql()})"
        override fun getParams() = sub.getParams()
    }

    // NULL
    class IsNull(val column: String) : Condition() {
        override fun toSql() = "$column IS NULL"
        override fun getParams() = emptyList<Any?>()
    }

    class IsNotNull(val column: String) : Condition() {
        override fun toSql() = "$column IS NOT NULL"
        override fun getParams() = emptyList<Any?>()
    }

    // 原始 SQL
    class Raw(val sql: String, private val rawParams: List<Any?>) : Condition() {
        override fun toSql() = sql
        override fun getParams() = rawParams
    }

    // 组合
    class And(val conditions: List<Condition>) : Condition() {
        override fun toSql(): String {
            if (conditions.size == 1) return conditions[0].toSql()
            return conditions.joinToString(" AND ") { "(${it.toSql()})" }
        }
        override fun getParams() = conditions.flatMap { it.getParams() }
    }

    class Or(val conditions: List<Condition>) : Condition() {
        override fun toSql(): String {
            if (conditions.size == 1) return conditions[0].toSql()
            return conditions.joinToString(" OR ") { "(${it.toSql()})" }
        }
        override fun getParams() = conditions.flatMap { it.getParams() }
    }

    class Not(val condition: Condition) : Condition() {
        override fun toSql() = "NOT (${condition.toSql()})"
        override fun getParams() = condition.getParams()
    }
}

// ===== 中缀运算符 =====

infix fun String.eq(value: Any?): Condition = Condition.Eq(this, value)
infix fun String.ne(value: Any?): Condition = Condition.Ne(this, value)
infix fun String.gt(value: Any?): Condition = Condition.Gt(this, value)
infix fun String.gte(value: Any?): Condition = Condition.Gte(this, value)
infix fun String.lt(value: Any?): Condition = Condition.Lt(this, value)
infix fun String.lte(value: Any?): Condition = Condition.Lte(this, value)
infix fun String.like(pattern: String): Condition = Condition.Like(this, pattern)
infix fun String.notLike(pattern: String): Condition = Condition.NotLike(this, pattern)
infix fun String.between(range: Pair<Any?, Any?>): Condition = Condition.Between(this, range.first, range.second)
infix fun String.inList(values: List<Any?>): Condition = Condition.InList(this, values)
infix fun String.notInList(values: List<Any?>): Condition = Condition.NotInList(this, values)
infix fun String.inSubquery(sub: QueryBuilder): Condition = Condition.InSubquery(this, sub)
infix fun String.notInSubquery(sub: QueryBuilder): Condition = Condition.NotInSubquery(this, sub)
fun String.isNull(): Condition = Condition.IsNull(this)
fun String.isNotNull(): Condition = Condition.IsNotNull(this)

// 组合（扁平化嵌套）
infix fun Condition.and(other: Condition): Condition {
    val left = if (this is Condition.And) this.conditions else listOf(this)
    val right = if (other is Condition.And) other.conditions else listOf(other)
    return Condition.And(left + right)
}

infix fun Condition.or(other: Condition): Condition {
    val left = if (this is Condition.Or) this.conditions else listOf(this)
    val right = if (other is Condition.Or) other.conditions else listOf(other)
    return Condition.Or(left + right)
}

fun not(condition: Condition): Condition = Condition.Not(condition)

// 原始 SQL 条件
fun raw(sql: String, vararg params: Any?): Condition = Condition.Raw(sql, params.toList())

// EXISTS 工厂
fun exists(sub: QueryBuilder): Condition = Condition.Exists(sub)
fun notExists(sub: QueryBuilder): Condition = Condition.NotExists(sub)

// 子查询工厂
fun subquery(table: String, block: QueryBuilder.() -> Unit): QueryBuilder {
    return QueryBuilder(escapeIdentifier(table)).apply(block)
}

// ===== JOIN 子句 =====

internal data class JoinClause(
    val type: JoinType,
    val table: String,
    val alias: String?,
    val on: String
)

// ===== QueryBuilder =====

class QueryBuilder internal constructor(private val table: String) {
    private var selectColumns: String = "*"
    private var tableAlias: String? = null
    private var isDistinct: Boolean = false
    private val joins = mutableListOf<JoinClause>()
    private val conditions = mutableListOf<Condition>()
    private val groupByColumns = mutableListOf<String>()
    private var havingCondition: Condition? = null
    private val orderBys = mutableListOf<Pair<String, Direction>>()
    private var limitCount: Int? = null
    private var offsetCount: Int? = null

    fun columns(vararg cols: String) {
        selectColumns = cols.joinToString(", ")
    }

    fun alias(alias: String) {
        tableAlias = alias
    }

    fun distinct() {
        isDistinct = true
    }

    // JOIN
    fun join(table: String, alias: String? = null, on: String, type: JoinType = JoinType.INNER) {
        joins += JoinClause(type, table, alias, on)
    }

    fun innerJoin(table: String, alias: String? = null, on: String) = join(table, alias, on, JoinType.INNER)
    fun leftJoin(table: String, alias: String? = null, on: String) = join(table, alias, on, JoinType.LEFT)
    fun rightJoin(table: String, alias: String? = null, on: String) = join(table, alias, on, JoinType.RIGHT)

    // WHERE
    fun where(condition: Condition) {
        conditions += condition
    }

    fun whereIf(test: Boolean, provider: () -> Condition) {
        if (test) conditions += provider()
    }

    fun whereRaw(sql: String, vararg params: Any?) {
        conditions += Condition.Raw(sql, params.toList())
    }

    // GROUP BY / HAVING
    fun groupBy(vararg columns: String) {
        groupByColumns += columns
    }

    fun having(condition: Condition) {
        havingCondition = condition
    }

    // ORDER BY
    fun orderBy(column: String, direction: Direction = Direction.ASC) {
        orderBys += column to direction
    }

    // LIMIT / OFFSET
    fun limit(count: Int) {
        limitCount = count
    }

    fun offset(count: Int) {
        offsetCount = count
    }

    // 构建 SQL
    fun toSql(): String {
        val sb = StringBuilder()

        // SELECT
        sb.append("SELECT ")
        if (isDistinct) sb.append("DISTINCT ")
        sb.append(selectColumns)

        // FROM
        sb.append(" FROM ").append(table)
        if (tableAlias != null) sb.append(" AS ").append(tableAlias)

        // JOIN
        for (join in joins) {
            sb.append(" ").append(join.type.sql).append(" ")
            sb.append(escapeIdentifier(join.table))
            if (join.alias != null) sb.append(" AS ").append(join.alias)
            sb.append(" ON ").append(join.on)
        }

        // WHERE
        if (conditions.isNotEmpty()) {
            val whereSql = if (conditions.size == 1) {
                conditions[0].toSql()
            } else {
                conditions.joinToString(" AND ") { "(${it.toSql()})" }
            }
            sb.append(" WHERE ").append(whereSql)
        }

        // GROUP BY
        if (groupByColumns.isNotEmpty()) {
            sb.append(" GROUP BY ").append(groupByColumns.joinToString(", "))
        }

        // HAVING
        if (havingCondition != null) {
            sb.append(" HAVING ").append(havingCondition!!.toSql())
        }

        // ORDER BY
        if (orderBys.isNotEmpty()) {
            sb.append(" ORDER BY ").append(orderBys.joinToString(", ") { "${it.first} ${it.second}" })
        }

        // LIMIT / OFFSET
        if (limitCount != null) {
            sb.append(" LIMIT ").append(limitCount)
        }
        if (offsetCount != null) {
            sb.append(" OFFSET ").append(offsetCount)
        }

        return sb.toString()
    }

    fun getParams(): List<Any?> {
        val params = mutableListOf<Any?>()
        conditions.forEach { params.addAll(it.getParams()) }
        havingCondition?.let { params.addAll(it.getParams()) }
        return params
    }
}

// ===== UpdateBuilder =====

class UpdateBuilder internal constructor(private val table: String) {
    private val sets = mutableListOf<Pair<String, Any?>>()
    private val conditions = mutableListOf<Condition>()

    fun set(column: String, value: Any?) {
        sets += column to value
    }

    fun where(condition: Condition) {
        conditions += condition
    }

    fun whereIf(test: Boolean, provider: () -> Condition) {
        if (test) conditions += provider()
    }

    fun whereRaw(sql: String, vararg params: Any?) {
        conditions += Condition.Raw(sql, params.toList())
    }

    fun toSql(): String {
        require(sets.isNotEmpty()) { "UPDATE 必须至少设置一个字段" }
        val sb = StringBuilder()
        sb.append("UPDATE ").append(table).append(" SET ")
        sb.append(sets.joinToString(", ") { "${escapeIdentifier(it.first)} = ?" })
        if (conditions.isNotEmpty()) {
            val whereSql = if (conditions.size == 1) {
                conditions[0].toSql()
            } else {
                conditions.joinToString(" AND ") { "(${it.toSql()})" }
            }
            sb.append(" WHERE ").append(whereSql)
        }
        return sb.toString()
    }

    fun getParams(): List<Any?> {
        val params = mutableListOf<Any?>()
        sets.forEach { params.add(it.second) }
        conditions.forEach { params.addAll(it.getParams()) }
        return params
    }
}

// ===== DeleteBuilder =====

class DeleteBuilder internal constructor(private val table: String) {
    private val conditions = mutableListOf<Condition>()

    fun where(condition: Condition) {
        conditions += condition
    }

    fun whereIf(test: Boolean, provider: () -> Condition) {
        if (test) conditions += provider()
    }

    fun whereRaw(sql: String, vararg params: Any?) {
        conditions += Condition.Raw(sql, params.toList())
    }

    fun toSql(): String {
        val sb = StringBuilder()
        sb.append("DELETE FROM ").append(table)
        if (conditions.isNotEmpty()) {
            val whereSql = if (conditions.size == 1) {
                conditions[0].toSql()
            } else {
                conditions.joinToString(" AND ") { "(${it.toSql()})" }
            }
            sb.append(" WHERE ").append(whereSql)
        }
        return sb.toString()
    }

    fun getParams(): List<Any?> {
        return conditions.flatMap { it.getParams() }
    }
}
