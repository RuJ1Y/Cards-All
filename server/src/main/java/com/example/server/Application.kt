package com.example.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.routing.post
import java.util.concurrent.atomic.AtomicInteger

fun main(){

    //创建一个线程安全的整数，初始值是 0
    val totalClicks = AtomicInteger(0)

    //用 Netty 创建一个 Ktor 服务器，监听这台电脑的 8080 端口
    embeddedServer(
        factory = Netty,
        host = "0.0.0.0", //监听本机所有网络入口，方便局域网设备访问
        port = 8080
    ) {
        routing { //所有接口都写在 routing { } 里面
            get("/ping") {
                call.respondText("""{"message":"pong"}""")
            }
            get("/clicks"){
                call.respondText("""{"totalClicks":${totalClicks.get()}}""")
            }
            post("/clicks"){
                val newTotalClicks = totalClicks.incrementAndGet()//安全地+1，然后返回加完后的新数字
                call.respondText("""{"totalClicks":$newTotalClicks}""")
            }
        }
    }.start(wait = true)//启动后保持运行
}
