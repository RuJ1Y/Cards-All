package com.example.server

import java.sql.Connection
import java.sql.DriverManager

//根据配置建立数据库连接
fun openDatabaseConnection(config: DatabaseConfig): Connection {
    return DriverManager.getConnection(
        config.url,
        config.username,
        config.password
    )
}
