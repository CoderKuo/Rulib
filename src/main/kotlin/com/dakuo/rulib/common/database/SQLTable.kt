package com.dakuo.rulib.common.database

/**
 * SQLTable 使用示例:
 *
 * // 1. 定义表结构
 * val userTable = SQLTable.table("users") {
 *     column("id") {
 *         type(BIGINT)
 *         primaryKey()
 *         autoIncrement()
 *     }
 *     column("username") {
 *         type(VARCHAR(50))
 *         notNull()
 *     }
 *     column("age") {
 *         type(INT)
 *         default("0")
 *     }
 *     column("balance") {
 *         type(DECIMAL(10, 2))
 *         default("0.00")
 *     }
 *     column("description") {
 *         type(TEXT)
 *     }
 *     column("created_at") {
 *         type(TIMESTAMP)
 *         notNull()
 *         default("CURRENT_TIMESTAMP")
 *     }
 * }
 *
 * // 2. 创建表
 * userTable.create()
 *
 * // 2.1 创建索引
 * userTable.createIndex("idx_username", "username")
 * userTable.createUniqueIndex("idx_email", "email")
 * userTable.createIndex("idx_age_balance", listOf("age", "balance"))
 *
 * // 3. 插入数据
 * userTable.insert(mapOf(
 *     "username" to "张三",
 *     "age" to 18,
 *     "balance" to 100.50,
 *     "description" to "这是一段描述"
 * ))
 *
 * // 4. 更新数据
 * userTable.update(
 *     data = mapOf("age" to 20),
 *     where = "username = ?",
 *     params = "张三"
 * )
 *
 * // 5. 查询数据
 * val users = userTable.select(
 *     where = "age > ?",
 *     mapper = { rs ->
 *         User(
 *             id = rs.getLong("id"),
 *             username = rs.getString("username"),
 *             age = rs.getInt("age"),
 *             balance = rs.getBigDecimal("balance")
 *         )
 *     },
 *     params = 18
 * )
 *
 * // 6. 删除数据
 * userTable.delete("username = ?", "张三")
 */
class SQLTable private constructor(
    private val tableName: String,
    private val columns: MutableList<Column> = mutableListOf(),
    private val indexes: MutableList<Triple<String, Any, Boolean>> = mutableListOf()
) {

    /**
     * 创建表
     */
    fun create() {
        val columnDefinitions = columns.joinToString(",\n") { it.getDefinition() }
        val sql = """
            CREATE TABLE IF NOT EXISTS $tableName (
            $columnDefinitions
            )
        """.trimIndent()
        SQLTemplate.update(sql)
        
        // 创建表后再创建索引
        indexes.forEach { (name, cols, unique) ->
            createIndex(name, cols, unique)
        }
    }

    /**
     * 检查索引是否存在
     */
    private fun indexExists(indexName: String): Boolean {
        return when (Database.getDatabaseType()!!.lowercase()) {
            "mysql" -> {
                val sql = """
                    SELECT COUNT(*) FROM information_schema.STATISTICS 
                    WHERE table_schema = DATABASE() 
                    AND TABLE_NAME = ? 
                    AND INDEX_NAME = ?
                """.trimIndent()
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName, indexName).first() > 0
            }
            "sqlite" -> {
                val sql = """
                    SELECT COUNT(*) FROM sqlite_master 
                    WHERE type = 'index' 
                    AND tbl_name = ? 
                    AND name = ?
                """.trimIndent()
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName, indexName).first() > 0
            }
            else -> throw IllegalArgumentException("Unsupported database type")
        }
    }

    /**
     * 创建索引
     * @param indexName 索引名称
     * @param columns 索引列，可以是单个列名或列名列表
     * @param unique 是否为唯一索引
     */
    fun createIndex(indexName: String, columns: Any, unique: Boolean = false) {
        if (indexExists(indexName)) {
            return
        }

        val columnList = when (columns) {
            is String -> listOf(columns)
            is List<*> -> columns.filterIsInstance<String>()
            else -> throw IllegalArgumentException("columns must be String or List<String>")
        }
        
        val uniqueStr = if (unique) "UNIQUE" else ""
        val sql = "CREATE $uniqueStr INDEX $indexName ON $tableName (${columnList.joinToString(",")})"
        SQLTemplate.update(sql)
    }

    /**
     * 创建普通索引
     */
    fun createIndex(indexName: String, columns: Any) {
        createIndex(indexName, columns, false)
    }

    /**
     * 创建唯一索引
     */
    fun createUniqueIndex(indexName: String, columns: Any) {
        createIndex(indexName, columns, true)
    }

    /**
     * 删除索引
     */
    fun dropIndex(indexName: String) {
        if (!indexExists(indexName)) {
            return
        }
        
        val sql = when (Database.getDatabaseType()!!.lowercase()) {
            "mysql" -> "DROP INDEX $indexName ON $tableName"
            "sqlite" -> "DROP INDEX IF EXISTS $indexName"
            else -> throw IllegalArgumentException("Unsupported database type")
        }
        SQLTemplate.update(sql)
    }

    /**
     * 插入数据
     */
    fun insert(data: Map<String, Any?>) {
        val columns = data.keys.joinToString(",")
        val placeholders = data.keys.joinToString(",") { "?" }
        val sql = "INSERT INTO $tableName ($columns) VALUES ($placeholders)"
        SQLTemplate.update(sql, *data.values.toTypedArray())
    }

    /**
     * 更新数据
     */
    fun update(data: Map<String, Any?>, where: String, vararg params: Any?) {
        val setClause = data.keys.joinToString(",") { "$it=?" }
        val sql = "UPDATE $tableName SET $setClause WHERE $where"
        val allParams = data.values.toList() + params.toList()
        SQLTemplate.update(sql, *allParams.toTypedArray())
    }

    /**
     * 删除数据
     */
    fun delete(where: String, vararg params: Any?) {
        val sql = "DELETE FROM $tableName WHERE $where"
        SQLTemplate.update(sql, *params)
    }

    /**
     * 查询数据
     */
    fun <T> select(
        columns: String = "*",
        where: String? = null,
        mapper: (java.sql.ResultSet) -> T,
        vararg params: Any?
    ): List<T> {
        val whereClause = where?.let { "WHERE $it" } ?: ""
        val sql = "SELECT $columns FROM $tableName $whereClause"
        return SQLTemplate.query(sql, mapper, *params)
    }
    /**
     * 表字段定义
     */
    class Column(
        val name: String,
        val type: ColumnType,
        val primaryKey: Boolean = false,
        val autoIncrement: Boolean = false,
        val notNull: Boolean = false,
        val defaultValue: String? = null
    ) {
        fun getDefinition(): String {
            val constraints = mutableListOf<String>()
            if (primaryKey) {
                if (autoIncrement) {
                    if (Database.getDatabaseType()!!.lowercase() == "sqlite") {
                        // SQLite的自增主键必须是INTEGER PRIMARY KEY
                        if (type !is ColumnType.Int) {
                            throw IllegalStateException("SQLite AUTOINCREMENT column must be INTEGER type")
                        }
                        return "$name INTEGER PRIMARY KEY AUTOINCREMENT"
                    } else {
                        constraints.add("PRIMARY KEY AUTO_INCREMENT")
                    }
                } else {
                    constraints.add("PRIMARY KEY")
                }
            }
            if (notNull) constraints.add("NOT NULL")
            if (defaultValue != null) constraints.add("DEFAULT $defaultValue")

            return "$name ${type.toSql(Database.getDatabaseType()!!)} ${constraints.joinToString(" ")}"
        }
    }

    // DSL构建器
    class Builder(private val tableName: String) {
        private val columns = mutableListOf<Column>()
        private val indexes = mutableListOf<Triple<String, Any, Boolean>>()

        fun column(name: String, init: ColumnBuilder.() -> Unit) {
            val builder = ColumnBuilder(name)
            builder.init()
            columns.add(builder.build())
        }

        fun index(name: String, columns: Any, unique: Boolean = false) {
            indexes.add(Triple(name, columns, unique))
        }

        fun uniqueIndex(name: String, columns: Any) {
            index(name, columns, true)
        }

        fun build(): SQLTable {
            return SQLTable(tableName, columns, indexes)
        }
    }

    // 字段DSL构建器
    class ColumnBuilder(private val name: String) {
        private var type: ColumnType = ColumnType.VarChar(255)
        private var primaryKey: Boolean = false
        private var autoIncrement: Boolean = false
        private var notNull: Boolean = false
        private var defaultValue: String? = null

        fun type(type: ColumnType) {
            this.type = type
        }

        fun primaryKey() {
            this.primaryKey = true
        }

        fun autoIncrement() {
            this.autoIncrement = true
        }

        fun notNull() {
            this.notNull = true
        }

        fun default(value: String) {
            this.defaultValue = value
        }

        fun build() = Column(name, type, primaryKey, autoIncrement, notNull, defaultValue)
    }

    companion object {
        // 数据类型
        sealed class ColumnType {
            object Int : ColumnType()
            object BigInt : ColumnType()
            object TinyInt : ColumnType()
            object Text : ColumnType()
            object LongText : ColumnType()
            object Blob : ColumnType()
            object LongBlob : ColumnType()
            object Double : ColumnType()
            object Float : ColumnType()
            object Boolean : ColumnType()
            object Date : ColumnType()
            object Time : ColumnType()
            object Timestamp : ColumnType()
            object DateTime : ColumnType()
            data class VarChar(val length: kotlin.Int) : ColumnType()
            data class Char(val length: kotlin.Int) : ColumnType()
            data class Decimal(val precision: kotlin.Int, val scale: kotlin.Int) : ColumnType()

            fun isInteger() = this is Int || this is BigInt || this is TinyInt

            fun toSql(databaseType: String): String = when (databaseType.lowercase()) {
                "mysql" -> when (this) {
                    is VarChar -> "VARCHAR($length)"
                    is Char -> "CHAR($length)"
                    is Decimal -> "DECIMAL($precision,$scale)"
                    is Int -> "INT"
                    is BigInt -> "BIGINT"
                    is TinyInt -> "TINYINT"
                    is Text -> "TEXT"
                    is LongText -> "LONGTEXT"
                    is Blob -> "BLOB"
                    is LongBlob -> "LONGBLOB"
                    is Double -> "DOUBLE"
                    is Float -> "FLOAT"
                    is Boolean -> "BOOLEAN"
                    is Date -> "DATE"
                    is Time -> "TIME"
                    is Timestamp -> "TIMESTAMP"
                    is DateTime -> "DATETIME"
                }

                "sqlite" -> when (this) {
                    is VarChar, is Char, is Text, is LongText -> "TEXT"
                    is Decimal -> "REAL"
                    is Int, is TinyInt -> "INTEGER"
                    is BigInt -> "INTEGER"
                    is Blob, is LongBlob -> "BLOB"
                    is Double, is Float -> "REAL"
                    is Boolean -> "INTEGER"
                    is Date, is Time, is Timestamp, is DateTime -> "TEXT"
                }

                else -> throw IllegalArgumentException("Unsupported database type: $databaseType")
            }
        }

        // DSL入口
        fun table(name: String, init: Builder.() -> Unit): SQLTable {
            val builder = Builder(name)
            builder.init()
            return builder.build()
        }
    }

    /**
     * 在一个连接中执行多个数据库操作
     */
    fun <R> workspace(block: TableContext.() -> R): R {
        return SQLTemplate.workspace {
            TableContext(this, tableName).block()
        }
    }

    /**
     * 在事务中执行多个数据库操作
     */
    fun <R> transaction(block: TableContext.() -> R): R {
        return workspace {
            transaction {
                block()
            }
        }
    }

    /**
     * 表操作上下文
     */
    inner class TableContext(
        private val sqlContext: SQLTemplate.SQLContext,
        private val tableName: String
    ) {
        /**
         * 插入数据
         */
        fun insert(data: Map<String, Any?>) {
            val columns = data.keys.joinToString(",")
            val placeholders = data.keys.joinToString(",") { "?" }
            val sql = "INSERT INTO $tableName ($columns) VALUES ($placeholders)"
            sqlContext.update(sql, *data.values.toTypedArray())
        }

        /**
         * 更新数据
         */
        fun update(data: Map<String, Any?>, where: String, vararg params: Any?): Int {
            val setClause = data.keys.joinToString(",") { "$it=?" }
            val sql = "UPDATE $tableName SET $setClause WHERE $where"
            val allParams = data.values.toList() + params.toList()
            return sqlContext.update(sql, *allParams.toTypedArray())
        }

        /**
         * 删除数据
         */
        fun delete(where: String, vararg params: Any?) {
            val sql = "DELETE FROM $tableName WHERE $where"
            sqlContext.update(sql, *params)
        }

        /**
         * 查询数据
         */
        fun <T> select(
            columns: String = "*",
            where: String? = null,
            mapper: (java.sql.ResultSet) -> T,
            vararg params: Any?
        ): List<T> {
            val whereClause = where?.let { "WHERE $it" } ?: ""
            val sql = "SELECT $columns FROM $tableName $whereClause"
            return sqlContext.query(sql, mapper, *params)
        }

        /**
         * 查询单条数据
         */
        fun <T> selectOne(
            columns: String = "*",
            where: String? = null,
            mapper: (java.sql.ResultSet) -> T,
            vararg params: Any?
        ): T? {
            return select(columns, where, mapper, *params).firstOrNull()
        }

        /**
         * 开启事务
         */
        fun beginTransaction() = sqlContext.beginTransaction()

        /**
         * 提交事务
         */
        fun commit() = sqlContext.commit()

        /**
         * 回滚事务
         */
        fun rollback() = sqlContext.rollback()

        /**
         * 在事务中执行操作
         */
        fun <R> transaction(block: () -> R): R {
            beginTransaction()
            try {
                val result = block()
                commit()
                return result
            } catch (e: Exception) {
                rollback()
                throw e
            }
        }
    }
}