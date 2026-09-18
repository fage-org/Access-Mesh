---
doc_type: task
id: T-PERM-068
title: 跨类型父子边收紧——sync/管理面同类型父边门禁 + 父字段缺省同类型回填（Q-007 转出）
status: done
plan: —（Q-007 单卡转出）
domain: permission-center
design_refs:
  - docs/design/access-service-api-contract.md#§19.1
  - docs/design/access-service-api-contract.md#§19.2
  - docs/design/access-service-api-contract.md#§12.1
  - docs/design/engine/implementation.md#§3（判定面闭包止步同类型括注 + 遗留节收口）
  - docs/design/extension-guide.md#§3.4（资源树与父子关系口径）
depends_on: []
blocks: []
acceptance:
  - "定案①（sync 收紧，2026-09-17 用户拍板）：resource-entity/sync 单条与 full-sync 的父边收紧为同类型——parentResourceTypeCode 显式异类型 → item 级 NON_RETRYABLE + reason=PARENT_TYPE_MISMATCH，先于父解析与版本写入（拒绝不推进同步版本，对齐 T-PERM-044 先例）"
  - "定案②（管理面对齐 move）：resource-entity/create 与 batch-create 补与 move 同款跨类型拒绝（业务键父轨 20053 字符串比对）；单条 create 裸 parentId 轨补父存在性（20004）与类型校验（20053）——batch-create 裸 parentId 已有存在性校验，仅补类型比对；跨类型项走 batch-create 既有宽容收集 errors 跳过（2026-09-12 部分成功拍板）"
  - "定案③（父字段缺省语义）：sync 单条/full-sync 的 parentResourceTypeCode 缺省 = item/scope 类型（半传 parentResourceCode 也按同类型解析挂父，兑现契约 §19.2 原意，对齐角色域 full-sync effectiveParentTypeCode 先例）；parentResourceCode 为空则父字段组整体忽略（无父/解挂）；管理面 create 父业务键维持成对显式语义不变（半传=无父造根）"
  - "回归锁（旧实现下失败）：单条 sync 跨类型拒绝且 never applyVersion；单条半传按同类型解析挂父（旧实现静默解挂）；full-sync 跨类型 item 拒绝且同批其余项 applied、版本不推进；full-sync 半传按 scope 类型解析；管理面 create 业务键跨类型 20053 / 裸 parentId 不存在 20004 / 裸 parentId 跨类型 20053；batch-create 跨类型项跳过且其余项成功"
  - "契约回写：§19.1 父资源定位句 + §19.2 父默认值句改写为定案①③语义；§12.1 create/batch-create 补父校验句（对齐 move 20053 口径）；decision-registry 登记定案"
  - "Q-007 收敛：pending-problems 条目随本卡 done 收敛入已收敛索引表"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-17
---

# T-PERM-068 跨类型父子边收紧（Q-007 转出）

## 背景

Q-007（2026-09-13 登记，源自 T-PERM-052 已知边界）：resource_entity 父子边的类型约束各写入口不一致——sync 单条/full-sync 与管理面 create/batch-create 均允许跨类型父边（父只查存在不比对类型），仅 move 拒绝（T-PERM-028，20053）。跨类型边无已知合法用例（角色域 ORG/POSITION 容器树是结构性跨类型，资源域无对应形态）；判定面闭包已按同类型闭合（T-PERM-057）、删除守卫已按跨类型全集防护，未出事，但存在「跨类型子节点在类型树查询下不可见」「create 能造、move 不让移」的口径分裂。附带发现：契约 §19.2「父资源默认与 scope 同类型」在资源域实现未兑现（半传 parentResourceCode 静默解挂），角色域 full-sync 已实现缺省回填（effectiveParentTypeCode）。

## 范围

- `ResourceEntitySyncAppServiceImpl`：单条 doSyncOneInternal 与 fullSync 阶段 A/循环体 C——父字段激活条件改为 parentResourceCode 非空；parentResourceTypeCode 缺省回填 item/scope 类型；显式异类型 NON_RETRYABLE PARENT_TYPE_MISMATCH（先于父解析与 applyVersion）。
- `ResourceManageAppServiceImpl`：createResource（业务键父轨跨类型 20053 + 裸 parentId 轨存在性 20004/类型 20053）、batchCreateResources（业务键父轨跨类型 + 裸 parentId 轨类型比对，走既有 errors 宽容收集）。
- 契约总册 §19.1/§19.2/§12.1 回写；engine/implementation §3 括注与遗留节收口；extension-guide §3.4 口径改写；decision-registry 定案行；Q-007 收敛。
- 评审处置（双轨本地评审，行为面零 P0-P2）：代码注释「sync 通道允许跨类型」8 处措辞对齐（含本批触达文件与 mapper javadoc/XML/守卫/测试注释）；环路拒绝 reason 改用回填后类型码（半传挂父成环不再显示裸 null）；补 characterization 两例（sync 只传 typeCode 不传 code=无父、batch-create 裸 parentId 跨类型跳过）。

## 当前口径

三项定案（2026-09-17 用户拍板，详见 acceptance 定案①②③）：sync 通道收紧为同类型父边（NON_RETRYABLE，不新增信封错误码）；管理面 create/batch-create 同批对齐 move（20053，含单条裸 parentId 补校验）；sync 父字段缺省 = 同类型回填（管理面 create 维持成对显式语义不变）。

边界（不动）：
- 判定面「闭包止步同类型」（T-PERM-057）与删除守卫跨类型全集防护维持——收紧后作为 DB 直写脏数据的纵深防线（TargetModeClosurePgIT 直插造数不受影响）。
- parentResourceTypeCode 不加大写 @Pattern 锁（resource-entity CRUD 面明确不在 T-PERM-066 锁面）。
- batch-create 宽容收集/部分成功语义（2026-09-12 拍板）不变，跨类型项按 errors 跳过。
- 角色域 sync 父语义（ORG/POSITION 结构性跨类型）不在本卡范围。

## 验收对照

见 acceptance；逐条对照：

- 定案①：`ResourceEntitySyncAppServiceImpl` 单条 doSyncOneInternal + fullSync 循环体 C 落地（NON_RETRYABLE/PARENT_TYPE_MISMATCH 先于父解析与版本写入）——回归锁 shouldRejectCrossTypeParent_withoutResolvingOrAdvancingVersion、fullSyncRejectsCrossTypeParentItem_butAppliesOthers（applyVersion times(1)）。
- 定案②：`ResourceManageAppServiceImpl` createResource/batchCreateResources 落地（20053/20004 + 裸 parentId 补校验 + 宽容收集跳过）——回归锁 shouldRejectCreateWithCrossTypeParentBusinessKey、shouldRejectCreateWhenRawParentIdMissing、shouldRejectCreateWhenRawParentIdCrossType、batchCreateSkipsCrossTypeParentItems、batchCreateSkipsCrossTypeRawParentIdItems。
- 定案③：effectiveParentTypeCode 缺省回填（单条/阶段 A/循环体同源）——回归锁 shouldResolveParentByOwnType_whenParentTypeCodeOmitted、fullSyncResolvesParentByScopeType_whenParentTypeCodeOmitted、shouldTreatParentAsAbsent_whenOnlyParentTypeCodeProvided（激活条件钉子）。
- 回归锁：10 个新用例全部先在旧实现下失败（RED 实证 /tmp/tperm068-red.log：恰 8 项失败 + 2 项补锁用例为语义钉子）、实现后全绿。
- 契约回写：§19.1/§19.2/§12.1 + engine/implementation §3 + extension-guide §3.4 + registry 2026-09-17 定案行（三文件 last_reviewed 已 bump）。
- Q-007：随本卡 done 收敛入 pending-problems 已收敛索引表。

## 非目标 / 遗留

- 管理面 create 父业务键半传（只传 code 不传 typeCode）仍按「无父造根」处理——与 sync 缺省回填语义有意不同（管理面为交互式 API，半传视为前端缺陷更安全；本卡不改）。若后续要求两域统一，另行登记。
- sync DTO 父字段无 @Valid 成对约束（半传在服务端语义化为挂父，无需 Bean Validation 层拒绝）。
- 本卡收口全量首跑 `TaskExecutionLeaseConcurrencyTest` 1 失败（takeoverReexecutesWithSameIdempotencyKey，裸 sleep(1200) 压 1s 租约 200ms 余量形态，registry 2026-09-16 已登记该家族不可靠）——隔离复跑两次失败方法轮换、基线（stash）对照与稳定态复验（当前代码 4/4 绿）后定性环境时序抖动、与本卡改动零耦合（失败轨迹在平台租约扫描内部）；重跑全量绿。该类剩余两个裸 sleep 方法未改有界轮询登记 Q-013。

## 完成记录

- 2026-09-17 收口。回归证据：`mvn test -pl access-service -DskipTestcontainers=true` 单测轨道 1263 项 0 失败（/tmp/tperm068-unit.log）；收口全量 `mvn test -T 1C`（含 E2E 八模块）1716 项 0 失败、BUILD SUCCESS（/tmp/tperm068-full2.log，2026-09-17；首跑该文件 1 项时序抖动定性见「非目标 / 遗留」）。双轨本地评审（代码轨+文档轨并行子代理）行为面零 P0-P2；处置：代码注释 8 处「sync 通道允许跨类型」措辞对齐、环路拒绝 reason 改回填后类型码（半传挂父成环不再显示裸 null）、补 characterization 两例、design_refs 补登 implementation §3 与 extension-guide §3.4。
