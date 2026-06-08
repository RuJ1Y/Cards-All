# Supabase 备用资料

当前后端学习主线是 Kotlin + Ktor。本文件只保留 Supabase 备用方向，避免以后重新讨论时从零开始。

## 适合 Supabase 的场景

- 登录注册。
- PostgreSQL 数据表。
- RLS 权限。
- Storage 文件。
- Realtime 简单实时刷新。
- Edge Functions 处理少量服务端逻辑。

## 以后若启用，学习顺序

1. Auth：用户身份、anon key、service role key。
2. Database：表、主键、外键、索引、约束。
3. RLS：select / insert / update / delete 权限。
4. Storage：头像、帖子图、资源文件。
5. Realtime：评论、通知、状态刷新。
6. Edge Functions：不适合放客户端的逻辑。

## 教学要求

Supabase 成员不能只给 SQL。每次必须说明：

- 业务问题。
- 表结构。
- 权限策略。
- 客户端如何调用。
- 如何验证普通用户、作者、管理员、未登录用户。
- 本次应该理解什么。

## 当前状态

暂不作为主线。等 Ktor 学习和产品节奏稳定后，再由用户决定是否启用。
