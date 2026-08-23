---
doc_type: task
id: T-ACCESS-018
title: 资源类型收敛（五组合并 + 双常量合一 + 前端权限串）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/frontend/README.md
depends_on: [T-ORG-001]
blocks: [T-ACCESS-019, T-PERM-043]
acceptance:
  - "权威 DDL 种子按 T-ACCESS-016 定稿映射重编：USER/ROLE/MENU/SYSTEM_CONFIG/ORG 五组合并、ADMIN_SYNC_TASK 删除、user_type ADMIN_USER 更名 LOCAL_USER；type_value 终值在同 tenant_id+type_key 内全局唯一（处理 USER=6 既有占用与 ADMIN_* 退役段）"
  - "ResourceTypeCode 与 AdminResourceType 两套常量合一，生产代码按新类型码全量切换（已核实影响约 50 个 Java 文件 + 安全矩阵）"
  - "前端权限串全量切换（已核实约 23 处：user 页 ADMIN_USER/ADMIN_ORG、role 页、config 页、permission-query 临时口径等），与后端类型码一致；无兼容别名双写"
  - "权限矩阵/种子/错误码/文档（api-contract、admin-service-api-contract、frontend 页设计）同步更新；种子、后端、前端权限串与现行文档中不再出现已合并的重复资源类型"
  - "范围排除：@OperationLog.targetType 契约为小写物理表名/逻辑对象码（sys_user、abstract_role 等，见 OperationLogAspect.resolveTargetType 与覆盖测试 KNOWN_TABLE_NAMES 白名单），与资源类型码是两个命名空间，不在本任务修改；若个别日志字段实际存储资源类型码，逐项列名处理，禁止全局替换"
  - "扩展操作按 T-ACCESS-016 §13.3 bit 终值表落地：USER:ENABLE=32（重分配）、USER:RESET_PASSWORD=64、ORG 六码同名同 bit；ADMIN_ROLE:GRANT/REVOKE 零消费者删除不迁移（AdminOperationCode.GRANT/REVOKE 常量一并删除），uk_operation_permission_typed_bit 无冲突"
  - "保留业务键按 T-ACCESS-016 §4.3 终态切换：subject 侧 ADMIN_USER→LOCAL_USER；resource 侧取消类型级保留（LocalProjectionOwner 资源保留常量与 sync 入口 rejectReservedResourceType 调用删除），同批补齐资源 sync 所有权检查——UPSERT/DISABLE/DELETE 任一 mutation 分支在进入前对命中实体统一 rejectIfLocalResource（owner=access-service 即 20045；DELETE 分支现状直接软删命中实体，必须覆盖），并核实 resource-entity/full-sync 清理范围按 sync_metadata(entityKind=RESOURCE_ENTITY, sourceService, scopeKey) 界定、不触及本地投影行（本地投影不写 sync_metadata）；管理入口（resource-entity create/update）类型保留清单换值 {USER, ORG, MENU}；补外部同步命中本地投影行 20045 的负向测试（含 DELETE）"
  - "单测 + PostgreSQL Testcontainers 全绿；空库执行新 DDL 后类型种子自洽（无类型码冲突、无悬挂引用）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ACCESS-018 资源类型收敛

## 背景

归并后残留两套资源类型命名空间：DDL 种子同时存在 USER=6/ADMIN_USER=16、ROLE=5/ADMIN_ROLE=18、MENU=1/ADMIN_MENU=19、SYSTEM_CONFIG=11/ADMIN_CONFIG=22、ADMIN_ORG=17、ADMIN_SYNC_TASK=28 等；代码中引擎枚举 `ResourceTypeCode`（12 码）与管理门禁 `AdminResourceType`（14 码）并存；前端同一工程混用两套权限串（user 页 ADMIN_*、role/config 页非 ADMIN_*）。已确认在 E2E 前全量收敛，验收基线只定一次。

## 范围

- 权威 DDL type_definition 种子终态 + type_value 重编（按 T-ACCESS-016 定稿）。
- 双常量类合一与全量代码切换、安全矩阵与门禁注解更新。
- 前端权限串、路由守卫与页面级 perms 定义切换。
- ADMIN_SYNC_TASK 类型与其无引用种子删除；`role_resource_permission` 等 authorization 表中 resource_type 列语义随类型码切换核对（空库重建，无数据迁移）。

## 当前口径

- 无存量生产数据（权威 DDL 重建模式），迁移成本是代码/种子/前端/文档，不是数据搬移。
- 不建兼容别名层、不做双写；旧类型码直接删除，码值不复用（沿用 ErrorCodeContractTest 退役清单模式登记）。
- ADMIN_FILE/ADMIN_NOTICE 等无重复对象的类型不改名，防止范围扩大。

## 非目标 / 遗留

- 不动业务域 CLASSIFY 分类模型（domain_config CLASSIFY 按 resourceTypeCode 关联，随类型码切换自然生效，不改三模式逻辑）。
- 主体 ID 统一（前置 T-ORG-001）与投影补齐（后续 T-ACCESS-019）不在本任务，三者独立提交；本任务在主体 ID 已统一后进行，类型码直接使用终值。
