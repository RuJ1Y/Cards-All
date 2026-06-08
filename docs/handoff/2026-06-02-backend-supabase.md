# 2026-06-02 服务端 / Supabase 成员日志

创建时间：2026-06-02 12:17:43 +08:00

## 角色确认

本次角色：服务端 / Supabase 成员。

已确认职责：
- 当前后端主线只使用 Supabase，不把 `server/` 里的自建后端骨架作为主线继续扩张。
- 负责 Supabase schema、RLS、Storage、Realtime、Edge Functions、接口说明和权限说明。
- 后端任务默认采用教学模式：先解释业务问题，再解释 Supabase 方案、操作步骤和验证方式。
- 表结构必须考虑权限、索引和未来扩展；RLS 默认开启，除非有明确理由并记录影响。
- Edge Function 或 RPC 必须写清请求、响应、错误说明和验证方式。

## 已阅读上下文

- `docs/PROJECT_CENTER.md`
- `docs/TEAM_RULES.md`
- `docs/SUPABASE_LEARNING.md`
- `docs/ARCHITECTURE.md`
- `docs/supabase_schema.sql`
- `docs/handoff/2026-06-02-godot-ui-computer-use.md`

## 当前后端事实

- `D:\Cards-All` 根目录不是 Git 仓库；`godot/`、`app/`、`server/` 当前也未检测到 Git 仓库。
- `server/` 存在 Ktor 骨架，但项目中枢和团队规则已经明确：暑假和学期内后端主线采用 Supabase，不安排自建独立后端。
- 当前 Supabase schema 记录在 `docs/supabase_schema.sql`。
- 现有 schema 包含：
  - `player_account`
  - `player_profile`
  - `player_characters`
  - `game_room_state`
  - `game_result`
- 当前 schema 仍保留自定义账号表和密码 hash，并注明暂未开启 RLS；这适合早期跑通玩法，不适合作为长期安全方案。
- 论坛 MVP 所需的 `profiles`、`posts`、`comments`、`post_likes`、`post_favorites`、`announcements`、`moderation_actions` 尚未完整落地。

## 本次目标

确认服务端 / Supabase 成员职责，并创建本日志文档，作为后续后端任务的固定交接入口。

## 已完成

- 阅读团队规则和项目中枢，确认后端成员职责。
- 阅读 Supabase 学习路线，确认后端任务输出需要保留教学结构。
- 快速检查当前 schema 与架构文档中的后端事实。
- 创建本日志文档。

## 改动文件

- `docs/handoff/2026-06-02-backend-supabase.md`

## 重要决策

- 后端工作继续以 Supabase 为唯一主线。
- 暂不修改 `server/`，不扩张 Ktor 自建后端。
- 后续 schema、权限和接口变更应优先同步到：
  - `docs/DATABASE.md`
  - `docs/API.md`
  - `docs/SUPABASE_LEARNING.md`
  - 必要时补充 `docs/adr/`

## 本次涉及的后端概念

- Auth：当前仍需从自定义账号表迁移或明确取舍到 Supabase Auth。
- Table：现有游戏相关表已记录，论坛 MVP 表尚未补齐。
- Policy / RLS：当前未开启，后续必须优先设计。
- Storage：头像和帖子图片后续应进入 Storage，不应长期使用 base64 字段。
- Realtime：可用于评论刷新、通知或房间状态变化订阅。
- Function：普通 CRUD 不强行使用 Edge Function；敏感逻辑、管理员批量操作、复杂通知聚合再考虑。
- Index：后续帖子列表、评论分页、房间状态查询都需要补索引。
- Migration：任何破坏性 schema 变更都要先说明迁移影响。

## 验证结果

- 已确认规则文档存在并完成阅读：`docs/TEAM_RULES.md`。
- 已确认项目中枢存在并完成阅读：`docs/PROJECT_CENTER.md`。
- 已确认根目录与 `godot/`、`app/`、`server/` 当前未检测到 Git 仓库。
- 本次无代码改动，未运行编译或测试。

## 未完成 / 遗留问题

- 尚未创建 `docs/DATABASE.md` 和 `docs/API.md`。
- 尚未为 Supabase Auth、profiles、论坛帖子与评论设计正式 schema。
- 尚未为现有游戏相关表补 RLS 策略。
- 尚未写 ADR 处理 `server/` Ktor 骨架与 Supabase 主线之间的历史路线冲突。
- 部分历史文档在 PowerShell 中显示乱码，后续整理文档时需要注意编码。

## 需要同步给其他成员

- 后端成员已确认岗位：服务端 / Supabase。
- 后端主线不要切回 Ktor 或其他自建服务，除非团队大脑或用户明确重评估并写入 ADR。
- Android、Godot、Web 管理后台后续对接的数据契约，应先沉淀到数据库和 API 文档，不要各端各自猜字段。

## 下一步建议

- 优先补 `docs/DATABASE.md`，把当前 schema、风险、RLS 演进计划写清楚。
- 再补 `docs/API.md`，记录 Android / Godot / Web 管理后台共同使用的 Supabase 表、字段和调用方式。
- 为论坛 MVP 设计最小 Supabase 数据模型：`profiles`、`posts`、`comments`、`post_likes`、`post_favorites`。
- 评估是否立即写 ADR：确认 `server/` 暂作为历史骨架，不作为当前后端主线。

## 本次学完你应该掌握

- 当前项目后端不是传统自建服务主线，而是 Supabase 主线。
- Supabase 后端成员不只写 SQL，还要说明业务问题、权限设计、客户端调用、验证方式和常见坑。
- RLS、Auth、表结构、索引、Storage、Realtime、Edge Function 都属于后端职责范围，但要按 MVP 优先级逐步落地。
