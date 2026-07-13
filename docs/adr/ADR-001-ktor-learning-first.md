# ADR-001：暂停 Supabase 主线，优先 Ktor 后端学习

日期：2026-07-13
状态：已接受

## 背景

用户最新目标是：未来找工作时，受 AI 影响，需要尽可能拓宽知识面，而不是只依赖低代码或高度托管方案。Supabase 能快速做出功能，但会隐藏一部分后端基础细节；Kotlin + Ktor + PostgreSQL 更适合补 HTTP、API、数据库、分层、鉴权、日志、局域网访问和部署常识。

## 决策

- Supabase 暂时放弃主线，只保留为备用学习资料和未来对照方案。
- 后端学习主线改为 Kotlin + Ktor + PostgreSQL。
- 当前 Ktor 先服务学习，不立即承诺承担完整 Cards 项目后端。
- Android、Godot、Web 后台后续都按“客户端访问 API，不直连数据库”的边界设计。
- Ktor 学到 PostgreSQL、CRUD、分层、错误处理、鉴权和局域网访问后，再评估是否接入项目主流程。

## 影响

- `server` 模块是独立运行的 JVM 服务，不打包进 Android App。
- 真机或其他设备访问时，应访问运行 Ktor 的电脑或服务器地址，例如 `http://电脑局域网IP:8080`。
- 项目短期交付速度可能慢于 Supabase，但后端知识覆盖更完整。
- 旧 Supabase 代码、文档和 schema 不能再作为当前主线依据，使用前必须对照 `PROJECT_CENTER.md`。

## 替代方案

- 继续 Supabase 主线：交付更快，但后端底层学习较少。
- Ktor 立即接管完整项目后端：学习密度高，但当前容易拖慢 Android、Godot 和后台 MVP。
- 混合方案：后续可评估，但现阶段会增加认知负担。

## 后续动作

- 继续推进 `docs/KTOR_BACKEND_LEARNING.md` 中的学习顺序。
- 下一阶段重点：Query、Path、JSON Body、PostgreSQL、CRUD、route/service/repository 分层。
- 等 Ktor 基础链路稳定后，再写 ADR 决定是否把 Ktor 升级为 Cards 项目主后端。
