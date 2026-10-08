---
doc_type: task
id: T-ACCESS-091
title: Q-063 租户开通与独立平台运营设计定稿
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md
  - docs/design/services/gateway.md#platform-tenant-boundary
depends_on: []
acceptance:
  - 平台身份与租户边界、平台账号管理范围均获用户确认并归位设计
  - 租户初始化、登录迁移、停用恢复和运营范围的待决行为均获用户确认
  - 契约、数据模型与认证接入方案足以实施，未决事项不被默认采纳
  - 按定稿方案建立实施计划、任务及验收依赖，保持 Q-063 未收敛直到产品交付
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

[Q-063](../../../pending-problems.md#q-063) 已获授权启动，目标为交付租户开通和运营能力。实施前仓库只有租户隔离基础与固定租户初始化；先明确影响身份和生命周期的产品行为，避免将讨论建议误当定案。

## 范围

核对现有初始化、会话、服务凭证、OAuth2、租户隔离和前端登录链路；推进[目标设计](../../../design/tenant-lifecycle.md)定稿并拆分后续实施任务。本卡仅承担设计与实施编排准备，不代表功能交付。

## 当前口径

平台身份独立于租户，租户 1 为普通租户；平台运营者不代客户管理。长期边界见[Gateway 设计](../../../design/services/gateway.md#platform-tenant-boundary)。产品行为均已按目标设计完成确认；新增取舍仍遵循用户决策协议。

平台采用多个同权账号，均可管理租户和平台账号，并保护最后一个启用管理员；初始化、随机密码交付、停用恢复和凭据恢复按[平台账号设计](../../../design/tenant-lifecycle.md#platform-accounts)执行。

新租户首管理员采用[随机初始密码一次展示与首次强制改密](../../../design/tenant-lifecycle.md#tenant-admin-handoff)；平台始终可重置首管理员密码且必须审计，作为不代客户管理的限定例外。[运营范围](../../../design/tenant-lifecycle.md#tenant-management)不含租户删除，编码不复用。

[租户登录](../../../design/tenant-lifecycle.md#tenant-login)一次切换至不可变 tenantCode，全部编码由运营开通时指定，租户 1 无 default 特殊绑定；[停用恢复](../../../design/tenant-lifecycle.md#tenant-suspension)采用新请求和新任务立即拒绝、恢复后用户重新登录及旧 OAuth2 令牌失效，有效服务凭证恢复可用。

普通管理台登录与 OAuth2 客户端解耦，不逐租户预建同名管理台 OAuth2 客户端；OAuth2 通道保持独立。

[运行时租户门禁](../../../design/tenant-lifecycle.md#redis-gate)选择共享 Redis 路线；状态不可信时拒绝访问，恢复后自动核对数据库重建，首期单主部署，实例切换和备份恢复采用受控流程。

共享门禁协议由 [T-ACCESS-092](T-ACCESS-092.md) 承载，平台与租户入口由同计划任务完成。

[首次部署](../../../design/tenant-lifecycle.md#tenant-initialization)只初始化平台账号，全部租户从运营台按统一标准模板同事务开通；保留现有空库重建政策，不引入在线迁移。

[前端](../../../design/tenant-lifecycle.md#frontend-realms)共用现有项目，平台与租户的入口、布局、令牌和页面状态分开；租户编码按 `[a-z][a-z0-9-]{0,63}` 校验，不自动转换大小写。

## 验收对照

- [x] 平台账号管理范围与身份边界完整定稿。
- [x] 租户开通、初始化、登录迁移、停用恢复与运营范围完整定稿。
- [x] 数据、接口、认证与审计设计具备可执行依据。
- [x] 建立实施计划、实施任务与真实产品入口验收链。

## 非目标 / 遗留

本卡不直接实现产品功能，不将 Q-063 标为已收敛。后续功能实现和完整回归必须由同主题新任务承接；外部 AI 评审仅在用户另行要求时执行。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
