---
doc_type: task
id: T-PERM-043
title: GROUP_ROLE 写入口删除与前端隐藏
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/role-manage.md
depends_on: [T-ACCESS-019, T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "GROUP_ROLE 专用写入口直接删除：group-role CRUD 写操作接口与 abstract-role/extra-roles/add|remove 接口移除（已核实前端/SDK 无任何调用方，不做兼容层），GroupRoleAppServiceImpl 及写 abstract_user_id=null 的死路径（违反 DDL NOT NULL，从未可用）同步删除"
  - "通用角色创建/更新入口显式拒绝 GROUP_ROLE：复用 ROLE_TYPE_MISMATCH(20022)（类型不匹配语义）；rejectReservedRoleType/LOCAL_PROJECTION_IMMUTABLE(20045) 为本地投影保护语义、GROUP_ROLE 不在其保留集，不适用本场景；不新增专属错误码"
  - "前端隐藏 GROUP_ROLE：角色管理页角色类型选项默认不出现（保留代码不删，常量/开关控制）；无导航入口现状保持"
  - "BASIC_ROLE 为首期唯一功能角色在契约与前端页面口径中明确"
  - "双事实源遗留登记：管理侧 user_role 关系与运行时 abstract_role.extra.basicRoleIds 两套读取路径的现状写入遗留清单，待未来按 role_inclusion(group_role_id, included_role_id) 单事实源设计另行立项时统一"
  - "单测 + PG 用例：已删除写入口做不存在负向验收（模式同 T-ACCESS-011 退役接口验收）；BASIC_ROLE CRUD 不受影响"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-PERM-043 GROUP_ROLE 写入口删除与前端隐藏

## 背景

GROUP_ROLE 写入路径从未可用：GroupRoleAppServiceImpl:123 写 user_role.abstract_user_id=null，违反 DDL NOT NULL（access-service.sql:1013），插入即抛异常；同时管理侧从 user_role 关系读组内 BASIC_ROLE、运行时从 abstract_role.extra(JSONB).basicRoleIds 展开，两套事实源互不同步。已确认处置：直接删除无调用方的专用写入口（与计划「没有存量调用方就不创建兼容层」约束一致，不为无调用方接口维护「功能未开放」语义）；前端隐藏；BASIC_ROLE 为首期唯一功能角色。本任务在 T-ACCESS-019 之后实施（同触 RoleManageAppServiceImpl，避免并发）。

## 范围

- 专用写接口与 GroupRoleAppServiceImpl 死路径删除。
- 前端 GROUP_ROLE 选项隐藏（不删代码）。
- 遗留双事实源清单登记（供未来立项输入）。

## 当前口径

- 与退役 API（T-ADMIN-024）同模式：无调用方直接删，错误码不复用、不加映射层。
- 通用入口复用现有类型校验错误，不新增错误码。
- 未来真实需求出现时按 role_inclusion 单事实源表设计重立项，删除 extra.basicRoleIds，禁止继续滥用 user_role 表达角色包含关系。

## 非目标 / 遗留

- GROUP_ROLE 枚举、role_type 种子与读模型保留且冻结（不扩展、不新增入口、不做能力框架）。
- 不实现 role_inclusion、不做角色包含运行时展开。
