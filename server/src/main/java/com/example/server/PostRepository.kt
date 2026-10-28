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
    //根据 ID 查询帖子
    fun findById(id: Int): PostResponse? {
        val sql = "SELECT id, title, content FROM public.posts WHERE id = ?"

        openDatabaseConnection(config).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1,id)
                statement.executeQuery().use { result ->
                    if (result.next()){
                        return PostResponse(
                            id = result.getInt("id"),
                            title = result.getString("title"),
                            content = result.getString("content")
                        )
                    }
                }
            }
        }
        return null
    }
    //新建帖子
    fun create(title: String, content: String): PostResponse{
        val sql = """
            INSERT INTO public.posts (title, content)
            VALUES (?, ?)
            RETURNING id, title, content
        """.trimIndent()
        openDatabaseConnection(config).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1,title)
                statement.setString(2,content)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        error("创建帖子后未返回数据")
                    }
                    return PostResponse(
                        id = result.getInt("id"),
                        title = result.getString("title"),
                        content = result.getString("content")
                    )
                }
            }
        }

    }
    //根据 ID 修改帖子的标题和内容
    fun update(id: Int, title: String, content: String): PostResponse?{
        val sql = """
            UPDATE public.posts
            SET title = ?, content = ?
            WHERE id = ?
            RETURNING id, title, content
        """.trimIndent()

        openDatabaseConnection(config).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1,title)
                statement.setString(2,content)
                statement.setInt(3,id)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        return null
                    }
                    return PostResponse(
                        id = result.getInt("id"),
                        title = result.getString("title"),
                        content = result.getString("content")
                    )
                }
            }
        }

    }

}
