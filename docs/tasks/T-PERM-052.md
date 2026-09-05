---
doc_type: task
id: T-PERM-052
title: 资源类型级所有权边界——类型声明门禁（sync 独占 + 管理面只读 + 声明变更守卫）
status: done
plan: docs/plans/design-audit-followup-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.1
  - docs/design/permission-center/api-contract.md#§5.3
  - docs/design/permission-center/api-contract.md#§6.2.2
  - docs/design/permission-center/api-contract.md#§6.3.1
  - docs/design/access-service-architecture.md#§4.3
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "类型所有权声明载体（用户定案 2026-09-05：extra JSONB 约定）：type_definition.extra 携带 managedMode（两态 MANAGED=缺省/SYNC，用户定案两态——SYSTEM 语义由 is_system 承载）+ syncSourceService（SYNC 必填）；type-definition create/update 保存边界结构校验（20044）：仅 type_key=resource_type 可携带此二键、mode 值域受限、SYNC 必填非空白来源且须为已注册有效服务、MANAGED 携带来源拒绝、非法 JSON 拒绝（对齐 SyncTypeGuard.validateSyncTypesExtra 先例防拼写错误静默失效）；读取侧 extra 损坏按 MANAGED 处理（fail-closed：外部同步拒绝、管理面可写）"
  - "声明变更守卫（用户定案 2026-09-05：无有效行才可改）：update 的新旧声明有效值变更（extra 整串替换语义下含删键隐式切回 MANAGED）且类型下存在有效 resource_entity 行 → 20056 TYPE_OWNERSHIP_CHANGE_CONFLICT；声明未变不触发行数查询"
  - "sync 入口类型门禁：resource-entity/sync 与 full-sync 目标类型必须声明 SYNC 且 syncSourceService==调用服务身份，否则 SECURITY_DENIED/RESOURCE_TYPE_OWNERSHIP_DENIED（类型不存在/声明缺失 fail-closed 同拒）；取代 syncTypeGuard 资源维度——SyncTypes.resourceTypeCodes 字段与 resource() 工厂删除、validateSyncTypesExtra 字段白名单收为三维（subject/role/source）且含已退役 resourceTypeCodes 的保存直接拒绝；T-ACCESS-018 的 rejectIfLocalResource（owner=access-service 行 20045）保留为纵深防御"
  - "管理面类型门禁：create/batch-create/update/move/remove 对 SYNC 类型一律 20055 RESOURCE_EXTERNALLY_MAINTAINED（资源由外部来源维护，请到来源系统操作）；batch-create 按去重类型码一次批量判定、remove 对删除全集（含 batchGetDescendantIds 展开的后代）批量取实体按类型值一次判定（N+1 禁令）——sync 通道允许跨类型父子边，MANAGED 根的子树可含 SYNC 类型后代，只判请求根集合会连带清掉外部来源维护的子树；读路径（tree/list/detail）不受限"
  - "错误码登记：20055/20056 perm 段顺延占用（20054 后首次取号；T-PERM-046 全域新码后续取号从 20057 起顺延避让）"
  - "契约与文档回写：api-contract §5.1（声明约定+接入流程+变更约束 20056）/§5.3（SYNC 类型只读+级联守卫+20055）/§6.2.2+§6.2.2.1（类型门禁与 RESOURCE_TYPE_OWNERSHIP_DENIED）/§6.3.1（syncTypes 三维+退役字段保存拒绝）；architecture §4.3 与 core-flows 的 T-ACCESS-018 行级口径改写为类型级（标注 2026-09-05 定案取代）；schema：type_definition.extra 注释补约定、resource_entity.maintain_source 注释登记 'SYNC' 记录值（判定不依赖该列，所有权由类型声明承载）"
  - "回归锁（须在旧实现下失败）：MANAGED 类型/异源 SYNC/未声明类型 × {sync、fullSync} 入口拒绝 + verify never 到 insert/update/softDeleteBatch；管理面 create/update/move 命中 SYNC 类型 20055 + never 写库；remove 级联含 SYNC 类型后代整批拒绝 + never softDeleteBatch（旧实现会连后代一并软删）；声明校验负向五型（非 resource_type 携带/非法 mode/SYNC 缺来源/来源未注册/MANUAL 语义携带来源）+ 变更守卫三态（有行 20056/删键隐式变更同拒/无行放行）+ 声明未变不查行数；syncTypes 含已退役字段保存拒绝；同源 SYNC 类型正常同步、本地投影既有拒绝行为不回归"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-05
---

# T-PERM-052 资源类型级所有权边界——类型声明门禁（sync 独占 + 管理面只读 + 声明变更守卫）

> 状态：done（2026-09-05 设计体检 P1 立项为行级方案，同日执行时经用户四项定案改定为类型级所有权方案后实施收口；access-service 全量套件 1039 单测 + 151 容器测试全绿）
> 依赖：无硬依赖；错误码与 T-PERM-046 取号协调（本卡占 20055/20056）

## 设计口径（2026-09-05 用户定案）

原行级方案（sync_metadata 反查接管拒绝 + maintain_source 行级白名单 + FULL diff 归属核验）在执行时被用户改定为**类型级所有权**模型，四项定案：

1. **声明载体 = type_definition.extra JSONB 约定**（非新列/独立表）：`managedMode` + `syncSourceService` 两键；
2. **mode 值域 = 两态 MANAGED(缺省)/SYNC**：SYSTEM 语义由既有 is_system 承载，不设第三态；
3. **syncTypes 移除资源维度**：resource-entity 通道唯一门禁 = 类型声明，service_config.extra.syncTypes 只剩 subject/role/source 三维；
4. **无有效行才可改**：类型下存在有效资源行时声明有效值不得变更（含删键隐式切回 MANAGED）。

两个混合场景的用户答案（推翻 T-ACCESS-018「公共类型外部同步合法」口径）：

- 外部服务同步用户 → 走自有用户类型（主体通道自有类型 + 资源通道自有类型），不动公共 `USER`；
- 菜单归本系统管理则同用户口径；其他服务的菜单走自建类型（如 `BI_MENU`）。

模型不变式：每个 resource_type 类型单一所有权——MANAGED=管理面维护（公共基础类型属之）/ SYNC=声明来源服务独占同步且管理面只读。双向越界（sync 接管人工行、管理面改/删同步行）在类型层结构性消除。`maintain_source` 降级为记录值（'SYNC' 登记进 schema 注释），判定不依赖该列。

## 范围

- ResourceTypeOwnershipGuard（extra 解析/保存校验/管理面门禁/变更守卫，新域组件）；
- type-definition create/update：声明结构校验 + 变更守卫（20056）；
- resource-entity sync/fullSync 入口类型门禁（取代 syncTypeGuard 资源维度）；
- 管理面 create/batch-create/update/move/remove 五入口门禁（remove 含级联删除全集守卫）；
- SyncTypeGuard 资源维度移除（字段/工厂/白名单/保存校验）；
- 错误码 20055/20056；契约（§5.1/§5.3/§6.2.2/§6.2.2.1/§6.3.1）+ architecture §4.3 + core-flows + schema 注释回写；
- 双向回归锁。

## 非目标

- 主体/角色同步通道维持现状（已是调用方自有类型+syncTypes 白名单模型，与类型级所有权同向）；
- service-config 接口声明通道维持现状（AccessMesh 内部通道，自有 owner_service_code 行级守卫，不写 sync_metadata）；
- 本地投影链路不动（rejectIfLocalResource 保留为纵深防御）；
- 不处理类型删除时的行数检查（deleteTypesByIds 现状不查行，见已知边界）；
- 前端类型定义页/资源页不改造（extra 为 JSON 编辑、20055 走统一错误提示；类型声明管理面增强另行评估）。

## 已知边界（登记，另行评估）

- **type-definition/remove 不检查类型下是否仍有资源行**（存量行为）：删除 SYNC 类型后其行失去类型解析（sync 报 RESOURCE_TYPE_OWNERSHIP_DENIED、管理面业务键寻址 20021）——与变更守卫「无有效行才可改」同构，是否补行数检查另行评估；
- **sync 通道跨类型父子边仍合法**（parentResourceTypeCode 可与 item 类型不同）：本卡以 remove 级联全集守卫防御，是否收紧为同类型父边另行评估；
- **SYNC 类型声明来源服务被删除后**类型声明残留（service-config remove 不级联类型声明）：该类型同步入口持续拒绝（fail-closed 方向），人工清理类型声明即可。

## 完成记录

- 2026-09-05 实施：错误码 20055/20056；ResourceTypeOwnershipGuard 新建（解析/校验/门禁/变更守卫）；ResourceEntityDomainService.hasValidRowsOfType + mapper existsValidByType；TypeDefinitionAppServiceImpl 声明校验+变更守卫；ResourceEntitySyncAppServiceImpl 入口门禁替换白名单；ResourceManageAppServiceImpl 五入口门禁（remove 级联全集）；SyncTypeGuard 资源维度移除；schema/api-contract/architecture/core-flows 回写；回归用例 22 个新增/适配（含旧实现下失败锁），受影响测试类 118 用例全绿，access-service 全量套件通过。
