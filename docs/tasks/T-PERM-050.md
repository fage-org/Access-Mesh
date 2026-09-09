---
doc_type: task
id: T-PERM-050
title: resource_type 删除级联清理与引用保护——预置操作定义孤儿根治（含资源实体/授权同类引用面盘点）
status: done
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
  status: done
last_updated: 2026-09-09
---

# T-PERM-050 resource_type 删除级联清理与引用保护——预置操作定义孤儿根治（含资源实体/授权同类引用面盘点）

> 状态：done（2026-09-09 收口）
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

## 当前口径（2026-09-09 用户拍板）

- **方案 = 级联软删**：`deleteTypesByIds` 同事务软删该 resource_type 全部有效操作定义行——对称于创建联动预置（建时自动生 4 行、删时自动清），与 T-PERM-051 投影级联（2026-09-07 定案）、deleteResources 授权级联同款先例；无新错误码。
- **类型级授权行并入级联**：同事务软删 `role_resource_permission` 中 `resource_type` = 被删类型值的有效行（正常流仅剩 scope_all 类型级行——资源行已被行数守卫拒绝、实例级授权随资源删除级联；防御性含残留实例行），`markRoles` 提交后失效角色快照；`OPERATION_PERMISSIONS_BY_TYPE` 按被删类型集合 `evictBatchAfterCommit`（复用 T-PERM-047 终态 per-type 键）。
- **级联面限定 `typeKey=resource_type`**：type_value 仅 tenant+type_key 内唯一（user_type 12 与 resource_type 12 可共存），user_type/role_type 删除不得误伤 resource_type 空间的操作行/授权行。
- **引用面盘点结论（2026-09-09 实核）**：resource_entity 面已由 T-PERM-052 评审批次关闭（行数守卫 20056 拒绝删除）；TYPE_DEFINITION 投影面已由 T-PERM-051 关闭（级联软删投影行 + 投影行下授权行）；user_type/role_type 删除零检查面拆分登记 **T-PERM-056**；`domain_config` CLASSIFY 与 `service_config.extra.syncTypes.sourceTypes` 按 typeCode 字符串引用类型——删除后残留条目为无害脏数据（消费方以现存类型集合为基准交/补集，残留码落入空集，双轨代码轨评审实核），定夺=仅登记不修。
- **级联并发语义**：操作行/授权行写入端（operation-permission CRUD、授权写入口）不持 RESOURCE_ENTITY 树写锁，级联为锁内快照的 best-effort 同事务语义（与 deleteResources 授权级联同款）；交错残留兜底=typeValue 软删不复用（孤儿类型值永不撞新类型）+ 列表 fail-closed 过滤（T-PERM-040 防御层）。
- 存量孤儿行订正语句登记 rebuild-runbook；T-PERM-040 列表过滤防御层保留（`shouldFilterOutOrphanOperationsWhoseTypeDefinitionDeleted` 不回退）。

## 优先级依据

影响为脏数据残留与管理视图不可达（非越权、非损坏；列表链路已 fail-closed 缓解），触发条件为删除自定义 resource_type（低频管理操作）；属低风险维护债，随 T-PERM-044~048 加固批次排期。

## 验收对照

| 验收条目 | 终态 |
|---|---|
| 核心缺口修复（三选一） | 选定级联软删（2026-09-09 用户拍板）：`deleteTypesByIds` 同事务软删被删 resource_type 全部有效操作定义行，无新错误码 |
| 同类引用面盘点并定夺 | resource_entity 面=T-PERM-052 行数守卫已关；TYPE_DEFINITION 投影面=T-PERM-051 级联已关；类型级授权面=并入本任务级联；user_type/role_type 面=拆分 T-PERM-056；domain_config CLASSIFY / syncTypes.sourceTypes 字符串引用面=实核无功能影响，仅登记不修（见当前口径） |
| 既有事实边界（排除项） | 已核实保持：typeValue 分配含软删行取 MAX、两个部分唯一索引下级联软删无冲突恶化路径 |
| 存量孤儿行订正 | rebuild-runbook §3 新增一行（operation_permission / role_resource_permission 两条幂等 UPDATE + 缓存注意）；T-PERM-040 列表过滤防御层保留（`shouldFilterOutOrphanOperationsWhoseTypeDefinitionDeleted` 维持全绿） |
| 与 T-PERM-047 协调 | 级联落库后按被删类型集合 `evictBatchAfterCommit(OPERATION_PERMISSIONS_BY_TYPE)`（复用 047 终态 per-type 键），无另起设计 |
| 回归测试 | 单测 2 用例（级联三面软删 + InOrder 顺序 + 缓存失效断言；typeKey 限定防跨族误伤）+ PgIT 1 用例（真实库三面归零 + `@SpyBean` 操作行软删中段失败整体回滚）；级联语义用例在旧实现（零级联）下必红 |

## 完成记录

- 实现：`TypeDefinitionAppServiceImpl#deleteTypesByIds` 级联块（操作行 + 类型级授权行同事务软删、markRoles、OPERATION_PERMISSIONS_BY_TYPE per-type evictBatchAfterCommit）；`RoleResourcePermissionMapper` 新增 `selectValidPermIdsByResourceTypes` / `selectRoleIdsByResourceTypes`（接口 + XML，delete_flag=0 批量 IN）。
- 回归证据（2026-09-09）：`mvn test -pl access-service -DskipTestcontainers=true` 单测轨道 **1096/0**；`mvn test -pl access-service -Dtest=TypeDefinitionProjectionPgIT` 容器 **7/0**；收口 `mvn test -T 1C`（E2E 含）全 10 模块 SUCCESS——access-service 单测 1096/0 + 容器 180/0 + e2e 14/0 + gateway 93/0。
- 设计回写：api-contract §5.1（remove 级联段 + last_reviewed 注记）、schema 三表注释（type_definition / operation_permission / role_resource_permission）、implementation §5.2（失效接线清单补 type-definition/remove 路径）、architecture §4.3（级联句延伸）、rebuild-runbook §3（存量订正行）、decision-registry 2026-09-09 定案行。
- 双轨评审（2026-09-09）：代码轨零 P0-P2（P3×3 处置：级联并发 best-effort 语义与配置残留面登记进当前口径、deletedTypeValues 微冗余已提取复用）；文档轨 P1（decision-registry 当轮登记）已补、P2（implementation §5.2 枚举缺项）与 P3（architecture §4.3 延伸）已顺手修。
