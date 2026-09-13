---
doc_type: task
id: T-ACCESS-036
title: resource_entity.sort_order 全字段面退役
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.2
  - docs/design/permission-center/api-contract.md（resource-entity 契约；040 完成后重挂新册）
  - docs/design/schema/access-service.sql
  - perm-sdk/perm-common（ResourceCreateReq/ResourceUpdateReq/ResourceResp/ResourceEntitySyncReq 单源双侧）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-040
blocks: []
acceptance:
  - "resource 面 sortOrder 全量退役（以全仓 rg sortOrder 清单核对）：resource_entity.sort_order 列与实体字段、SDK perm-common 册 ResourceCreateReq.sortOrder / ResourceUpdateReq.sortOrder / ResourceResp.sortOrder **与 access-service 服务端册 permission/dto/resp/ResourceResp.sortOrder**（HTTP 线格式独立于 SDK——仅改 SDK 则响应仍回 sortOrder）、ResourceEntitySyncReq 与 ResourceEntitySyncItem（full-sync item）的 sortOrder、ResourceTreeResp.ResourceTreeNode.sortOrder、全部写入点（管理面 create/batch/update 与资源同步通道）、前端 resource-operation.ts 类型、hook.ts 创建/编辑提交载荷与 ResourceForm.vue 表单展示/必填链路"
  - "role/menu/org/type_definition 的 sortOrder 不在范围（功能角色排序、菜单展示排序、组织投影源、类型定义排序均在用；hook.ts:63 的树排序消费的是 type_definition.sortOrder），验收以范围区分为准"
  - "负向锁与既有严格 mapper 行为锁对齐（T-PERM-053 先例）：旧载荷（仍含 sortOrder）→ 400 且不触达业务层（全局唯一 ObjectMapper 为 cacheObjectMapper 裸实例、FAIL_ON_UNKNOWN_PROPERTIES 保持 Jackson 默认开启，未知字段走 HttpMessageNotReadableException → 90001 信封）；不放宽 mapper 配置，ServiceConfigSyncOperationCodeRetiredTest 同款锁风格新增 sortOrder 用例"
  - "前后端与 SDK 同批锁步（硬断裂，空库无存量调用方）：前端停发与表单链路清理、SDK 双册删字段与 access-service 同一提交批"
  - "PermCommonReqContractTest 注解签名快照更新；契约（新册）回写（明确 400 行为）；全量回归绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

resource_entity.sort_order 全仓零读取方（无任何按该列排序的消费；资源管理页创建/编辑当前每次提交 sortOrder、sync 通道可写该字段，但均为死数据写入）。因涉 SDK 单源 DTO、同步通道载荷与前端提交面，从字段消减批次单列。

## 范围

resource 面 sortOrder 的列、实体、SDK 双册字段（含 ResourceEntitySyncReq/full-sync item）、树响应字段、全部写入点、前端类型与提交/表单展示链路一次性退役（前端同批清理属字段清理，非信息架构变更——设计 §6 的「前端 IA 维持」不冲突）。

## 当前口径

- 未知字段实际通道为严格 mapper 400（HttpMessageNotReadableException → 90001 信封），与 T-PERM-053 的 operationCode 退役行为锁同一机制——负向锁钉该行为，不放宽 mapper。
- 空库窗口内做对外契约变更成本最低（无存量调用方）；前后端 + SDK 同批锁步发布。
- depends_on 含 040：契约回写落新册。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不动 abstract_role.sort_order（功能角色在用）、sys_menu.sort_order（菜单展示在用）、sys_org.sort_order（组织投影源）、type_definition.sort_order（类型定义在用）。
