# Ktor 后端学习路线

本文档用于约束“服务端 / Ktor 学习成员”的教学方式。当前后端先不急着服务 Cards 项目主线，目标是用 Kotlin + Ktor 建立后端基础认知。

说明：Kotlin 是编程语言，Ktor 是 Kotlin 生态里的后端框架。

## 1. 当前定位

当前阶段：

- 先学习后端基础，不追求立刻完成项目后端。
- 先本地运行，不需要公网服务器。
- 局域网访问可以用自己的电脑作为服务端主机。
- 数据库可以先部署在本机。
- Android、Godot、Web 管理后台暂时不依赖这个学习后端。

推荐学习形态：

```text
Android / Godot / 浏览器 / Apifox
        ↓
Ktor API: http://电脑局域网IP:8080
        ↓
PostgreSQL: 运行在本机或 Docker
```

不推荐：

```text
Android / Godot 直接连接 PostgreSQL
```

原因：客户端直接连接数据库会暴露账号、密码、SQL 和数据权限，真实项目中不这样设计。

## 2. 学习目标

短期目标：

- 理解什么是客户端、服务端、数据库。
- 理解 localhost、局域网 IP、端口、防火墙。
- 会启动一个 Ktor 服务。
- 会写 `/ping` 接口。
- 会接收请求参数并返回 JSON。
- 会用 PostgreSQL 建表和查询。
- 会让 Ktor 连接 PostgreSQL。
- 会写最小 CRUD。
- 会用 Apifox、浏览器或 PowerShell 验证接口。

长期目标：

- 理解后端 API 如何保护数据库。
- 理解业务逻辑为什么不应该全部写在客户端。
- 理解 Controller/Route、Service、Repository、DTO 的分层。
- 理解鉴权、权限、错误处理、日志、配置、部署的作用。
- 后续能判断什么时候该用 Ktor，什么时候用 Supabase 更划算。

## 3. 教学式任务模板

每次 Ktor 后端任务必须按这个结构输出：

```text
本次要解决的问题：

这次你需要理解：
- 

涉及的 Ktor/后端概念：
- 

设计方案：
- 路由：
- 数据结构：
- 数据库：
- 分层：

实现步骤：
1. 
2. 
3. 

验证方式：
- 浏览器：
- PowerShell：
- Apifox：
- 局域网设备：

常见坑：
- 

本次学完你应该掌握：
- 
```

如果本次只解释概念，也要保留“本次学完你应该掌握”。

## 4. 学习顺序

### 第 0 阶段：后端基本概念

要理解：

- 客户端、服务端、数据库分别负责什么。
- HTTP 请求和响应是什么。
- URL、Method、Header、Body、Status Code 是什么。
- 为什么 App 不应该直接连接数据库。
- 局域网为什么不需要公网服务器，但需要一台机器跑服务。

小任务：

- 画出 Android/Godot -> Ktor -> PostgreSQL 的调用链。
- 查出电脑局域网 IP。
- 理解 `localhost`、`127.0.0.1`、`10.0.2.2`、`192.168.x.x` 的区别。

### 第 1 阶段：Ktor 最小服务

要理解：

- Gradle 项目。
- Ktor Application。
- Routing。
- Netty 引擎。
- JSON 响应。

小任务：

- 启动 Ktor 服务。
- 实现 `GET /ping`。
- 返回：

```json
{"message":"pong"}
```

- 用浏览器和 PowerShell 验证。

### 第 2 阶段：请求参数和 JSON

要理解：

- Path parameter。
- Query parameter。
- Request body。
- DTO。
- kotlinx.serialization。

小任务：

- `GET /hello?name=Y`
- `GET /posts/{id}`
- `POST /posts`
- 返回结构化 JSON。

### 第 3 阶段：本地 PostgreSQL

要理解：

- 数据库、表、行、列。
- 主键、外键、唯一约束。
- 索引。
- SQL 基础：select、insert、update、delete。
- DBeaver 或命令行如何连接数据库。

小任务：

- 本地安装或 Docker 启动 PostgreSQL。
- 创建学习库 `cards_learning`。
- 创建 `posts` 表。
- 手写几条 SQL 查询。

### 第 4 阶段：Ktor 连接数据库

要理解：

- 数据库连接配置。
- 环境变量和配置文件。
- Repository 层。
- SQL 错误如何处理。

小任务：

- Ktor 读取数据库连接配置。
- 实现 `GET /posts`。
- 实现 `POST /posts`。
- 接口返回数据库里的真实数据。

### 第 5 阶段：分层和错误处理

要理解：

- Route 负责 HTTP。
- Service 负责业务规则。
- Repository 负责数据访问。
- DTO 和数据库模型不要随便混用。
- 404、400、500 的区别。

小任务：

- 拆分 posts route/service/repository。
- 对不存在的帖子返回 404。
- 对非法输入返回 400。
- 对未知异常返回统一错误格式。

### 第 6 阶段：基础鉴权

要理解：

- 登录态。
- 密码为什么不能明文存储。
- Session 和 JWT 的区别。
- 认证和授权的区别。

小任务：

- 先做学习版用户表。
- 实现注册、登录。
- 理解密码哈希。
- 理解后续如何保护发帖接口。

### 第 7 阶段：局域网访问

要理解：

- 服务绑定地址 `0.0.0.0` 和 `localhost` 的区别。
- Windows 防火墙。
- Android 模拟器访问宿主机用 `10.0.2.2`。
- 真机访问电脑用局域网 IP。

小任务：

- 让手机或另一台设备访问 `http://电脑局域网IP:8080/ping`。
- 记录遇到的防火墙、端口、网络问题。

## 5. 暂不做的内容

当前不急着做：

- 完整项目后端。
- 复杂权限系统。
- 微服务。
- Kubernetes。
- 消息队列。
- 高并发架构。
- 复杂 IM。
- 线上生产部署。

这些以后可以学，但现在先把最小后端链路跑通。

## 6. 阶段验收

完成本学习阶段时，至少应该能做到：

- 解释一次 HTTP 请求如何从客户端到 Ktor，再到数据库。
- 独立启动 Ktor 服务。
- 独立启动或连接本地 PostgreSQL。
- 写出 3 到 5 个简单接口。
- 用工具验证接口。
- 说清楚为什么客户端不能直接连数据库。
- 说清楚 Ktor 和 Supabase 的区别。
