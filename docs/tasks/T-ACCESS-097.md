---
doc_type: task
id: T-ACCESS-097
title: OAuth2 客户端租户内唯一改造
status: proposed
plan: —
domain: access-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-api-contract.md
  - docs/design/tenant-lifecycle.md#tenant-login
depends_on: []
acceptance:
  - uk_oauth2_client_id 改为 (tenant_id, client_id) 复合唯一，权威 DDL 同步更新
  - authorize/token/refresh 与 JWT 校验链的客户端解析全部带租户，跨租户同名客户端互不可见、不误绑
  - 重复注册仅对租户内重复提示"已存在"，不再泄露他租户占用情况
  - 契约总册 OAuth2 章、e2e 与相关 PgIT 适配，全量回归通过
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-08
---

## 背景

[Q-069](../../pending-problems.md#q-069)：`sys_oauth2_client.client_id` 现为全局唯一（索引不含 tenant_id）。历史上单租户期撞名场景不存在；租户生命周期批次使"各租户自行注册客户端"成为常态后，两个租户注册同名客户端（如都想用 `admin-web`）必然冲突，且重复注册错误向租户管理员泄露"该标识已被别处占用"。

## 范围

权威 DDL 唯一索引改造；OAuth2AppServiceImpl/RequestContextInterceptor 等客户端解析链（findActiveByClientId、getValidClient、code/refresh/JWT 分支的 clientId 校验）全部改带租户解析；重复注册错误语义；契约与测试适配。

## 当前口径

2026-10-08 用户拍板：client_id 改**租户内唯一**（两租户可同名），不采用"全局唯一"口径。沿用空库重建政策，不提供存量库在线迁移工具。

## 验收对照

- [ ] 复合唯一索引与解析链改造。
- [ ] 错误语义不泄露他租户存在性。
- [ ] 契约、e2e、PgIT 回写与适配。

## 非目标 / 遗留

不做存量库在线迁移（环境重建由部署方按重建规程执行）；不改 OAuth2 客户端注册的产品形态。
