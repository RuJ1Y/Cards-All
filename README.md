# Cards Community

面向毕设和实习展示的论坛项目。当前主线是 Android 论坛客户端与 Kotlin/Ktor 后端。

## 模块

- `community/`：Android 论坛客户端。
- `server/`：Ktor 后端学习与接口实现。
- `docs/`：项目规则、架构、学习路线和决策记录。

## 当前目标

- 跑通帖子列表、详情、发布和评论主流程。
- 学会 HTTP、JSON、CRUD、PostgreSQL、鉴权和后端分层。
- 后续增加 Web 管理后台；iOS 端为低优先级扩展。

暂不做：复杂 IM、推荐算法、微服务和 Kubernetes。

## 技术路线

- Android：Kotlin、Jetpack、Ktor Client。
- 后端：Kotlin、Ktor、kotlinx.serialization、PostgreSQL。
- Web 管理后台：TypeScript 与成熟前端框架，后续启动。

## 运行后端

```powershell
.\gradlew.bat :server:run --console=plain
```

测试接口：`GET http://localhost:8080/ping`

## 构建 Android 论坛

```powershell
.\gradlew.bat :community:assembleDebug --console=plain
```

项目规则和当前方向以 `docs/PROJECT_CENTER.md` 与 `docs/TEAM_RULES.md` 为准。
