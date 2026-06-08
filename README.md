# Cards-All

这是 Cards 项目的总整理目录，用来承载后续三个方向：

- `app/`：现有 Android 版本 Cards，作为旧版参考和素材来源。
- `server/`：Kotlin Ktor 后端，从最小接口开始逐步扩展。
- 后续会加入 Godot 客户端，用来重做 Cards 的游戏端。

## 项目目标

当前目标不是单纯“把游戏做出来”，而是做一个能用于面试展示的完整项目。

核心方向：

```text
Godot 版 Cards 游戏客户端
+ Kotlin Ktor 自建游戏后端
+ 后续面向游戏内容的社区系统
+ 简单管理后台
+ 云端部署说明
```

定位：

- 游戏本体主要给朋友玩。
- 社区系统是后续面试重点。
- 后端学习从 Cards 游戏后端开始，不直接上复杂社区。
- Supabase 不再作为主后端方向，后续主线改为自建 Ktor 后端。

## 学习背景

当前基础：

- 已系统学过 Android 前端。
- 已有 Kotlin/Android 项目经验。
- 后端、数据库、部署经验较少。

因此学习策略是：

```text
不先学一堆概念
先用 Cards 项目做一个最小可运行后端
再逐步补数据库、登录、房间、战斗结算、社区功能
```

## 技术路线

后端主线：

```text
Kotlin
Ktor Server
kotlinx.serialization
PostgreSQL
Exposed
JWT
Docker Compose
```

游戏客户端主线：

```text
Godot 4.x
GDScript
HTTPRequest
JSON
```

后续社区主线：

```text
用户系统
帖子
评论
点赞
收藏
关注
通知
内容审核
管理后台
```

暂时不学：

```text
微服务
Kubernetes
消息队列
高并发架构
复杂推荐系统
商业级反作弊
```

## 总体计划

### 第一阶段：Ktor 入门与 Cards 后端骨架

目标：

- 跑通 Ktor 服务。
- 返回角色和卡牌数据。
- 创建房间。
- 拉取房间状态。
- 提交一次玩家行动。
- 后端调用游戏规则生成新状态。

接口计划：

```text
GET  /ping
GET  /characters
GET  /cards
POST /rooms
GET  /rooms/{roomId}
POST /rooms/{roomId}/actions
```

### 第二阶段：迁移游戏核心逻辑

从 Android 项目迁移这些内容到后端：

```text
GameModels.kt
GameAction.kt
GameEngine.kt
DefaultCharacterRepository.kt
DefaultCardRepository.kt
```

原则：

```text
Godot 客户端只负责显示和提交操作
Ktor 后端负责校验和战斗结算
```

### 第三阶段：Godot 版 Cards

目标：

- 登录或临时玩家身份。
- 选择角色。
- 创建和加入房间。
- 显示双方角色、手牌、cost、回合。
- 点击技能或卡牌后提交 action。
- 展示后端返回的新战斗状态。

第一版先用 HTTP 轮询，不急着做 WebSocket。

### 第四阶段：数据库与账号系统

加入：

```text
PostgreSQL
玩家账号
玩家资料
角色拥有关系
战斗记录
JWT 登录
```

### 第五阶段：社区与管理后台

社区是面试重点，后续做：

```text
帖子发布
评论
点赞
收藏
关注
通知
角色/卡牌资料库
攻略内容
战斗记录分享
```

管理后台做：

```text
用户管理
帖子审核
公告发布
封禁
角色/卡牌配置
战斗记录查看
```

## 当前进度

已经完成：

- 新增 `server` 模块。
- 接入 Ktor Server。
- 接入 JSON 序列化。
- 使用 Netty 启动 HTTP 服务。
- 完成 `/ping` 接口。
- 验证 `/ping` 可以返回 `pong`。

当前后端接口：

```text
GET http://localhost:8080/ping
```

返回：

```json
{"message":"pong"}
```

## 用什么软件打开

推荐：

```text
IntelliJ IDEA：打开项目、写 Kotlin/Ktor 后端
Apifox：测试后端接口
浏览器：简单访问 /ping
Godot：后续写游戏客户端
DBeaver：后续查看数据库
```

打开项目时，直接打开：

```text
D:\Cards-All
```

不要只打开 `server` 文件夹。

## 重要文件

后端入口：

```text
D:\Cards-All\server\src\main\kotlin\com\example\cards\server\Application.kt
```

后端 Gradle 配置：

```text
D:\Cards-All\server\build.gradle.kts
```

总项目模块配置：

```text
D:\Cards-All\settings.gradle.kts
```

## 怎么运行后端

在 IntelliJ IDEA 底部 Terminal 中确认当前目录是：

```powershell
D:\Cards-All
```

然后运行：

```powershell
.\gradlew.bat :server:run --console=plain
```

看到服务启动后，不要关闭这个 Terminal。

停止后端：

```text
Ctrl + C
```

## 怎么测试

方式一：浏览器打开：

```text
http://localhost:8080/ping
```

方式二：Apifox 新建请求：

```text
Method: GET
URL: http://localhost:8080/ping
```

方式三：PowerShell：

```powershell
Invoke-RestMethod http://localhost:8080/ping
```

成功结果应该是：

```json
{"message":"pong"}
```

## 下一步

下一次继续做：

```text
GET /characters
GET /cards
```

也就是先让后端把 Cards 的角色和卡牌数据返回出来。
