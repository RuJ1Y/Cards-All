package com.example.server

import io.ktor.http.HttpStatusCode
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
import io.ktor.server.request.receive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.server.routing.put

fun main(){
    //配置数据库参数
    val config = loadDatabaseConfig()
    val repository = PostRepository(config)

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

            //Ktor 收到请求后，会提供一个 ApplicationCall 对象，在路由里用 call 访问它
            get("/ping") {
                call.respond(PingResponse("pong"))//自动转为json
            }

            get("/clicks"){
                call.respond(ClicksResponse(totalClicks = totalClicks.get()))
            }

            get("/hello") {
                val name = call.request.queryParameters["name"]?:"陌生人"//创建变量记录客户端传参
                call.respond(mapOf("message" to "你好，$name"))
            }

            get("/posts/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "帖子 ID 必须是数字")
                    )
                    return@get
                }
                val post = withContext(Dispatchers.IO){
                    repository.findById(id)
                }
                if (post == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("message" to "帖子不存在")
                    )
                    return@get
                }
                call.respond(post)
            }

            get("/posts") {
                val databasePosts = withContext(Dispatchers.IO){
                    repository.findAll()
                }
                call.respond(databasePosts)
            }

            post("/clicks"){
                val newTotalClicks = totalClicks.incrementAndGet()//安全地+1，然后返回加完后的新数字
                call.respond(ClicksResponse(totalClicks = newTotalClicks))
            }

            post("/posts"){
                val request = call.receive<CreatePostRequest>()//保存客户端上传的帖子数据
                if (request.title.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "标题不能为空")
                    )
                    return@post
                }
                if (request.content.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "帖子内容不能为空")
                    )
                    return@post
                }
                val post = withContext(Dispatchers.IO){
                    repository.create(
                        title = request.title,
                        content = request.content
                    )
                }
                call.respond(HttpStatusCode.Created, post)
            }

            put("/posts/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "帖子 ID 必须是数字")
                    )
                    return@put
                }

                val request = call.receive<UpdatePostRequest>()
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

//用户帖子数据
@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String
)

//用户修改帖子所需的数据
@Serializable
data class UpdatePostRequest(
    val title: String,
    val content: String
)

//让服务器返回一个由它生成的帖子ID
@Serializable
data class PostResponse(
    val id: Int,
    val title: String,
    val content: String
)
