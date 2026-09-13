---
doc_type: task
id: T-ACCESS-037
title: system_config 单入口化（admin /config 退役）
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.3
  - docs/design/access-service-api-contract.md#§17.3（/config 未成册登记位——原 admin 册无 /config 契约段，T-ACCESS-040 实核 0 命中；退役时同步收口该登记行）
  - docs/design/access-service-api-contract.md#§17.2（system-config；T-ACCESS-040 重挂总册）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-040
blocks: []
acceptance:
  - "admin /config 入口退役：ConfigController + ConfigService(Impl) + DTO + 测试删除；契约段（新册）移除"
  - "system_config 管理单入口 /api/perm/system-config（前端唯一消费方，T-PERM-024 收口形态维持）"
  - "门禁口径核对：SYSTEM_CONFIG 操作码消费面收敛到 perm 入口口径，无悬空门禁"
  - "负向验收：/config/* 端点不存在；全量回归绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

system_config 表双管理入口：admin /config（ConfigServiceImpl，UPDATE/DELETE 门禁）与 perm /api/perm/system-config（MANAGE 门禁）。消费面核实（2026-09-13）：前端只调 perm 入口，admin 入口在 frontend/e2e/gateway 主代码零消费——僵尸端点，双门禁语义与双契约描述。

## 范围

退役 admin 入口全链 + 契约回写 + 门禁消费面核对。

## 当前口径

- admin ConfigServiceImpl 的 DELETE 门禁（SYSTEM_CONFIG:DELETE）消费随端点消亡；SYSTEM_CONFIG:DELETE 为 CRUD 预置种子（CROSS JOIN 全类型），**不删操作码本身**——034 的 USER:MANAGE 清理不涉此类预置码。
- depends_on 含 040：契约回写落新册，不写旧册。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改 system_config 表结构与三命名空间 fail-closed 语义（T-ACCESS-007 形态维持）。
