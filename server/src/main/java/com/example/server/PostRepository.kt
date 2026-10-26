package com.example.server

//负责帖子的数据库读写
class PostRepository ( private val config: DatabaseConfig ){

    //查询所有帖子
    fun findAll(): List<PostResponse> {
        val posts = mutableListOf<PostResponse>()

        //建立数据库连接，use 方法能在运行后自动关闭
        openDatabaseConnection(config).use { connection ->
            val sql = "SELECT id, title, content FROM public.posts ORDER BY id"
            //准备执行 SQL 语句
            connection.prepareStatement(sql).use { statement ->
                //记录查询结果
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val post = PostResponse(
                            id = result.getInt("id"),
                            title = result.getString("title"),
                            content = result.getString("content")
                        )
                        posts.add(post)
                    }
                }
            }
        }

        return posts
    }

}
