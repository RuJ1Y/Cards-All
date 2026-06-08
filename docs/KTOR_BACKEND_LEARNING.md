# Ktor 后端学习

目标：用 Kotlin + Ktor 学后端基础。当前先做知识积累，不急着接 Cards 项目。

Kotlin 是语言，Ktor 是后端框架。

## 1. 当前边界

- 本地学习优先，不需要公网服务器。
- 局域网访问时，你的电脑就是服务端主机。
- 数据库先用本机或 Docker PostgreSQL。
- Android / Godot 不直连数据库，必须经过 Ktor API。
- 学到一定阶段后，再决定是否接入项目。

基本链路：

```text
Android / Godot / 浏览器 / Apifox
-> Ktor API: http://电脑局域网IP:8080
-> PostgreSQL
```

## 2. 每次任务必须讲清

```text
本次要解决的问题：
这次要理解的概念：
涉及的路由/API：
涉及的数据库：
实现步骤：
验证方式：
常见坑：
本次学完应掌握：
```

不能只给代码。

## 3. 学习顺序

### 0. 后端常识

要懂：

- 客户端、服务端、数据库分别做什么。
- HTTP 请求/响应、URL、Method、Header、Body、Status Code。
- `localhost`、`127.0.0.1`、`10.0.2.2`、局域网 IP 的区别。
- 为什么客户端不能直接连数据库。

验收：能画出 `客户端 -> Ktor -> PostgreSQL`。

### 1. Ktor 最小服务

要做：

- 启动 Ktor。
- 写 `GET /ping`。
- 返回 `{"message":"pong"}`。
- 用浏览器、PowerShell 或 Apifox 验证。

要懂：Gradle、Application、Routing、Netty、JSON。

### 2. 请求参数和 JSON

要做：

- `GET /hello?name=Y`
- `GET /posts/{id}`
- `POST /posts`

要懂：query、path、body、DTO、kotlinx.serialization。

### 3. PostgreSQL 基础

要做：

- 本机或 Docker 启动 PostgreSQL。
- 创建 `cards_learning`。
- 建 `posts` 表。
- 手写 `select / insert / update / delete`。

要懂：表、行、列、主键、外键、唯一约束、索引。

### 4. Ktor 连接数据库

要做：

- Ktor 读取数据库配置。
- `GET /posts` 读真实数据。
- `POST /posts` 写真实数据。

要懂：连接配置、环境变量、Repository、SQL 错误。

### 5. 分层和错误处理

要做：

- 拆 `route / service / repository / dto`。
- 不存在返回 404。
- 非法输入返回 400。
- 未知异常统一返回错误格式。

要懂：HTTP 层、业务层、数据层各自职责。

### 6. 基础鉴权

要做：

- 学习版用户表。
- 注册、登录。
- 密码哈希。
- 保护发帖接口。

要懂：认证、授权、Session、JWT、密码不能明文存。

### 7. 局域网访问

要做：

- 服务绑定 `0.0.0.0`。
- 真机访问 `http://电脑局域网IP:8080/ping`。
- 处理 Windows 防火墙和端口问题。

要懂：`localhost` 只代表本机，局域网设备要访问电脑 IP。

## 4. 暂不做

- 完整项目后端。
- 微服务、Kubernetes、消息队列、高并发。
- 复杂 IM。
- 线上生产部署。

## 5. 阶段验收

完成后应能：

- 独立启动 Ktor 和 PostgreSQL。
- 写 3 到 5 个简单接口。
- 用工具验证接口。
- 解释一次请求如何到 API 再到数据库。
- 说清 Ktor 和 Supabase 的区别。
