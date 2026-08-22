---
doc_type: task
id: T-ACCESS-015
title: 菜单 CRUD 写链路对齐 v3.5 最终态与权威 DDL（消除 sys_menu DDL-实体漂移）
status: proposed
plan: docs/plans/access-post-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#§3-模块边界
  - docs/design/permission-center-v3.5-design.md#§2.1
  - docs/design/permission-center-v3.5-design.md#§4.1
  - docs/design/schema/access-service.sql
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-012
blocks: []
acceptance:
  - "执行前确认：菜单管理对外契约字段终态（displayName/menuType=DIR|MENU|EXTERNAL|IFRAME|HIDDEN 字符串枚举、移除 perms/component/visible）与前端菜单管理页消费字段联动方案"
  - "SysMenu 实体与 SysMenuMapper 对齐 access-service.sql sys_menu 列（display_name、menu_type 5 值 VARCHAR、无 name/perm_code/visible/component）；Mapper XML 无 perm_code 查询残留，findByPermCode 与 MENU_PERM_CODE_EXISTS 按终态退役或显式保留理由"
  - "菜单写链路（create/update/delete）在真实 PostgreSQL（Testcontainers 或既有空库测试轨道）下成功执行，覆盖 uk_sys_menu_tenant_resource/uk_sys_menu_tenant_path 唯一索引冲突路径"
  - "MenuCreateReq/MenuUpdateReq 契约 DTO 按 v3.5 §2.1 最终态定稿并回写 admin-service-api-contract §菜单接口；BUTTON 分支逻辑（MENU_TYPE_BUTTON/非按钮→按钮投影删除）随 5 值枚举移除，ADMIN_MENU 投影对齐 v3.5 §4.1 派生公式（业务菜单=resource_type 非空）"
  - "存量 mock DomainService 的菜单测试迁移到新字段；LocalProjectionDomainService.upsertAdminMenu 名参与 UserMenuQueryService 消费链路回归通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-ACCESS-015 菜单 CRUD 写链路对齐 v3.5 最终态与权威 DDL

## 背景

T-ACCESS-006 建立跨域只读查询时登记的存量 DDL-实体漂移：权威 DDL `access-service.sql` 的 `sys_menu` 已按 v3.5 最终态收敛（`display_name`、`menu_type` 5 值枚举 DIR/MENU/EXTERNAL/IFRAME/HIDDEN、无 `component`/`visible`/`perm_code` 列），但菜单 CRUD 写路径（`MenuWriteAppServiceImpl` + `SysMenu` 实体 + `SysMenuMapper`）仍使用旧实体字段 `name`/`visible`/`perm_code`/`component` 与数字 `menu_type`，Mapper 仍按 `perm_code` 查询——真实 PostgreSQL 下菜单创建/更新与相关查询会直接失败（现有测试仅 mock DomainService，未覆盖此组合）。原登记由 T-ACCESS-012 收口；2026-08-22 评审确认该收敛属功能开发（含对外契约 DTO 变更），与文档生命周期任务主题不同，按治理规则新开本任务承接。

## 范围

- `SysMenu` 实体、`SysMenuMapper`（含 XML）对齐权威 DDL 列。
- `MenuCreateReq`/`MenuUpdateReq` 契约 DTO 按 v3.5 §2.1 定稿（含错误码处置：`MENU_PERM_CODE_EXISTS`）。
- `MenuWriteAppServiceImpl` 写链路与 BUTTON 分支移除后的投影/删除语义。
- `LocalProjectionDomainService.upsertAdminMenu` 及 `UserMenuQueryService` 消费链路回归。
- 真实 PostgreSQL 写入验证与既有菜单测试迁移。

## 非目标 / 遗留

- 不改变 v3.5 §2.1 已定稿的菜单模型语义（零权限化、5 值枚举、派生公式）。
- 前端菜单管理页字段联动在执行前确认项中登记，不在本卡预设方案。
