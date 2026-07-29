package com.example.cards.community.network
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

object ServerApi {
    //创建 Ktor 网络客户端
    private val client = HttpClient(Android)
    //记录服务器的基础地址，const 表示它是编译时常量
    private const val BASE_URL = "http://192.168.1.5:8080"

    suspend fun ping(): String {
        val response = client.get("$BASE_URL/ping")
        return response.bodyAsText()
    }
}
