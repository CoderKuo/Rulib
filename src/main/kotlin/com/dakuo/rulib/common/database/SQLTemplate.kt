package com.dakuo.rulib.common.database

private val IDENTIFIER_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

internal fun escapeIdentifier(name: String): String {
    require(name.matches(IDENTIFIER_PATTERN)) { "Invalid SQL identifier: $name" }
    return when (Database.getDatabaseType()?.lowercase()) {
        "sqlite", "postgresql" -> "\"$name\""
        else -> "`$name`"
    }
}

object SQLTemplate {

    private val dataSource: javax.sql.DataSource by lazy {
        Database.dataSource
    }

    /**
     * 执行更新操作
     */
    fun update(sql: String, vararg params: Any?): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                return stmt.executeUpdate()
            }
        }
    }

    /**
     * 执行查询操作
     */
    fun <T> query(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?): List<T> {
        val result = mutableListOf<T>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(mapper(rs))
                    }
                }
            }
        }
        return result
    }

    /**
     * 执行查询单个结果操作
     */
    fun <T> queryOne(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?): T? {
        return query(sql, mapper, *params).firstOrNull()
    }

    /**
     * 执行批量更新操作（自动包裹事务）
     */
    fun batch(sql: String, params: List<Array<Any?>>): IntArray {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    for (param in params) {
                        param.forEachIndexed { index, value ->
                            stmt.setObject(index + 1, value)
                        }
                        stmt.addBatch()
                    }
                    val result = stmt.executeBatch()
                    conn.commit()
                    return result
                }
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (re: Exception) {
                    e.addSuppressed(re)
                }
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * 在一个连接中执行多个数据库操作
     */
    fun <R> workspace(block: SQLContext.() -> R): R {
        dataSource.connection.use { conn ->
            val context = SQLContext(conn)
            return context.block()
        }
    }

    class SQLContext(private val connection: java.sql.Connection) {

        /**
         * 执行更新操作
         */
        fun update(sql: String, vararg params: Any?): Int {
            connection.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                return stmt.executeUpdate()
            }
        }

        /**
         * 执行查询操作
         */
        fun <T> query(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?): List<T> {
            val result = mutableListOf<T>()
            connection.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(mapper(rs))
                    }
                }
            }
            return result
        }

        /**
         * 执行查询单个结果操作
         */
        fun <T> queryOne(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?): T? {
            return query(sql, mapper, *params).firstOrNull()
        }

        /**
         * 执行批量更新操作（自动包裹事务，若已在事务中则复用当前事务）
         */
        fun batch(sql: String, params: List<Array<Any?>>): IntArray {
            val wasInTransaction = !connection.autoCommit
            if (!wasInTransaction) {
                connection.autoCommit = false
            }
            try {
                connection.prepareStatement(sql).use { stmt ->
                    for (param in params) {
                        param.forEachIndexed { index, value ->
                            stmt.setObject(index + 1, value)
                        }
                        stmt.addBatch()
                    }
                    val result = stmt.executeBatch()
                    if (!wasInTransaction) {
                        connection.commit()
                    }
                    return result
                }
            } catch (e: Exception) {
                if (!wasInTransaction) {
                    try {
                        connection.rollback()
                    } catch (re: Exception) {
                        e.addSuppressed(re)
                    }
                }
                throw e
            } finally {
                if (!wasInTransaction) {
                    connection.autoCommit = true
                }
            }
        }

        /**
         * 开启事务
         */
        fun beginTransaction() {
            connection.autoCommit = false
        }

        /**
         * 提交事务
         */
        fun commit() {
            connection.commit()
            connection.autoCommit = true
        }

        /**
         * 回滚事务
         */
        fun rollback() {
            connection.rollback()
            connection.autoCommit = true
        }

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
                try {
                    rollback()
                } catch (re: Exception) {
                    e.addSuppressed(re)
                }
                throw e
            }
        }
    }

}
