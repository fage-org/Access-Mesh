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
  - "类型所有权声明载体（用户定案 2026-09-05：extra JSONB 约定）：type_definition.extra 携带 managedMode（两态 MANAGED=缺省/SYNC，用户定案两态——SYSTEM 语义由 is_system 承载）+ syncSourceService（SYNC 必填）；type-definition create/update 保存边界结构校验（20044）：仅 type_key=resource_type 可携带此二键、mode 值域受限、SYNC 必填非空白来源且须为已注册、未软删、status=1 启用的服务（与运行时入口同规则，codex 二轮复评 P2 对齐；保留内部来源如 admin-service 拒绝——运行时拒绝其冒充，声明即锁死类型）、MANAGED 携带来源拒绝、已知键显式 null 拒绝、非法 JSON 拒绝（对齐 SyncTypeGuard.validateSyncTypesExtra 先例；未知键开放不视为声明——拼错键=无声明按缺省 MANAGED，codex 二轮复评定案）；读取侧 extra 损坏按 MANAGED 处理（fail-closed：外部同步拒绝、管理面可写）"
  - "声明变更守卫（用户定案 2026-09-05：无有效行才可改）：update 的新旧声明有效值变更（extra 整串替换语义下含删键隐式切回 MANAGED）且类型下存在有效 resource_entity 行 → 20056 TYPE_OWNERSHIP_CHANGE_CONFLICT；is_system 系统预置类型所有权声明钉死不可变更（codex 二轮复评 P1-1 定案：空类型翻转后事实链路照旧写入即双 writer，先于行数判定）；声明未变不触发行数查询"
  - "sync 入口类型门禁：resource-entity/sync 与 full-sync 目标类型必须声明 SYNC 且 syncSourceService==调用服务身份，否则 SECURITY_DENIED/RESOURCE_TYPE_OWNERSHIP_DENIED（类型不存在/声明缺失 fail-closed 同拒）；取代 syncTypeGuard 资源维度——SyncTypes.resourceTypeCodes 字段与 resource() 工厂删除、validateSyncTypesExtra 字段白名单收为三维（subject/role/source）且含已退役 resourceTypeCodes 的保存直接拒绝；T-ACCESS-018 的 rejectIfLocalResource 行级防线已随 2026-09-05 内部来源统一收编删除（见第 8 条）"
  - "管理面类型门禁：create/batch-create/update/move/remove 对 SYNC 类型一律 20055 RESOURCE_EXTERNALLY_MAINTAINED（资源由外部来源维护，请到来源系统操作）；batch-create 按去重类型码一次批量判定、remove 对删除全集（含 batchGetDescendantIds 展开的后代）批量取实体按类型值一次判定（N+1 禁令）——sync 通道允许跨类型父子边，MANAGED 根的子树可含 SYNC 类型后代，只判请求根集合会连带清掉外部来源维护的子树；读路径（tree/list/detail）不受限"
  - "错误码登记：20055/20056 perm 段顺延占用（20054 后首次取号；T-PERM-046 全域新码后续取号从 20057 起顺延避让）"
  - "契约与文档回写：api-contract §5.1（声明约定+接入流程+变更约束 20056）/§5.3（SYNC 类型只读+级联守卫+20055）/§6.2.2+§6.2.2.1（类型门禁与 RESOURCE_TYPE_OWNERSHIP_DENIED）/§6.3.1（syncTypes 三维+退役字段保存拒绝）；architecture §4.3 与 core-flows 的 T-ACCESS-018 行级口径改写为类型级（标注 2026-09-05 定案取代）；schema：type_definition.extra 注释补约定、resource_entity.maintain_source 注释登记 'SYNC' 记录值（判定不依赖该列，所有权由类型声明承载）"
  - "回归锁（须在旧实现下失败）：MANAGED 类型/异源 SYNC/未声明类型 × {sync、fullSync} 入口拒绝 + verify never 到 insert/update/softDeleteBatch；管理面 create/update/move 命中 SYNC 类型 20055 + never 写库；remove 级联含 SYNC 类型后代整批拒绝 + never softDeleteBatch（旧实现会连后代一并软删）；声明校验负向五型（非 resource_type 携带/非法 mode/SYNC 缺来源/来源未注册/MANUAL 语义携带来源）+ 变更守卫三态（有行 20056/删键隐式变更同拒/无行放行）+ 声明未变不查行数；syncTypes 含已退役字段保存拒绝；同源 SYNC 类型正常同步、本地投影既有拒绝行为不回归"
  - "双轨评审收口（2026-09-05）：sync 入口门禁补查来源服务 service_config 注册+启用状态（用户定案——服务停用/注销即四通道一起断，内部来源不可达无需豁免分支）；type-definition/remove 补「类型下有有效行则拒删」守卫（20056）；API 类型禁止声明 SYNC（保存校验 20044）；syncSourceService 禁止首尾空白（trim 不对称锁死类型防护）；管理面 remove 补树写锁（§17.1 入口列表）；投影 upsert parentId=null 走 UpdateEntity 强制清列；契约 last_reviewed 与全部残留失实表述修正"
  - "内部来源统一（2026-09-05 补充定案，用户字段落清理追问驱动）：USER/ORG/MENU/ROLE 四类型种子声明 SYNC+syncSourceService=access-service（schema 种子 UPDATE；声明校验豁免——内部来源仅 is_system 预置类型可声明、不要求 service_config 注册行；20055 message 特化指向事实链路管理入口）；**收编删除三套旧机制**——类型保留清单（create/batch-create 拦截 + LocalProjectionOwner.isReservedResourceType + LocalProjectionGuard.rejectReservedResourceType）、行级投影防线（update/move/remove 与 sync 入口的 rejectIfLocalResource、投影 upsert 的 rejectIfForeignResource、投影禁用/删除的 isOwnResource 过滤），外部同步对四类型从「不撞投影行即放行」收紧为一律入口拒绝；`resource_entity.sync_key` 列删除（DDL + 实体字段 + 两写入方 + 契约 §6.3/§10-14 失实描述修正）；`owner_service_code/maintain_source` 两列保留（读取面收敛为 service-config 通道行归属判定——uk 不含 owner，重复防护由 uk 承担、列只做撞码时归属判定；删除需重构该通道为按服务拆类型，另议）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-05
---

# T-PERM-052 资源类型级所有权边界——类型声明门禁（sync 独占 + 管理面只读 + 声明变更守卫）

> 状态：done（2026-09-05 设计体检 P1 立项为行级方案，同日执行时经用户四项定案改定为类型级所有权方案后实施收口；经双轨子代理与 codex 外部评审三轮收口后终态 access-service 全量 1053 单测 + 151 容器测试全绿、gateway 93 单测 + E2E 6/6 全绿）
> 依赖：无硬依赖；错误码与 T-PERM-046 取号协调（本卡占 20055/20056）

## 设计口径（2026-09-05 用户定案）

原行级方案（sync_metadata 反查接管拒绝 + maintain_source 行级白名单 + FULL diff 归属核验）在执行时被用户改定为**类型级所有权**模型，四项定案：

1. **声明载体 = type_definition.extra JSONB 约定**（非新列/独立表）：`managedMode` + `syncSourceService` 两键；
2. **mode 值域 = 两态 MANAGED(缺省)/SYNC**：SYSTEM 语义由既有 is_system 承载，不设第三态；
3. **syncTypes 移除资源维度**：resource-entity 通道唯一门禁 = 类型声明，service_config.extra.syncTypes 只剩 subject/role/source 三维；
4. **无有效行才可改**：类型下存在有效资源行时声明有效值不得变更（含删键隐式切回 MANAGED）；is_system 系统预置类型一律钉死不可变更（codex 二轮复评 P1-1 定案，2026-09-05）。

两个混合场景的用户答案（推翻 T-ACCESS-018「公共类型外部同步合法」口径）：

- 外部服务同步用户 → 走自有用户类型（主体通道自有类型 + 资源通道自有类型），不动公共 `USER`；
- 菜单归本系统管理则同用户口径；其他服务的菜单走自建类型（如 `BI_MENU`）。

模型不变式：每个 resource_type 类型单一所有权——MANAGED=管理面维护（SERVICE/API 等非事实链路公共类型属之）/ SYNC=声明来源服务独占同步且管理面只读。双向越界（sync 接管人工行、管理面改/删同步行）在类型层结构性消除。`maintain_source` 降级为记录值（'SYNC' 登记进 schema 注释），判定不依赖该列。

**内部来源统一（2026-09-05 补充定案，用户字段落清理追问驱动）**：USER/ORG/MENU/ROLE 四类事实链路类型种子声明 `SYNC + syncSourceService=access-service`——「这些类型也是同步（内部服务同步）」，两态模型本可表达。三套旧机制收编删除：保留清单、行级投影防线（rejectIfLocalResource/rejectIfForeignResource）、投影 isOwnResource 过滤；投影写入方成为四类型唯一合法 writer。`owner_service_code/maintain_source` 保留（service-config 通道行归属判定：uk 防重复、列做撞码归属），`resource_entity.sync_key` 删除（写-only 死列）。

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
- 本地投影链路的写入逻辑不动（其行级防线 rejectIfLocalResource/rejectIfForeignResource/isOwnResource 已随内部来源统一批次收编删除，见设计口径）；
- 前端类型定义页/资源页不改造（extra 为 JSON 编辑、20055 走统一错误提示；类型声明管理面增强另行评估）。

## 已知边界（登记，另行评估）


- **sync 通道跨类型父子边仍合法**（parentResourceTypeCode 可与 item 类型不同）：本卡以 remove 级联全集守卫防御，是否收紧为同类型父边另行评估；
- **SYNC 类型声明来源服务被删除后**类型声明残留（service-config remove 不级联类型声明）：该类型同步入口持续拒绝（fail-closed 方向），人工清理类型声明即可；
- **SERVICE/API 类型的固定图种子行维持 MANAGED**（手工 CRUD 现状允许，靠启动固定图校验保护）——是否也声明内部来源收紧另行评估；
- **存量库迁移与硬性发布顺序**（dev 库可重建，DDL 为准）：`UPDATE type_definition SET extra='{"managedMode":"SYNC","syncSourceService":"access-service"}' WHERE tenant_id=1 AND type_key='resource_type' AND type_code IN ('USER','ORG','MENU','ROLE');` + `ALTER TABLE resource_entity DROP COLUMN IF EXISTS sync_key;`（dev 库 sync_key 全 NULL 零风险）。**必须先跑 UPDATE 再上线代码**（fail-closed 发布顺序，syncTypes 先例）：先代码后 DDL 的窗口内四类型解析为 MANAGED——外部同步侧收紧无害（无 SYNC 声明即拒），但管理面 create 对四类型放行、恢复旧保留清单阻止的投影孤儿行风险；上线前按 `SELECT * FROM resource_entity WHERE resource_type IN (四类型 type_value) AND owner_service_code IS DISTINCT FROM 'access-service' AND delete_flag=0;` 核对存量外部行（dev 库实查为 0；若有，被投影生命周期接管或成永久孤儿，需人工定夺）。

## 完成记录

- 2026-09-05 实施：错误码 20055/20056；ResourceTypeOwnershipGuard 新建（解析/校验/门禁/变更守卫）；ResourceEntityDomainService.hasValidRowsOfType + mapper existsValidByType；TypeDefinitionAppServiceImpl 声明校验+变更守卫；ResourceEntitySyncAppServiceImpl 入口门禁替换白名单；ResourceManageAppServiceImpl 五入口门禁（remove 级联全集）；SyncTypeGuard 资源维度移除；schema/api-contract/architecture/core-flows 回写；回归用例 22 个新增/适配（含旧实现下失败锁），受影响测试类 118 用例全绿，access-service 全量套件通过。
- 2026-09-05 codex 外部评审收口批次（gpt-5.6-sol xhigh 只读评审，2P1+3P2+2P3 七条逐条代码级核实全属实零误报，无存疑项）：P1-1 sync/fullSync 类型门禁移到树锁之后 + type-definition update/remove 涉 resource_type 时持 (resource_entity, 租户) 树锁并锁内重读（peek→lock→reread，T-PERM-044 先例）——堵「门禁放行/行数守卫查零行 → 类型翻转来源/删除 → 资源插入落库」交错破坏单一所有权（双轨评审曾定性为语义混乱非越权，codex 定级 P1 采纳）；P1-2 moveRole 移到根显式清 parent 列（flex update 忽略 null，事实侧残留旧父与投影侧清列分叉——投影清列修复暴露的存量 bug；菜单/组织用 0L 哨兵无同款问题）；P2 类型批删行数检查改一次批量查询（selectDistinctTypesWithValidRows）、投影 parentId 测试改 UpdateWrapper.getUpdates 断言（旧断言在旧实现下也过，luna 实证）、来源长度 64→128 对齐 service_code 列宽；P3 schema service_config.extra 注释退役字段清扫、契约 §6.2.2 门禁 bullet 四类型归属表述修正、任务卡非目标行清理、挂错注释与 FQCN 风格修正。新增回归用例 5 个（含 InOrder 锁序×2、getUpdates 强断言×2、非 resource_type 不持锁）。
- 2026-09-05 双轨子代理评审收口批次（代码正确性与安全边界 + 规范符合性与文档一致性两轨，14 项发现逐条代码级核实；P1×1：resource-entity 通道运行时未校验服务注册/状态（已停用/注销服务仍可同步其声明类型，与三兄弟通道不一致）——用户定案门禁补查 service_config（isSyncEntranceAllowed，注册+未软删+status=1，真实原因仅记内部日志）；用户选全部四项顺手修：remove 补树写锁（§17.1 入口列表同步）/ type-definition/remove 补行数守卫（20056）/ API 类型禁 SYNC 声明（service-config 通道是其事实 writer）/ 投影 upsert parentId=null 走 UpdateEntity 强制清列；事实性小修：来源首尾空白保存拒绝（trim 不对称会锁死类型）、损坏 extra 回落方向措辞修正（对同步 fail-closed、对管理面可恢复）、batch-create 20055 回归锁补齐、existsValidByType 注释修正、契约 last_reviewed/两处 sync_key 残留枚举/core-flows syncKey 句/admin 契约终态注记取代标注/任务卡矛盾句与计数修正；新增回归用例 10 个）。
- 2026-09-05 内部来源统一批次（同日补充定案）：四类型种子声明 + is_system 内部来源豁免 + 20055 message 特化；删除保留清单/行级投影防线/isOwnResource 过滤（LocalProjectionGuard 三个资源侧方法与 LocalProjectionOwner.isReservedResourceType 移除）；resource_entity.sync_key 列删除（实体字段 + ResourceEntitySyncAppServiceImpl/ResourceSyncHandlerImpl 写入点）；契约 §5.1/§6.2.2/§6.3/§10-14、architecture §4.3、core-flows、admin-service-api-contract、AGENTS.md 同步改写；测试适配（行级投影防线组替换为内部来源门禁用例）。
- 2026-09-05 codex 二轮复评收口批次（同模型同提示词复评全量变更 + 上轮修复，额度中断后 resume 续跑；3P1+2P2+2P3+1存疑 逐条核实零误报，3 项设计取舍经用户定案）：P1-1 投影/事实链路写入不校验类型所有权（空类型翻转声明后单一 writer 被顺序破坏，无需并发）→ 用户定案「钉死系统预置类型」：is_system 类型所有权声明一律不可变更（20056，先于行数判定；投影代码零改动，顺序+并发窗口一并关闭，先例=is_system 禁删）；P1-2 create/batch-create 未参树锁 → 门禁/类型解析/插入前持 (resource_entity, 租户) 树锁（§17.1 入口列表同步）；P1-3 gateway E2E（T-API-001 ExampleProtectedApiE2EIT）被退役入口确定性破坏 → 用户定案本轮迁移：seed 去掉 syncTypes.resourceTypeCodes、第③步迁 service-config/sync（FULL 接口声明一步建 API 资源+映射，替代直连 resource-entity/sync + 手工建映射），T-API-001 卡追加更替注记；P2-1 保存校验与运行时来源准入不一致（status=1 缺失、admin-service 保留来源可声明=无人可写锁死类型）→ 同规则对齐（注册+未软删+status=1、保留内部来源拒绝）；P2-2 fullSync 拒绝用例与类型删除用例补锁序/锁内重读 InOrder 断言（上轮修复的回归锁缺口）；P3 文档矛盾 5 处清扫（architecture §4.3 行级防线残留与 USER/MENU 归属例、lifecycle 197、admin 契约 952、schema 1014 注释、本卡 52 行）+ 本任务新增代码 FQCN 6 处清理；存疑定案：已知键显式 null 保存拒绝（20044）、未知键开放（extra 通用扩展位，javadoc「防拼写错误」措辞订正）。新增回归用例 5 个（is_system 钉死 guard+AppService 两级、create/batch-create 锁序、fullSync 锁序、类型删除 peek→lock→reread→守卫完整序）。
- 2026-09-05 codex 三轮复评收口批次（收敛验证轮，2P1+2P2+5P3 无存疑，逐条核实零误报，P1-2 修法经用户定案）：P1-1 type-definition/create 未参树锁（管理面门禁对「类型不存在」放行→并发建 SYNC 类型→在途资源插入落库破坏单一所有权）→ resource_type 创建持锁，锁先于首次类型读取（§17.1 入口列表同步，顺带封闭并发同码建类型查重窗口）；P1-2 类型值缓存陈旧（TYPE_VALUE/TYPE_CODE 均 10s L2 且类型删除链路零失效——零行删类型后 10s 内创建同名资源，门禁库内直查放行、类型解析命中陈旧缓存落库成引用不到有效 type_definition 的孤儿行；删建同码还挂旧值）→ 用户定案双管齐下：类型 create/delete 提交后 evictAfterCommit 双向缓存键 + 管理面 create/batch-create 改消费门禁库内直查返回的权威类型行（guard 两个门禁方法返回 TypeDefinition，写路径不再经类型缓存，类型不存在当场 fail-closed 20021）；P2 batch-create 锁序用例假阳性修复（批量段前 clearInvocations——旧断言可被单创建段锁满足；engine 入序钉「权限→锁→门禁→插入」）+ is_system 钉死正向边界用例（同声明重提/仅改非声明字段放行，防 isSystem 判定误移到相等比较前）；P3 活文档退役链路清扫（architecture §1.4/§4 模块表、example-service 接入路径）、上轮引入的错误码表述订正（同步入口拒绝=RESOURCE_TYPE_OWNERSHIP_DENIED 同步拒绝响应而非 20055；20055 限管理面 CRUD、20056 限声明变更/删除冲突）、schema resource_entity 表注释行级防线残留改「记录列」口径、任务卡计数 1049→1053、FQCN 残留与 import 顺序修正。新增回归用例 5 个（类型缺失 fail-closed 陈旧缓存 lenient 桩、createType 锁序+非 resource_type 不持锁、钉死正向边界、批量码→权威行映射、create/delete 失效断言）。
