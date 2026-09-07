---
doc_type: task
id: T-PERM-050
title: resource_type 删除级联清理与引用保护——预置操作定义孤儿根治（含资源实体/授权同类引用面盘点）
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§5.3
depends_on: []
blocks: []
acceptance:
  - "核心缺口修复（方案三选一，执行时决策）：创建 resource_type 同事务预置 CRUD 四操作位（TypeDefinitionAppServiceImpl#createTypeDefinition，schema type_definition 表注释明文承诺），但 deleteTypesByIds 仅 softDeleteBatch 类型定义行——零级联零引用检查，预置操作行成永久孤儿（delete_flag=0 且类型已软删，批量反解缺项；管理列表已 fail-closed 过滤致其不可见，无手动清理入口）。方案：(a) 同事务级联软删该 resource_type 全部有效操作定义行（对齐创建联动的对称语义）；(b) 删除保护——存在有效操作定义行时拒绝删除类型（对齐 biz-domain remove 引用保护先例）；(c) 维持孤儿+列表过滤现状，修订 schema 表注释与承诺口径"
  - "同类引用面盘点并定夺范围（执行时决策）：resource_entity.resource_type（NOT NULL）与 role_resource_permission.resource_type 同样以 type_value 引用类型定义，删除类型同样零检查零级联——已删类型下的资源实体行、授权行同为孤儿（管理视图类型反解缺项不可达）。定夺：并入本任务统一处置 / 拆分登记后续任务 / 仅登记不修；若级联软删操作定义行，须评估已授权行（引用该类型操作位的 role_resource_permission）的处置语义，避免只清操作定义留下授权行反解缺项。【T-PERM-051 协调注记（2026-09-07）：引用面盘点须含「类型定义自身投影行及其下授权行」——该面已随 T-PERM-051 落地级联处置（deleteTypesByIds 同事务软删 TYPE_DEFINITION 投影行 + 投影行下授权行，deleteResources 同款）；本卡盘点时该子面已关闭，勿重复处置】"
  - "既有事实边界（排除项，已核实）：typeValue 分配含软删行取 MAX（selectMaxTypeValueAllRows，软删不复用是分配语义本身）→ 孤儿操作行 resource_type 永不撞新类型，uk_operation_permission_typed / uk_operation_permission_typed_bit（均 WHERE delete_flag=0 部分索引）无冲突恶化路径，级联软删不与历史行冲突"
  - "存量孤儿行订正：根因修复只约束新写入，存量孤儿操作行需登记订正语句（对齐 rebuild-runbook 既有订正语句先例）；T-PERM-040 落地的 operation-permission/list fail-closed 过滤为防御层保留（存量与跨环境数据兜底），锁定用例 shouldFilterOutOrphanOperationsWhoseTypeDefinitionDeleted 不回退"
  - "与 T-PERM-047 协调：若采用级联软删方案，该写路径属操作定义变更，OPERATION_PERMISSIONS_BY_TYPE 缓存失效接线随 047 统一处置或本任务一并落地（排期时定）；若采用删除保护方案则无新写路径，无缓存接线需求。**047 终态（2026-09-07 收口）**：per-type evict 模式与键构造 PermCacheCatalog.operationPermissionsByTypeKey 已就绪——级联软删落库后对被删类型集合逐键 evictBatchAfterCommit 即可复用，无需另起设计"
  - "回归测试：按选定方案锁定语义——级联方案断言同事务软删且失败回滚（类型行与操作行同生共死）；保护方案断言存在引用时拒绝（新错误码走 20001-29999 分段空闲段并回写契约 §5.1）；孤儿过滤既有用例维持全绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-31
---

# T-PERM-050 resource_type 删除级联清理与引用保护——预置操作定义孤儿根治（含资源实体/授权同类引用面盘点）

> 状态：proposed（T-PERM-040 收口登记，2026-08-31 设计定案：列表侧 fail-closed 缓解先行落地，根因修复登记后续任务）
> 依赖：无硬依赖（独立维护债；与 T-PERM-047 排期协调见 acceptance 第 5 条；与 T-PERM-051 引用面互需协调已由 051 侧登记，见 acceptance 第 2 条注记）
> 前置验收：见 acceptance

## 背景

T-PERM-040 收口的外部评审 P1 揭示类型生命周期不对称：`TypeDefinitionAppServiceImpl#createTypeDefinition` 对 `resource_type` 新类型同事务联动预置 CRUD 四操作位（`insertPresetOperations`，schema `type_definition` 表注释明文承诺「创建 resource_type 时自动预置 CRUD 四个 operation_permission」），但 `deleteTypesByIds` 在门禁与 isSystem 过滤后仅 `softDeleteBatch` 类型定义行——不级联清理操作定义、不检查任何引用。

后果：操作定义行成永久孤儿（`delete_flag=0` 且 `resource_type` 指向已软删类型值，批量反解 `batchResolveTypeCodes` 只返回未软删类型而缺项）。T-PERM-040 已在 `operation-permission/list` 落地 fail-closed 过滤（不回退逐项解析，锁定用例断言逐项解析零调用），缓解本链路影响；但孤儿行在管理列表不可见即无手动清理入口，纯残留。

引用面不止操作定义：`resource_entity.resource_type`（NOT NULL）与 `role_resource_permission.resource_type` 同以 `type_value` 引用类型定义，删除类型同样零检查零级联——该类型下的资源实体行、授权行同为孤儿，管理视图类型反解缺项不可达。

无冲突恶化路径（已核实）：`selectMaxTypeValueAllRows` 含软删行取 MAX，typeValue 软删不复用——孤儿行 `resource_type` 永不撞新类型；两个部分唯一索引（`uk_operation_permission_typed` / `uk_operation_permission_typed_bit`，均 `WHERE delete_flag = 0`）下级联软删亦不与历史行冲突。

## 范围

- 级联/保护方案选定与落地（三选一见 acceptance 第 1 条），含错误码契约与 schema 注释回写。
- 同类引用面（资源实体/授权行）范围定夺与处置（并入/拆分/仅登记）。
- 存量孤儿行订正语句登记（rebuild-runbook）。
- 不改创建侧联动预置语义（T-PERM-028 落地行为保持）；不改 T-PERM-040 列表过滤防御层。

## 优先级依据

影响为脏数据残留与管理视图不可达（非越权、非损坏；列表链路已 fail-closed 缓解），触发条件为删除自定义 resource_type（低频管理操作）；属低风险维护债，随 T-PERM-044~048 加固批次排期。
