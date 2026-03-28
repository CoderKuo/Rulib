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
 *
 * // 7. 查询单条数据
 * val user = userTable.selectOne(
 *     where = "id = ?",
 *     mapper = { rs -> rs.getString("username") },
 *     params = 1
 * )
 *
 * // 8. 计数与存在检查
 * val total = userTable.count()
 * val adultCount = userTable.count("age >= ?", 18)
 * val exists = userTable.exists("username = ?", "张三")
 *
 * // 9. 插入或更新（Upsert）
 * userTable.insertOrUpdate(
 *     data = mapOf("id" to 1, "username" to "张三", "age" to 25),
 *     keys = listOf("id")
 * )
 *
 * // 10. 批量插入
 * userTable.batchInsert(listOf(
 *     mapOf("username" to "张三", "age" to 18),
 *     mapOf("username" to "李四", "age" to 20)
 * ))
 *
 * // 11. 检查表是否存在
 * if (userTable.tableExists()) { ... }
 *
 * // 12. 添加新列（表结构迁移）
 * userTable.addColumn("email") {
 *     type(VARCHAR(100))
 *     default("''")
 * }
 */
class SQLTable private constructor(
    private val tableName: String,
    private val columns: MutableList<Column> = mutableListOf(),
    private val indexes: MutableList<Triple<String, Any, Boolean>> = mutableListOf()
) {
    private val safeTableName by lazy { escapeIdentifier(tableName) }

    /**
     * 创建表
     */
    fun create() {
        val columnDefinitions = columns.joinToString(",\n") { it.getDefinition() }
        val sql = """
            CREATE TABLE IF NOT EXISTS $safeTableName (
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
     * 检查表是否存在
     */
    fun tableExists(): Boolean {
        return when (Database.getDatabaseType()!!.lowercase()) {
            "mysql" -> {
                val sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName).first() > 0
            }
            "sqlite" -> {
                val sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?"
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName).first() > 0
            }
            "postgresql" -> {
                val sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?"
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName).first() > 0
            }
            else -> throw IllegalArgumentException("Unsupported database type")
        }
    }

    /**
     * 检查列是否存在
     */
    private fun columnExists(columnName: String): Boolean {
        return when (Database.getDatabaseType()!!.lowercase()) {
            "mysql" -> {
                val sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?"
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName, columnName).first() > 0
            }
            "sqlite" -> {
                val results = SQLTemplate.query("PRAGMA table_info($safeTableName)", { rs -> rs.getString("name") })
                results.any { it == columnName }
            }
            "postgresql" -> {
                val sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?"
                SQLTemplate.query(sql, { rs -> rs.getInt(1) }, tableName, columnName).first() > 0
            }
            else -> throw IllegalArgumentException("Unsupported database type")
        }
    }

    /**
     * 添加新列（表结构迁移），如果列已存在则跳过
     */
    fun addColumn(name: String, init: ColumnBuilder.() -> Unit) {
        if (columnExists(name)) return
        val builder = ColumnBuilder(name)
        builder.init()
        val column = builder.build()
        val sql = "ALTER TABLE $safeTableName ADD COLUMN ${column.getDefinition()}"
        SQLTemplate.update(sql)
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
            "postgresql" -> {
                val sql = """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = 'public'
                    AND tablename = ?
                    AND indexname = ?
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
        val safeIndexName = escapeIdentifier(indexName)
        val safeColumns = columnList.joinToString(",") { escapeIdentifier(it) }
        val sql = "CREATE $uniqueStr INDEX $safeIndexName ON $safeTableName ($safeColumns)"
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

        val safeIndexName = escapeIdentifier(indexName)
        val sql = when (Database.getDatabaseType()!!.lowercase()) {
            "mysql" -> "DROP INDEX $safeIndexName ON $safeTableName"
            "sqlite", "postgresql" -> "DROP INDEX IF EXISTS $safeIndexName"
            else -> throw IllegalArgumentException("Unsupported database type")
        }
        SQLTemplate.update(sql)
    }

    /**
     * 插入数据
     */
    fun insert(data: Map<String, Any?>) {
        val columns = data.keys.joinToString(",") { escapeIdentifier(it) }
        val placeholders = data.keys.joinToString(",") { "?" }
        val sql = "INSERT INTO $safeTableName ($columns) VALUES ($placeholders)"
        SQLTemplate.update(sql, *data.values.toTypedArray())
    }

    /**
     * 插入或更新数据（Upsert）
     * @param data 要插入/更新的数据
     * @param keys 用于冲突检测的唯一键列名列表
     */
    fun insertOrUpdate(data: Map<String, Any?>, keys: List<String>) {
        val columns = data.keys.joinToString(",") { escapeIdentifier(it) }
        val placeholders = data.keys.joinToString(",") { "?" }
        val updateCols = data.keys.filter { it !in keys }

        val sql = if (updateCols.isEmpty()) {
            when (Database.getDatabaseType()!!.lowercase()) {
                "mysql" -> "INSERT IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                "sqlite" -> "INSERT OR IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                "postgresql" -> {
                    val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO NOTHING"
                }
                else -> throw IllegalArgumentException("Unsupported database type")
            }
        } else {
            when (Database.getDatabaseType()!!.lowercase()) {
                "mysql" -> {
                    val updateClause = updateCols.joinToString(",") {
                        "${escapeIdentifier(it)}=VALUES(${escapeIdentifier(it)})"
                    }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON DUPLICATE KEY UPDATE $updateClause"
                }
                "sqlite", "postgresql" -> {
                    val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                    val updateClause = updateCols.joinToString(",") {
                        "${escapeIdentifier(it)}=excluded.${escapeIdentifier(it)}"
                    }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO UPDATE SET $updateClause"
                }
                else -> throw IllegalArgumentException("Unsupported database type")
            }
        }
        SQLTemplate.update(sql, *data.values.toTypedArray())
    }

    /**
     * 批量插入数据（自动包裹事务）
     */
    fun batchInsert(dataList: List<Map<String, Any?>>) {
        if (dataList.isEmpty()) return
        val keys = dataList.first().keys
        val columns = keys.joinToString(",") { escapeIdentifier(it) }
        val placeholders = keys.joinToString(",") { "?" }
        val sql = "INSERT INTO $safeTableName ($columns) VALUES ($placeholders)"
        val params = dataList.map { data -> keys.map { data[it] }.toTypedArray() }
        SQLTemplate.batch(sql, params)
    }

    /**
     * 批量插入或更新数据（自动包裹事务）
     * @param dataList 要插入/更新的数据列表（所有 Map 的 key 必须一致）
     * @param keys 用于冲突检测的唯一键列名列表
     */
    fun batchInsertOrUpdate(dataList: List<Map<String, Any?>>, keys: List<String>) {
        if (dataList.isEmpty()) return
        val colNames = dataList.first().keys
        val columns = colNames.joinToString(",") { escapeIdentifier(it) }
        val placeholders = colNames.joinToString(",") { "?" }
        val updateCols = colNames.filter { it !in keys }

        val sql = if (updateCols.isEmpty()) {
            when (Database.getDatabaseType()!!.lowercase()) {
                "mysql" -> "INSERT IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                "sqlite" -> "INSERT OR IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                "postgresql" -> {
                    val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO NOTHING"
                }
                else -> throw IllegalArgumentException("Unsupported database type")
            }
        } else {
            when (Database.getDatabaseType()!!.lowercase()) {
                "mysql" -> {
                    val updateClause = updateCols.joinToString(",") {
                        "${escapeIdentifier(it)}=VALUES(${escapeIdentifier(it)})"
                    }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON DUPLICATE KEY UPDATE $updateClause"
                }
                "sqlite", "postgresql" -> {
                    val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                    val updateClause = updateCols.joinToString(",") {
                        "${escapeIdentifier(it)}=excluded.${escapeIdentifier(it)}"
                    }
                    "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO UPDATE SET $updateClause"
                }
                else -> throw IllegalArgumentException("Unsupported database type")
            }
        }
        val params = dataList.map { data -> colNames.map { data[it] }.toTypedArray() }
        SQLTemplate.batch(sql, params)
    }

    /**
     * 更新数据
     */
    fun update(data: Map<String, Any?>, where: String, vararg params: Any?) {
        val setClause = data.keys.joinToString(",") { "${escapeIdentifier(it)}=?" }
        val sql = "UPDATE $safeTableName SET $setClause WHERE $where"
        val allParams = data.values.toList() + params.toList()
        SQLTemplate.update(sql, *allParams.toTypedArray())
    }

    /**
     * 删除数据
     */
    fun delete(where: String, vararg params: Any?) {
        val sql = "DELETE FROM $safeTableName WHERE $where"
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
        val sql = "SELECT $columns FROM $safeTableName $whereClause"
        return SQLTemplate.query(sql, mapper, *params)
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
     * 查询记录数
     */
    fun count(where: String? = null, vararg params: Any?): Long {
        val whereClause = where?.let { "WHERE $it" } ?: ""
        val sql = "SELECT COUNT(*) FROM $safeTableName $whereClause"
        return SQLTemplate.query(sql, { rs -> rs.getLong(1) }, *params).first()
    }

    /**
     * 检查是否存在符合条件的记录
     */
    fun exists(where: String, vararg params: Any?): Boolean {
        return count(where, *params) > 0
    }

    // ===== 查询 DSL =====

    /**
     * DSL 查询
     */
    fun <T> query(mapper: (java.sql.ResultSet) -> T, block: QueryBuilder.() -> Unit): List<T> {
        val builder = QueryBuilder(safeTableName).apply(block)
        return SQLTemplate.query(builder.toSql(), mapper, *builder.getParams().toTypedArray())
    }

    /**
     * DSL 查询单条
     */
    fun <T> queryOne(mapper: (java.sql.ResultSet) -> T, block: QueryBuilder.() -> Unit): T? {
        val builder = QueryBuilder(safeTableName).apply { block(); limit(1) }
        return SQLTemplate.query(builder.toSql(), mapper, *builder.getParams().toTypedArray()).firstOrNull()
    }

    /**
     * DSL 计数
     */
    fun count(block: QueryBuilder.() -> Unit): Long {
        val builder = QueryBuilder(safeTableName).apply { columns("COUNT(*)"); block() }
        return SQLTemplate.query(builder.toSql(), { rs -> rs.getLong(1) }, *builder.getParams().toTypedArray()).first()
    }

    /**
     * DSL 存在检查
     */
    fun exists(block: QueryBuilder.() -> Unit): Boolean {
        return count(block) > 0
    }

    /**
     * DSL 更新
     */
    fun update(block: UpdateBuilder.() -> Unit): Int {
        val builder = UpdateBuilder(safeTableName).apply(block)
        return SQLTemplate.update(builder.toSql(), *builder.getParams().toTypedArray())
    }

    /**
     * DSL 删除
     */
    fun delete(block: DeleteBuilder.() -> Unit): Int {
        val builder = DeleteBuilder(safeTableName).apply(block)
        return SQLTemplate.update(builder.toSql(), *builder.getParams().toTypedArray())
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
            val safeName = escapeIdentifier(name)
            val constraints = mutableListOf<String>()
            if (primaryKey) {
                if (autoIncrement) {
                    when (Database.getDatabaseType()!!.lowercase()) {
                        "sqlite" -> {
                            if (type !is ColumnType.Int) {
                                throw IllegalStateException("SQLite AUTOINCREMENT column must be INTEGER type")
                            }
                            return "$safeName INTEGER PRIMARY KEY AUTOINCREMENT"
                        }
                        "postgresql" -> {
                            val serialType = if (type is ColumnType.BigInt) "BIGSERIAL" else "SERIAL"
                            return "$safeName $serialType PRIMARY KEY"
                        }
                        else -> {
                            constraints.add("PRIMARY KEY AUTO_INCREMENT")
                        }
                    }
                } else {
                    constraints.add("PRIMARY KEY")
                }
            }
            if (notNull) constraints.add("NOT NULL")
            if (defaultValue != null) constraints.add("DEFAULT $defaultValue")

            return "$safeName ${type.toSql(Database.getDatabaseType()!!)} ${constraints.joinToString(" ")}"
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

                "postgresql" -> when (this) {
                    is VarChar -> "VARCHAR($length)"
                    is Char -> "CHAR($length)"
                    is Decimal -> "DECIMAL($precision,$scale)"
                    is Int -> "INTEGER"
                    is BigInt -> "BIGINT"
                    is TinyInt -> "SMALLINT"
                    is Text, is LongText -> "TEXT"
                    is Blob, is LongBlob -> "BYTEA"
                    is Double -> "DOUBLE PRECISION"
                    is Float -> "REAL"
                    is Boolean -> "BOOLEAN"
                    is Date -> "DATE"
                    is Time -> "TIME"
                    is Timestamp -> "TIMESTAMP"
                    is DateTime -> "TIMESTAMP"
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
            TableContext(this).block()
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
        private val sqlContext: SQLTemplate.SQLContext
    ) {
        /**
         * 插入数据
         */
        fun insert(data: Map<String, Any?>) {
            val columns = data.keys.joinToString(",") { escapeIdentifier(it) }
            val placeholders = data.keys.joinToString(",") { "?" }
            val sql = "INSERT INTO $safeTableName ($columns) VALUES ($placeholders)"
            sqlContext.update(sql, *data.values.toTypedArray())
        }

        /**
         * 插入或更新数据（Upsert）
         * @param data 要插入/更新的数据
         * @param keys 用于冲突检测的唯一键列名列表
         */
        fun insertOrUpdate(data: Map<String, Any?>, keys: List<String>) {
            val columns = data.keys.joinToString(",") { escapeIdentifier(it) }
            val placeholders = data.keys.joinToString(",") { "?" }
            val updateCols = data.keys.filter { it !in keys }

            val sql = if (updateCols.isEmpty()) {
                when (Database.getDatabaseType()!!.lowercase()) {
                    "mysql" -> "INSERT IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                    "sqlite" -> "INSERT OR IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                    "postgresql" -> {
                        val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO NOTHING"
                    }
                    else -> throw IllegalArgumentException("Unsupported database type")
                }
            } else {
                when (Database.getDatabaseType()!!.lowercase()) {
                    "mysql" -> {
                        val updateClause = updateCols.joinToString(",") {
                            "${escapeIdentifier(it)}=VALUES(${escapeIdentifier(it)})"
                        }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON DUPLICATE KEY UPDATE $updateClause"
                    }
                    "sqlite", "postgresql" -> {
                        val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                        val updateClause = updateCols.joinToString(",") {
                            "${escapeIdentifier(it)}=excluded.${escapeIdentifier(it)}"
                        }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO UPDATE SET $updateClause"
                    }
                    else -> throw IllegalArgumentException("Unsupported database type")
                }
            }
            sqlContext.update(sql, *data.values.toTypedArray())
        }

        /**
         * 批量插入数据
         */
        fun batchInsert(dataList: List<Map<String, Any?>>) {
            if (dataList.isEmpty()) return
            val keys = dataList.first().keys
            val columns = keys.joinToString(",") { escapeIdentifier(it) }
            val placeholders = keys.joinToString(",") { "?" }
            val sql = "INSERT INTO $safeTableName ($columns) VALUES ($placeholders)"
            val params = dataList.map { data -> keys.map { data[it] }.toTypedArray() }
            sqlContext.batch(sql, params)
        }

        /**
         * 批量插入或更新数据
         * @param dataList 要插入/更新的数据列表（所有 Map 的 key 必须一致）
         * @param keys 用于冲突检测的唯一键列名列表
         */
        fun batchInsertOrUpdate(dataList: List<Map<String, Any?>>, keys: List<String>) {
            if (dataList.isEmpty()) return
            val colNames = dataList.first().keys
            val columns = colNames.joinToString(",") { escapeIdentifier(it) }
            val placeholders = colNames.joinToString(",") { "?" }
            val updateCols = colNames.filter { it !in keys }

            val sql = if (updateCols.isEmpty()) {
                when (Database.getDatabaseType()!!.lowercase()) {
                    "mysql" -> "INSERT IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                    "sqlite" -> "INSERT OR IGNORE INTO $safeTableName ($columns) VALUES ($placeholders)"
                    "postgresql" -> {
                        val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO NOTHING"
                    }
                    else -> throw IllegalArgumentException("Unsupported database type")
                }
            } else {
                when (Database.getDatabaseType()!!.lowercase()) {
                    "mysql" -> {
                        val updateClause = updateCols.joinToString(",") {
                            "${escapeIdentifier(it)}=VALUES(${escapeIdentifier(it)})"
                        }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON DUPLICATE KEY UPDATE $updateClause"
                    }
                    "sqlite", "postgresql" -> {
                        val conflictCols = keys.joinToString(",") { escapeIdentifier(it) }
                        val updateClause = updateCols.joinToString(",") {
                            "${escapeIdentifier(it)}=excluded.${escapeIdentifier(it)}"
                        }
                        "INSERT INTO $safeTableName ($columns) VALUES ($placeholders) ON CONFLICT($conflictCols) DO UPDATE SET $updateClause"
                    }
                    else -> throw IllegalArgumentException("Unsupported database type")
                }
            }
            val params = dataList.map { data -> colNames.map { data[it] }.toTypedArray() }
            sqlContext.batch(sql, params)
        }

        /**
         * 更新数据
         */
        fun update(data: Map<String, Any?>, where: String, vararg params: Any?): Int {
            val setClause = data.keys.joinToString(",") { "${escapeIdentifier(it)}=?" }
            val sql = "UPDATE $safeTableName SET $setClause WHERE $where"
            val allParams = data.values.toList() + params.toList()
            return sqlContext.update(sql, *allParams.toTypedArray())
        }

        /**
         * 删除数据
         */
        fun delete(where: String, vararg params: Any?) {
            val sql = "DELETE FROM $safeTableName WHERE $where"
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
            val sql = "SELECT $columns FROM $safeTableName $whereClause"
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
         * 查询记录数
         */
        fun count(where: String? = null, vararg params: Any?): Long {
            val whereClause = where?.let { "WHERE $it" } ?: ""
            val sql = "SELECT COUNT(*) FROM $safeTableName $whereClause"
            return sqlContext.query(sql, { rs -> rs.getLong(1) }, *params).first()
        }

        /**
         * 检查是否存在符合条件的记录
         */
        fun exists(where: String, vararg params: Any?): Boolean {
            return count(where, *params) > 0
        }

        // ===== 查询 DSL =====

        /**
         * DSL 查询
         */
        fun <T> query(mapper: (java.sql.ResultSet) -> T, block: QueryBuilder.() -> Unit): List<T> {
            val builder = QueryBuilder(safeTableName).apply(block)
            return sqlContext.query(builder.toSql(), mapper, *builder.getParams().toTypedArray())
        }

        /**
         * DSL 查询单条
         */
        fun <T> queryOne(mapper: (java.sql.ResultSet) -> T, block: QueryBuilder.() -> Unit): T? {
            val builder = QueryBuilder(safeTableName).apply { block(); limit(1) }
            return sqlContext.query(builder.toSql(), mapper, *builder.getParams().toTypedArray()).firstOrNull()
        }

        /**
         * DSL 计数
         */
        fun count(block: QueryBuilder.() -> Unit): Long {
            val builder = QueryBuilder(safeTableName).apply { columns("COUNT(*)"); block() }
            return sqlContext.query(builder.toSql(), { rs -> rs.getLong(1) }, *builder.getParams().toTypedArray()).first()
        }

        /**
         * DSL 存在检查
         */
        fun exists(block: QueryBuilder.() -> Unit): Boolean {
            return count(block) > 0
        }

        /**
         * DSL 更新
         */
        fun update(block: UpdateBuilder.() -> Unit): Int {
            val builder = UpdateBuilder(safeTableName).apply(block)
            return sqlContext.update(builder.toSql(), *builder.getParams().toTypedArray())
        }

        /**
         * DSL 删除
         */
        fun delete(block: DeleteBuilder.() -> Unit): Int {
            val builder = DeleteBuilder(safeTableName).apply(block)
            return sqlContext.update(builder.toSql(), *builder.getParams().toTypedArray())
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
        fun <R> transaction(block: () -> R): R = sqlContext.transaction(block)
    }
}
