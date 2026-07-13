# 项目中枢

本文件是最高层事实来源。成员开工前只需先读：

1. `docs/PROJECT_CENTER.md`
2. `docs/TEAM_RULES.md`
3. 自己角色相关文档

若旧 README、历史交接或旧架构文档冲突，以本文档和用户最新指令为准。

## 1. 项目定位

Cards-All 是一个围绕原创卡牌/策略游戏的多端社区项目，同时服务毕设、实习简历和个人学习。

目标形态：

- Godot 游戏客户端。
- Android 论坛客户端。
- Web 管理后台。
- 后端学习主线：Kotlin + Ktor + PostgreSQL，本地学习优先，逐步补齐真实后端知识面。
- Supabase 暂时放弃主线，只保留为以后对照和备用资料。
- iOS 论坛端：低优先级扩展。

## 2. 阶段目标

### 暑假

- Godot 游戏做出可演示原型。
- Android 论坛跑通列表、详情、发布、评论等主流程。
- Web 管理后台做最小审核/用户/公告能力，至少完成一到两个。
- Ktor 后端学习跑通 `/ping`、JSON、PostgreSQL 连接和最小 CRUD。
- 沉淀 README、截图、演示和毕设材料。

暂缓：完整 IM、复杂联机、推荐算法、完整 iOS、线上生产部署。

### 一个学期

- 把暑假 MVP 打磨成完整版本。
- 补齐测试、文档、部署说明和演示材料。
- 后端学习达到能解释 API、数据库、鉴权、分层和局域网访问。
- 再决定 Ktor 是否升级为项目主后端，或是否需要引入其他方案辅助。

## 3. 成员分工

- 团队大脑 / 总控：维护方向、任务、优先级、验收和跨成员同步。
- 产品设计：用户流程、MVP 范围、验收标准、毕设叙事。
- 架构：模块边界、数据流、接口契约、ADR。
- 服务端 / Ktor 学习：用 Kotlin + Ktor 学后端，不以项目交付为第一目标。
- Android 前端：Android 论坛 UI、状态、Repository、接口/mock 对接。
- Godot 前端：游戏场景、交互、资源、玩法原型。
- Web 管理后台：审核、用户、公告、配置、统计等后台能力。
- iOS 论坛：主线稳定后再启动。
- 指导老师 / 毕设顾问：题目、论文结构、答辩表达。

## 4. 技术路线

- Android：Kotlin，现代 Android / Jetpack 生态。
- Godot：Godot 4.x。
- 后端学习：Kotlin + Ktor，PostgreSQL，Gradle，kotlinx.serialization。
- 后端路线原则：为了未来求职和 AI 时代的适应性，优先拓宽 HTTP、API、数据库、鉴权、部署、日志和分层架构等通用能力。
- Supabase：暂不作为当前学习或项目主线。
- Web 后台：TypeScript + 主流前端框架和成熟 UI 组件库。
- 数据库学习：客户端不直连数据库，必须经过 API。

技术原则：

- 用行业常用、稳定、可维护方案。
- 遇到问题先复现、定位、查官方文档、最小修复、验证。
- 不为省事绕开架构、权限、错误处理和长期维护。
- 需要降级或换技术，必须记录原因并等用户确认。

## 5. Git 与 GitHub

仓库策略：

- Godot 游戏端和 Android 论坛端优先独立 Git 仓库，不共用一个仓库。
- `D:\Cards-All` 作为本地总工作区和文档中枢。
- Ktor 后端学习先本地实验，稳定后再决定是否单独建仓或接入项目主流程。

GitHub 仓库名建议：

```text
cards-godot-game
cards-android-forum
```

GitHub 描述必须中英双语：

```text
Cards Godot game client / Cards 项目 Godot 游戏客户端
Android forum client for the Cards community / Cards 社区 Android 论坛客户端
```

分支、提交、合并规则见 `docs/TEAM_RULES.md`。

## 6. 当前优先级

1. 所有成员遵守独立分支工作流。
2. 精简文档，减少上下文负担。
3. Ktor 后端学习先跑通本地最小链路，并逐步进入 PostgreSQL、CRUD、分层、鉴权和局域网访问。
4. Godot 和 Android 论坛各自推进 MVP。
5. Web 管理后台后续跟进。
6. 每次任务结束必须交接。

## 7. 关键文档

- `docs/TEAM_RULES.md`：所有成员硬规则。
- `docs/KTOR_BACKEND_LEARNING.md`：Ktor 后端学习路线。
- `docs/ARCHITECTURE.md`：当前架构基线。
- `docs/adr/`：关键技术取舍记录。
- `docs/SUPABASE_LEARNING.md`：Supabase 备用学习资料。
- `docs/handoff/`：历史交接记录，可能过期，使用前要对照本文档。
