package com.example.cards.community.network
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

object ServerApi {
    //创建 Ktor 网络客户端，并启用 JSON 自动转换
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }
    //记录服务器的基础地址，const 表示它是编译时常量
    private const val BASE_URL = "http://172.20.10.4:8080"

    suspend fun ping(): String {
        val response = client.get("$BASE_URL/ping")
        val pingResponse = response.body<PingResponse>()
        return pingResponse.message
    }

    suspend fun getPosts(): List<PostResponse> {
        val response = client.get("$BASE_URL/posts")
        return response.body<List<PostResponse>>()
    }

}

// 和服务端一样的数据类
@Serializable
data class PingResponse(
    val message: String
)

@Serializable
data class PostResponse(
    val id: Int,
    val title: String,
    val content: String
)
