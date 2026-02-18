package com.dakuo.rulib.common.database

import com.dakuo.rulib.common.getMainConfig
import taboolib.common.platform.function.getDataFolder
import taboolib.module.database.getHost
import java.io.File
import javax.sql.DataSource

object Database {

    val dataSource: DataSource by lazy {
        when (getDatabaseType()?.lowercase()) {
            "sqlite" -> getSQLiteHost().createDataSource()
            "mysql" -> getMysqlHost().createDataSource()
            else -> throw IllegalArgumentException("Invalid database type: ${getDatabaseType()}")
        }
    }

    private fun getSQLiteHost() = File(getDataFolder(), "data.db").getHost()

    private fun getMysqlHost() = getMainConfig().getHost("database.mysql")

    fun getDatabaseType() = getMainConfig().getString("database.type")

    /**
     * 执行更新操作
     */
    fun update(sql: String, vararg params: Any?) = SQLTemplate.update(sql, *params)

    /**
     * 执行查询操作
     */
    fun <T> query(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?) = 
        SQLTemplate.query(sql, mapper, *params)

    /**
     * 执行查询单个结果操作
     */
    fun <T> queryOne(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any?) =
        SQLTemplate.queryOne(sql, mapper, *params)

    /**
     * 执行批量更新操作
     */
    fun batch(sql: String, params: List<Array<Any?>>) = SQLTemplate.batch(sql, params)

    /**
     * 创建表对象
     */
    fun table(name: String, init: SQLTable.Builder.() -> Unit) = SQLTable.table(name, init)

    /**
     * 创建表
     */
    fun createTable(name: String, init: SQLTable.Builder.() -> Unit) {
        table(name, init).create()
    }

    /**
     * 插入数据
     */
    fun insert(table: String, data: Map<String, Any?>) {
        val columns = data.keys.joinToString(",")
        val placeholders = data.keys.joinToString(",") { "?" }
        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders)"
        update(sql, *data.values.toTypedArray())
    }

    /**
     * 更新数据
     */
    fun update(table: String, data: Map<String, Any?>, where: String, vararg params: Any?) {
        val setClause = data.keys.joinToString(",") { "$it=?" }
        val sql = "UPDATE $table SET $setClause WHERE $where"
        val allParams = data.values.toList() + params.toList()
        update(sql, *allParams.toTypedArray())
    }

    /**
     * 删除数据
     */
    fun delete(table: String, where: String, vararg params: Any?) {
        val sql = "DELETE FROM $table WHERE $where"
        update(sql, *params)
    }

    /**
     * 查询数据
     */
    fun <T> select(
        table: String,
        columns: String = "*",
        where: String? = null,
        mapper: (java.sql.ResultSet) -> T,
        vararg params: Any?
    ): List<T> {
        val whereClause = where?.let { "WHERE $it" } ?: ""
        val sql = "SELECT $columns FROM $table $whereClause"
        return query(sql, mapper, *params)
    }

    /**
     * 在一个连接中执行多个数据库操作
     */
    fun <R> workspace(block: SQLTemplate.SQLContext.() -> R): R {
        return SQLTemplate.workspace(block)
    }
}



