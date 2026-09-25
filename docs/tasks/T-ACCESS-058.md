---
doc_type: task
id: T-ACCESS-058
title: （ADM-T03）映射模型、服务模式与同步/管理面
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §8.1/§8.3
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-056
blocks: []
acceptance:
  - "resource_api_mapping 新增 required_operation_id + 维护来源（MANUAL/SERVICE_SYNC/BOOTSTRAP）、service_config 新增 api_auth_mode（NOT NULL DEFAULT 'LEGACY_API'，可信服务配置控制、不接受客户端模式头）；业务类型从操作定义取得（不两份类型真值）；API:ACCESS 不得再作为 requiredPermission；schema 文档与迁移脚本同批（COMMENT 改动须同步迁移脚本先例）"
  - "版本化同步 DTO（requiredPermission 真正落库并被读侧消费；旧协议不自动升级）；手工/同步/bootstrap 共用校验保存（登记 API、服务维护权、租户、操作有效且属选定类型；任一必要操作引用解析失败整批回滚）；操作引用删除守卫并入操作位变更/删除守卫引用判定（20069 族）；操作覆盖改变即使 ID 不变也重算准入投影"
  - "FULL 只收敛该服务 SERVICE_SYNC 自有映射；清理归属按映射来源判定（修复 MANUAL 映射绑 SERVICE_SYNC 实体被 FULL 误清的现行缺口——MappingSyncHandlerImpl 按实体 maintain_source/owner 推断）；登记实体回收不使保留映射悬挂（N16/N17/N18）；同路由跨 owner 争写拒绝"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-058 （ADM-T03）映射模型、服务模式与同步/管理面

## 背景

设计 §8.1/§8.3（报告临时编号 ADM-T03）。现行 ResourceApiMapping 无业务操作引用；清理归属按绑定 API 实体推断（已核实的误清缺口）；旧同步 DTO operationCode 已于 T-PERM-053 删除。

## 范围

- DDL/实体/Mapper/管理面 API 与前端（requiredPermission 编辑）；软删/类型删除等间接路径同经引用处置守卫；API 登记实体保留（不删除 API 实体、不改为任意业务实例——设计 §11 映射数据行拍板）。

## 非目标 / 遗留

- 存量运行库盘点与补操作处置在 T-ACCESS-061/T-PERM-054；网关消费在 T-ACCESS-059。
