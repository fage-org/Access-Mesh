---
doc_type: task
id: T-ACCESS-005
title: 实现强事务权限投影并删除内部同步子系统
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/access-service-architecture.md#4-管理事实与权限投影
  - docs/design/permission-center/core-flows.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "用户、组织、菜单及成员关系写入由 access.application 编排，在同一事务内维护对应权限投影"
  - "管理事实是本地实体唯一事实源；投影保留独立主键并通过稳定外部键定位"
  - "权限管理入口拒绝直接修改 access-service 所有的本地投影；外部同步所有权保持有效"
  - "删除 sys_sync_task API（Gateway 对外 /admin/sync-task/*、服务内 /sync-task/*）、实体、Mapper、builder、handler、scheduler、重试、补偿和内部 full-sync 编排；退役路径不再注册 Controller 映射"
  - "回写 admin-service-api-contract.md：以同事务本地权限投影取代 sys_sync_task 契约，并逐接口保留或更正明确的不同步例外"
  - "删除 access 内部 PermissionFeignClient/SyncTaskFeignClient 及相关依赖；外部 sync/full-sync 和 sync_metadata 保留"
  - "故障注入证明管理事实、权限投影和 permission_change_log 任一步失败都会整体回滚"
  - "缓存失效只在事务成功提交后发生，回滚不发布变更"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-005 实现强事务权限投影并删除内部同步子系统

## 背景

同库后，旧 outbox/Feign 链路的最终一致性与人工补偿不再必要，应由本地事务直接保证投影一致。

## 范围

- 建立跨域写编排和投影所有权保护。
- 替换用户、组织、菜单、成员关系的内部同步调用。
- 删除完整内部同步子系统并保留外部同步能力。

## 完成记录

（待实施后填写。）
