package com.example.server

data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String
)

//从环境变量读取数据库连接配置
fun loadDatabaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = System.getenv("DB_URL")
            ?: "jdbc:postgresql://127.0.0.1:5432/cards_community_learning",
        username = System.getenv("DB_USER") ?: "cards_user",
        password = System.getenv("DB_PASSWORD")
            ?: error("请设置 DB_PASSWORD 环境变量")
    )
}
