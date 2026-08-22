---
doc_type: task
id: T-ACCESS-013
title: OAuth2 资源服务器与 scope 授权模型（委托令牌访问业务 API 的显式开放）
status: proposed
plan: docs/plans/access-post-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-012
blocks: []
acceptance:
  - "OAuth2 委托令牌访问业务 API 由显式配置的路径白名单 + scope 校验控制（默认拒绝）"
  - "JWT 载荷的 client_id/scope/audience 参与授权判定；scope → 权限映射与平台权限体系（PermQueryEngine）语义一致"
  - "撤销令牌（黑名单）对开放路径生效，路径限定不产生绕过"
  - "文档回写：架构文档 §6 的 OAuth2 JWT 适用范围更新为开放路径清单"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-15
---

# T-ACCESS-013 OAuth2 资源服务器与 scope 授权模型

## 背景

T-ACCESS-004 将 OAuth2 JWT 认证分支精确限定为 `/auth/oauth2/userinfo` 单一端点（唯一消费方），委托令牌不得触达管理接口或其他端点。若未来需要"OAuth2 令牌访问业务 API"（如第三方应用按 scope 调接口），必须显式实现资源服务器能力，避免以"全路径 USER"或通配路径方式放开。

## 范围

- 将 JWT 认证分支的路径白名单配置化（默认仅 `/auth/oauth2/userinfo`；**不得默认放开 `/auth/oauth2/**` 通配**——前缀匹配会覆盖 authorize 等非资源端点，开放路径必须逐项显式配置并经 scope/audience 校验）。
- JWT 载荷 `client_id`/`scope`/`audience` 参与授权判定。
- scope → 权限映射（与 PermQueryEngine 平台权限语义一致或独立映射）。
- 黑名单（撤销）对开放路径持续生效。

## 非目标

- 不改变平台用户会话（uuid）认证路径。
- 不引入第三方 OAuth2 服务端（本任务只做资源服务器侧）。

## 完成记录

（待实施后填写。）
