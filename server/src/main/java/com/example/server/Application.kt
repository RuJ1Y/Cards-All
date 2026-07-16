package com.example.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.call
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.routing.post
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

fun main(){

    //创建一个线程安全的整数，初始值是 0
    val totalClicks = AtomicInteger(0)

    //用 Netty 创建一个 Ktor 服务器，监听这台电脑的 8080 端口
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0", //监听本机所有网络入口，方便局域网设备访问
        port = 8080
    ) {
        install(ContentNegotiation) {//给Ktor服务器安装“自动内容转换能力”，并指定使用JSON。
            json()
        }
        routing { //所有接口都写在 routing { } 里面
            get("/ping") {
//                call.respondText("""{"message":"pong"}""")//手搓json
                call.respond(PingResponse("pong"))//自动转为json
            }
            get("/clicks"){
                call.respond(ClicksResponse(totalClicks = totalClicks.get()))
            }
            get("/hello") {
                val name = call.request.queryParameters["name"]?:"陌生人"//创建变量记录客户端传参
                call.respond(mapOf("message" to "你好，$name"))
            }
            get("/post/{id}") {
                val id = call.parameters["id"]?:"未知"
                call.respond(mapOf("postId" to id))
            }
            post("/clicks"){
                val newTotalClicks = totalClicks.incrementAndGet()//安全地+1，然后返回加完后的新数字
                call.respond(ClicksResponse(totalClicks = newTotalClicks))
            }
        }
    }.start(wait = true)//启动后保持运行
}
//自定义数据类，方便后续自动转为标准json
@Serializable//标记这个类可以被转换成 JSON，也可以从 JSON 转回来
data class PingResponse(
    val message: String
)
//点击数
@Serializable
data class ClicksResponse(
    val totalClicks: Int
)
