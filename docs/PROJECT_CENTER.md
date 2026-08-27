# 项目中枢

本文件是最高层事实来源。成员开工前先读本文和 `docs/TEAM_RULES.md`。

## 1. 项目定位

Cards Community 是面向毕设、实习和长期维护的论坛项目。

目标形态：

- Android 论坛客户端。
- Kotlin + Ktor 后端。
- PostgreSQL 数据库。
- Web 管理后台。
- iOS 论坛端：低优先级扩展。

## 2. 阶段目标

暑假：完成帖子列表、详情、发布、评论等主流程，并跑通 Ktor、JSON 和最小 CRUD。

一个学期：补齐账号、鉴权、测试、部署说明、管理后台和毕设材料，形成可演示版本。

暂缓：完整 IM、推荐算法、微服务、Kubernetes 和复杂线上部署。

## 3. 成员分工

- 团队大脑：方向、优先级、验收和跨成员同步。
- 产品设计：用户流程、MVP、验收标准和毕设叙事。
- 架构：模块边界、数据流、接口契约和 ADR。
- Ktor 后端学习：HTTP、API、数据库、鉴权、分层和部署基础。
- Android 前端：论坛 UI、状态、Repository 和 API 对接。
- Web 管理后台：审核、用户、公告和统计。
- iOS 论坛：主线稳定后再启动。
- 毕设顾问：题目、论文结构和答辩表达。

## 4. 技术路线

- Android：Kotlin、Jetpack、Ktor Client。
- 后端：Kotlin、Ktor、PostgreSQL、Gradle、kotlinx.serialization。
- Web：TypeScript、主流前端框架和成熟组件库。
- 数据边界：客户端只访问 API，不直连数据库。

要求使用行业常用、稳定、可维护的方案。遇到问题先复现、定位、修复和验证；换方案必须说明原因并等待用户确认。

## 5. 仓库

`D:\Cards-All` 是一个 Gradle 多模块仓库：

- `community`：Android 论坛。
- `server`：Ktor 后端。
- `docs`：项目约束和学习资料。

分支、提交和合并规则见 `docs/TEAM_RULES.md`。

## 6. 当前优先级

1. 完成 Android 论坛核心流程。
2. 推进 Ktor、PostgreSQL、CRUD、分层和鉴权学习。
3. 稳定 API 契约和错误格式。
4. 启动最小 Web 管理后台。
5. 补齐测试、README、部署说明和毕设材料。

## 7. 关键文档

- `docs/TEAM_RULES.md`：团队硬规则。
- `docs/KTOR_BACKEND_LEARNING.md`：后端学习路线。
- `docs/ARCHITECTURE.md`：架构基线。
- `docs/adr/`：关键技术决策。
- `docs/SUPABASE_LEARNING.md`：备用资料，不是当前主线。
