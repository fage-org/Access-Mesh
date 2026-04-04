# 通用权限中心 - SQL 与设计文档

本目录包含通用权限中心的 PostgreSQL 表结构及详细设计文档，便于直接执行建表并交由 AI 或开发实现后端与管理端。

## 文件说明

| 文件 | 说明 |
|------|------|
| **permission_center_schema.sql** | 完整建表 DDL：17 张表、唯一约束与部分唯一索引、GIN 索引、表/列中文注释。无外键，按文件内顺序执行即可。 |
| **DESIGN.md** | 详细设计文档：概念、表清单、鉴权/授权流程、位运算约定、条件审核、冲突检测、资源依赖、管理端与接口建议，供 AI 开发项目使用。 |
| **MIXED_KERNEL_ARCHITECTURE.md** | 混合内核架构文档：三服务拓扑（gateway + identity-service + permission-center）、运行时边界、核心模型、关键流程。 |
| **MIXED_KERNEL_EXECUTION_PLAN.md** | 分阶段执行计划：Phase 0-8 的状态、交付物与验证记录。 |

## 使用方式

1. **建表**：在目标 PostgreSQL 库中按**文件内顺序**执行 `permission_center_schema.sql`（建议先建库或 schema，再执行）。
2. **开发**：将 `DESIGN.md` 与 `permission_center_schema.sql` 一并提供给 AI 或开发人员，作为实现权限中心后端、鉴权服务、管理端页面的依据。
3. **架构**：参阅 `MIXED_KERNEL_ARCHITECTURE.md` 了解三服务拓扑与运行时边界。

## 表一览（17 张）

- 基础：type_definition, biz_domain, abstract_user, abstract_role, operation_permission, resource_entity, resource_api_mapping, permission_condition
- 关联：user_role, role_resource_permission
- 配置：domain_scope_config, domain_relation_config, domain_scope_binding, resource_dependency, permission_conflict_rule, permission_version
- 审计：permission_change_log

## 设计要点

- 软删除统一使用 `delete_flag`（0=未删除，删除时填本行 id），`deleted_at` 仅为审计展示字段。
- 类型定义使用专用 `type_definition` 表（原 system_config），按 `(type_key, type_value)` 查枚举。
- 操作权限绑定资源类型（`operation_permission.resource_type`），位运算 `binary_bit + inherit_mask` 使用 BIGINT（63 位）。
- 权限条件支持预设（handler 编码）+ 自定义（需审核），通过 `condition_source` 和 `status` 区分。
- 冲突互斥在查询时检测失效，不在写入时阻止，异步通知管理员修正。
- 资源依赖为声明式元数据，由资源注册方自动维护，写入时校验防环。
- 资源树继承（子/父）由查询接口参数 `inherit_mode` 控制，不在表结构中定义。
- 所有表均含 tenant_id 及 created_by/updated_by/deleted_by/created_at/updated_at/deleted_at。
