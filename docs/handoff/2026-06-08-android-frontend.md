# 2026-06-08 Android 前端成员日志

创建时间：2026-06-08 15:06:04 +08:00

## 角色确认

本次角色：Android 前端成员。

已确认职责：
- 负责 Android 论坛端或综合 App 的 UI、页面流程、ViewModel、Repository、状态管理和错误反馈。
- UI 层不直接承载复杂业务逻辑；页面只负责展示、导航和触发用户动作。
- 网络、Supabase、后续 API 调用应封装在 Repository 或数据层。
- 页面状态必须覆盖加载、空状态、错误、成功。
- 关键用户路径必须能自测，并在交接中写清验证方式和结果。
- 不自行切换后端路线；Android 对接的数据契约应优先同步到 `docs/API.md`、`docs/DATABASE.md` 或相关交接文档。

## 已阅读上下文

- `README.md`
- `docs/PROJECT_CENTER.md`
- `docs/TEAM_RULES.md`
- `docs/ARCHITECTURE.md`
- `docs/SUPABASE_LEARNING.md`
- `docs/KTOR_BACKEND_LEARNING.md`
- `docs/supabase_schema.sql`
- `docs/handoff/2026-06-02-backend-supabase.md`
- `docs/handoff/2026-06-02-godot-ui-computer-use.md`

## 当前 Android 事实

- Android 端位于 `app/`，当前不是独立 Git 仓库；`D:\Cards-All` 根目录也不是 Git 仓库。
- `app/build.gradle.kts` 使用 Kotlin Android、Kotlin serialization、ViewBinding、DataBinding、AppCompat、Material、ConstraintLayout、Supabase PostgREST、Supabase Realtime、Ktor Android client。
- 应用包名为 `com.example.cards`，`minSdk = 24`，`targetSdk = 36`，`compileSdk = 36`。
- 当前 Android 页面主要是横屏 Activity：启动、登录、大厅、用户管理、房间搜索、角色选择、抽取、角色详情、匹配加载、战斗、结算。
- 当前已有数据层和领域层雏形：
  - `auth/AuthRepository.kt`
  - `data/SupabaseNet.kt`
  - `game/engine/*`
  - `game/repo/*`
  - `game/sync/*`
  - `ui/viewmodel/GameViewModel.kt`
- 当前 Supabase schema 包含 `player_account`、`player_profile`、`player_characters`、`game_room_state`、`game_result`。
- 当前 schema 仍是自定义账号表和 anon key 直连思路，RLS 未开启；这适合早期跑通，不适合长期安全方案。
- 代码中已经存在部分 Activity 直接调用 Supabase 的情况，后续 Android 前端任务应逐步向 Repository / ViewModel 边界收敛，避免继续扩散。

## 路线注意

- `README.md` 保留了较旧的 Ktor 自建后端路线；当前实际工作以 `docs/PROJECT_CENTER.md` 和 `docs/ARCHITECTURE.md` 为准。
- Ktor 当前是后端学习支线，不应由 Android 前端线程自行接入为主线后端。
- 暑假 MVP 中 Android 论坛端可以先使用 mock 数据、Supabase 或团队确认的临时接口，但字段和权限假设必须记录。
- 如果涉及论坛 MVP，新表至少需要和后端 / Supabase 成员对齐：`profiles`、`posts`、`comments`、`post_likes`、`post_favorites`。

## 本次目标

确认本对话职责为 Android 前端成员，阅读工作区文档，并创建本日志文件作为后续 Android 前端任务的固定交接入口。

## 已完成

- 阅读项目中枢和团队规则，确认 Android 前端职责边界。
- 阅读架构文档，确认 Android 依赖方向：UI -> ViewModel / 状态 -> Repository / Service -> Supabase / API。
- 阅读 Supabase 与 Ktor 学习文档，确认 Android 不自行决定后端路线。
- 阅读当前 Supabase schema，确认 Android 已有账号、资料、角色、房间和战斗结果相关表依赖。
- 快速检查 Android 工程结构、Manifest、Gradle 配置和 Supabase 入口。
- 创建本 Android 前端成员日志。

## 改动文件

- `docs/handoff/2026-06-08-android-frontend.md`

## 重要决策

- 本对话后续只承担 Android 前端范围，除非用户明确调整角色。
- Android 任务默认遵守现有 XML Activity + Kotlin 结构，不贸然改成全新架构或重写 UI 技术栈。
- 后续涉及 Supabase 调用时，优先收敛到 Repository / 数据层，避免 Activity 继续直接散落查询逻辑。
- 涉及论坛、账号、评论、审核等长期能力时，不只做前端按钮隐藏，要同步考虑后端权限和数据契约。

## 验证结果

- 已确认 `D:\Cards-All` 不是 Git 仓库。
- 已确认 `app/` 不是 Git 仓库。
- 已确认 Android 工程文件存在并可读。
- 本次只创建文档日志，无 Android 代码改动，未运行 Gradle 编译或设备测试。

## 未完成 / 遗留问题

- 尚未全面审计 Android 页面状态覆盖情况。
- 尚未运行 `:app:assembleDebug` 或 Android 仪器测试。
- 尚未整理论坛 MVP 的 Android 页面清单、数据模型和接口契约。
- 尚未补齐 `docs/API.md`、`docs/DATABASE.md`、`docs/PRODUCT.md`、`docs/ROADMAP.md`。
- 现有 Android 中部分 Supabase 调用仍在 Activity 或同步类中，后续需要按任务逐步收敛。
- 当前 anon key 和 Supabase URL 直接写在 Android 代码中，后续安全方案应由后端 / 架构成员统一评估。

## 需要同步给其他成员

- Android 前端成员已确认岗位。
- Android 后续不要自行扩张 Ktor 学习支线，也不要绕过 Supabase / API 契约直接猜字段。
- Supabase / 架构成员若补齐 `docs/API.md` 和 `docs/DATABASE.md`，Android 需要及时按契约调整 Repository 和页面状态。
- 如果论坛 MVP 启动，产品或团队大脑应先确认最小页面流：登录、帖子列表、帖子详情、发帖、评论、个人资料。

## 下一步建议

- 先梳理 Android 当前用户路径：Splash -> Login -> Lobby -> Room / Character / Game / Result。
- 再梳理论坛 MVP 缺口：帖子列表、详情、发布、评论、个人资料。
- 为 Android 前端补一个小型页面状态清单，标出每页的 loading / empty / error / success 是否齐全。
- 对直接散落在 Activity 中的 Supabase 调用做分批收敛，优先处理用户路径最核心、最容易出错的页面。
- 等后端 / Supabase 成员固定论坛 schema 后，再实现 Android 论坛 Repository 和页面。
