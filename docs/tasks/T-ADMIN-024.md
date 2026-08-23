---
doc_type: task
id: T-ADMIN-024
title: 恒拒绝退役 API 直接删除
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "执行前 grep 复核前端/SDK/example/活文档对 4 个端点无引用（已核实前端已用新路径 /api/perm/abstract-role/*，归档文档引用不算）"
  - "直接删除恒拒绝映射及配套：/role/create、/role/grant-menu（AdminRoleController）、/user-role/assign、/user-role/revoke（AdminUserRoleController）与关联 DTO；ROLE_API_RETIRED(10111) 错误码退役（码值不复用，ErrorContract 退役清单登记）"
  - "不建兼容路由层、不做别名双写；契约文档同步移除对应条目"
  - "负向验收：已删除端点返回 404 无映射；既有单测全绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ADMIN-024 恒拒绝退役 API 直接删除

## 背景

4 个退役端点保留映射但恒抛 10111 ROLE_API_RETIRED（AdminErrorCode:355）：/role/create、/role/grant-menu、/user-role/assign、/user-role/revoke。已核实无任何真实调用方（前端已切换新路径，旧路径引用仅存归档文档）。按计划「没有存量调用方，不创建兼容层，直接删除旧入口」约束处置。

## 范围

- 删除 4 个映射、配套 DTO 与退役错误码；契约文档同步。

## 当前口径

- 直删不保留兼容层；错误码码值不复用（沿用 ErrorCodeContractTest 退役清单模式）。

## 非目标 / 遗留

- 不扫描/清理归档文档中的历史引用（archive 不作为实现依据）。
