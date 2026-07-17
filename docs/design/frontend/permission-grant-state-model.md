---
doc_type: design
title: 权限授予 状态模型
status: draft
domain: frontend
last_reviewed: 2026-07-17
---

# 权限授予 状态模型

> 本文为 [`permission-grant.md`](./permission-grant.md) 的状态机补充文档，不重复布局/字段/视觉设计，仅定义状态、事件、转换条件、UI 状态与不变量。
>
> 依据实现：`frontend/src/views/system/permission-grant/`（hook.ts / grant-task.ts / summary.ts / types.ts re-export + `@/utils/permission-grant-types.ts` / `components/GrantDialog.vue`）。
> 后端语义：`docs/design/permission-center/overview.md`（资源依赖 / 冲突规则 / 缓存一致性）、`implementation.md` §2.2/§2.4。
> 覆盖需求：未配置/允许/拒绝/继承/覆盖/冲突、权限依赖、单项与批量修改、未保存/保存中/保存失败、并发修改与版本冲突。

## 0. 模型分层与正交维度

页面状态不是单一状态机，而是 **6 个正交维度** 的并发组合。任一时刻的 UI = 各维度状态的笛卡尔投影。分开建模是因为它们生命周期不同、转换条件独立。

| 维度 | 范围 | 生命周期 | 关键状态 |
|---|---|---|---|
| D1 页面门控 | 全页 | 进入页面即定 | NO_VIEW / VIEWABLE |
| D2 角色上下文 | 当前角色 | 选角色到切角色 | UNSELECTED / LOADING / READY / LOAD_FAILED |
| D3 编辑能力 | 当前角色 | 跟随 D2 + 能力 | EDITABLE / READONLY(+reason) |
| D4 权限单元有效态 | 每个资源×操作单元格 | baseline+task 投影 | 见 §1 |
| D5 草稿-保存生命周期 | 全页草稿 | 选角色到保存完成 | CLEAN / DIRTY / SAVING / SAVE_FAILED / SAVE_OUTCOME_UNKNOWN / STALE（+ STALE_WITH_CHILD_FAILURE 组合态）；SAVE_FAILED 四子态见 §2.1 |
| D6 授权弹窗 | 弹窗实例 | 打开到关闭 | CLOSED / GRANT / ADJUST × step0–3 |

**单一事实源**：`baseline`（服务端事实）+ 有序 `grantTasks[]`（本地任务快照）-> `replayGrantTasks()` 纯函数投影 -> `mainDraft`/`childDraft` -> diff -> 中栏摘要 / 右栏变更 / 弹窗预填。三栏不允许维护第二份事实。`failedChildren` 作为 overlay 叠加在 childDraft 之上（投影顺序 baseline -> grantTasks -> failedChildren）。

## 1. 权限单元有效状态机（D4）

对应需求中的 **未配置 / 允许 / 拒绝 / 继承 / 覆盖 / 冲突**。采用 **正交两维** 建模（而非单一枚举），因为同一单元格可同时是"被 ALL 覆盖"且"有移除草稿"。

### 1.1 有效主状态 `SummaryEffective`（R6 优先级）

优先级 **ALL覆盖 > 直接(含条件) > 操作继承(INHERITED) > 派生(DERIVED) > 未授权**；"直接无条件"与"直接有条件"互斥，不参与排序。

| 状态 | 对应需求词 | 含义 | 可编辑 | 来源 |
|---|---|---|---|---|
| `UNAUTHORIZED` | 未配置 | 当前角色无直接记录且无有效覆盖 | 是（可发起授予） | 单元格无计划态分支 |
| `DIRECT` | 允许 | 直接持有、无条件 | 是 | 单元格计划态分支含无条件分支（conditionCode=null） |
| `CONDITIONAL` | 允许（受限） | 直接持有、绑定条件 | 是 | 单元格仅有条件分支（conditionCode≠null） |
| `ALL_COVERED` | 覆盖 | 实例单元被同操作的 ALL 权限覆盖 | 视直接记录而定 | INSTANCE + ALL cell 有有效分支 |
| `INHERITED` | 继承（操作位） | 被同角色其他操作的 `effectiveBits` 覆盖（如 MANAGE 覆盖 VIEW）；无直接记录但运行时有效 | **否（只读）** | baseline 无此键、但同资源有 effectiveBits 覆盖此操作的其他操作授权 |
| `DERIVED` | 派生 | 角色组合(COMPOSED)、资源依赖(AUTO_DEP)或资源继承展开(grantSource=INHERITED)产生 | **否（只读）** | grantSource≠MANUAL |
| `NOT_GRANTABLE` | 拒绝（授予） | 操作者缺转授权能力，不可新增 | **否（仅可回收既有）** | isGrantableByOperator=false |

> **P1-3 修正**：`INHERITED` 显式建模操作位继承。后端 `OperationPermission` 有 `binaryBit`+`inheritMask`，`OperationPermissionUtils.effectiveBits` 动态计算一个操作授权覆盖的全部操作位（MANAGE 授权运行时使 VIEW/CREATE/UPDATE/DELETE 均有效）。授权页 baseline 仅含直接授权记录，若不建模 `INHERITED`，被覆盖的操作单元格会显示为 `UNAUTHORIZED` 并允许创建冗余直接授权。`INHERITED` 只读，撤销语义为"移除来源操作授权"（如移除 MANAGE），而非创建直接记录。
>
> **精确建模依赖后端**：当前前端 `OperationItem.inheritMask` 仅摘要字符串，无法精确判定"VIEW 被 MANAGE 覆盖"。需 T-PERM-034 返回结构化操作继承关系（operationCode -> 被其 effectiveBits 覆盖的 operationCode 集），前端据此对 baseline 内已有授权做 effectiveBits 反查，标记被覆盖单元格为 `INHERITED`。在此之前 `INHERITED` 态不可达（同 `DERIVED`）。

### 1.2 草稿副状态 `SummaryDraftChange`（正交）

| 副状态 | 含义 | 触发 |
|---|---|---|
| `null` | 与 baseline 一致 | - |
| `ADD` | 待新增（baseline 无、draft 有） | grant 任务确认 |
| `REMOVE` | 待移除（baseline 有、draft 无） | remove 任务 / 调整弹窗移除 |
| `MODIFY` | 属性变更（conditionCode/canGrant 变） | adjust 任务 |

### 1.3 正交标记

- `allCovered`：INSTANCE 单元被 ALL 覆盖（**只看计划态 mainDraft，不 OR baseline**——ALL 被草稿移除后实例不再显示覆盖，三栏一致）
- `hasBaselineDirectRecord`：baseline 是否有该 INSTANCE 直接记录（副标记"● 有直接记录"依据）
- `grantableByOperator` / `denyReason`：操作者授予能力（fail-closed）

### 1.4 关于"拒绝"与"冲突"的澄清（重要）

本系统是 **允许模型（allow-list）**，没有显式 deny 权限：

- **拒绝** 有两种语义，不可混淆：
  - "拒绝授予" = `NOT_GRANTABLE`：操作者没有对该资源×操作的转授权能力，是**编辑门控**，不是运行时拒绝。
  - "运行时拒绝访问" = 由 `permission_conflict_rule`（角色互斥/权限互斥）在 `PermQueryEngine` 查询管线评估时抑制有效权限。**这不在授权页草稿态内**。
- **冲突** = `permission_conflict_rule`，是 **运行时查询侧** 概念，不是授权草稿的一个状态。授权页仅在 [`permission-grant.md`](./permission-grant.md) §9.2 保存前校验 把"将产生冲突/冗余"列为 **警告类**（不阻断），冲突的实际评估和展示归 `conflict-rule` 管理页与 `permission-query` 排查页。因此 D4 不设独立的 `CONFLICT` 有效态。

**继承**有两种语义，已分别建模，不可混用：

- **操作继承**（`INHERITED` 有效态）：同一角色其他操作的 `effectiveBits` 覆盖本操作（如 MANAGE 覆盖 VIEW）。**无独立授权记录**，是运行时投影；授权页据 effectiveBits 反查 baseline 标记，只读，撤销 = 移除来源操作授权（如移除 MANAGE）。精确建模依赖 T-PERM-034 返回结构化操作继承关系。
- **派生**（`DERIVED` 有效态）：跨角色或跨资源产生**独立记录**，`grantSource≠MANUAL`（COMPOSED 角色组合 / AUTO_DEP 资源依赖补全 / INHERITED 资源继承展开克隆）。只读，撤销需去来源角色/规则页。

两者都只读，但来源与撤销路径不同：操作继承撤销本角色的来源操作授权；派生撤销需跳转来源角色/依赖规则页。

### 1.5 单元格 6 态（CellState，底层视图，**单个 GrantVariantId 的转换**）

D4 的有效主状态由底层 `CellState` 6 态 + 正交标记派生。转换通过 **任务原子操作**（非直接 toggle，§16 已移除中栏直接编辑）：

```
                 grant task
    UNAUTHORIZED ─────────────► PENDING_ADD
         ▲                          │
         │ cancel/revoke task       │
         │◄─────────────────────────┘
         │
         │ remove task
         │   (baseline 有键时)
         ▼
      GRANTED ─────adjust(属性)─────► MODIFIED
         │  ▲                          │
         │  │ revert attr              │ remove task
         │  │◄─────────────           │
         │ remove task                 ▼
         ▼                        PENDING_REMOVE
      PENDING_REMOVE ◄──restore──── GRANTED
         │                          ▲
         │                          │
         └────► (保存后回 baseline)  │
                                    │
   INSTANCE + allKey 在 draft ──► ALL_COVERED（正交，可与其他态共存标记）
```

| 转换 | 条件 | 动作 |
|---|---|---|
| UNAUTHORIZED->PENDING_ADD | grantableByOperator 且 grant 任务确认 | commitGrantTask(intent=grant) |
| PENDING_ADD->UNAUTHORIZED | 撤销该任务 | removeGrantTask |
| GRANTED->PENDING_REMOVE | remove 任务（或 adjust 弹窗移除） | commitGrantTask(intent=remove) |
| GRANTED->MODIFIED | conditionCode 或 canGrant 变 | commitGrantTask(intent=adjust) |
| MODIFIED->GRANTED | 撤销属性修改（replace 回原值 或 remove task） | replaceGrantTask / removeGrantTask |
| MODIFIED->PENDING_REMOVE | 移除 | commitGrantTask(intent=remove) |
| PENDING_REMOVE->GRANTED | 恢复 | removeGrantTask（撤销 remove） |
| `*`->ALL_COVERED 标记 | 同操作 ALL 键进入 draft | 由 grant ALL 任务驱动（正交，不改主态） |

> **INHERITED 撤销语义**：`INHERITED` 单元格只读，不可直接授予/移除；其"有效"来自同角色其他操作的 effectiveBits 覆盖。撤销 `INHERITED` 有效权限 = 移除来源操作授权（如移除 MANAGE 使 VIEW 回到 UNAUTHORIZED），而非创建/移除本操作直接记录。该态精确建模依赖 T-PERM-034，当前不可达。

> 注：`ALL_COVERED` 作为 `CellState` 是"未授权且被覆盖"；作为正交 `allCovered` 标记可与 GRANTED/PENDING_REMOVE 共存（R6："★ ALL 覆盖 + ● 有直接记录" / "★ ALL 覆盖 + － 移除直接记录"）。

## 2. 草稿-保存生命周期状态机（D5）

对应需求中的 **未保存 / 保存中 / 保存失败 / 并发修改 / 版本冲突**。

```
                  selectRole 成功
        UNSELECTED ─────────────► CLEAN
            │                        │
            │                    grant/adjust/remove task
            │                        ▼
            │                     DIRTY ◄────────────┐
            │                        │               │
            │                    saveAll()           │ retry
            │                        │               │
            │                        ▼               │
            │                     SAVING ────────────┘ (非 stale)
            │                    │  │  │
            │     全成功+reload OK│  │  │ reload 失败
            │              ▼      │  │  ▼
            │            CLEAN    │  │  STALE ──reloadOK──► DIRTY(重试)
            │          (保存成功)  │  │
            │                       │  │ 主请求失败
            │     子权限部分失败    │  ▼
            │      (主已存)         │ SAVE_FAILED
            │         ▼             │ (main_failed:
            │      SAVE_FAILED       │  草稿保留)
            │   (child_partial:     │
            │    failedChildren     │
            │    overlay 保留)       │
            └────────────────────────┘
```

| 状态 | 含义 | 进入条件 | 退出条件 | UI |
|---|---|---|---|---|
| `CLEAN` | 无草稿，与 baseline 同步 | 选角色成功 / 保存成功重载 / discardAll | 任何 task 提交 | 保存按钮禁用，提示"已同步" |
| `DIRTY` | 有未保存草稿（allDiff.length>0） | commitGrantTask / failedChildren overlay 存在 | saveAll 开始 / discardAll / removeGrantTask 清空 | 保存按钮可用，离开保护激活 |
| `SAVING` | 保存请求进行中 | saveAll 通过门禁后 `saving=true` | 请求返回（成功或失败） | **三栏全只读**，保存按钮 loading，禁用切换角色/域/任务操作 |
| `SAVE_FAILED` | 保存失败（业务拒绝或子部分失败） | 见 §2.1 分支 | retry / discardAll / reload | 见 §2.1 |
| `SAVE_OUTCOME_UNKNOWN` | 保存请求超时/断网，服务端可能已提交但响应未返回 | save 网络中断（E17b/E20） | fetchBaseline+reconcile 后 -> CLEAN/DIRTY | 矩阵置灰 + "正在确认保存结果…"，禁止盲目重试 |
| `STALE` | baseline 过期，阻止继续保存 | reloadBaseline 失败 -> `baselineStale=true` | reloadBaseline 成功 | 保存禁用，提示"权限事实已过期，重新选择角色刷新" |

> **P1-3 组合态 `STALE_WITH_CHILD_FAILURE`**：`childFailure`（E16，failedChildren 非空）与 `stale`（E18，baselineStale=true）正交，可同时发生。UI 须同时呈现"先刷新事实"+"刷新后重试子项"，恢复顺序固定：先 fetchBaseline+reconcile 清 stale -> 再 retry 清 childFailure。原子恢复流程见 [`permission-grant-error-flow.md`](./permission-grant-error-flow.md) §2.5。

### 2.1 SAVE_FAILED 的四个子分支（决策点 5）

| 子态 | 条件 | 草稿/overlay 处理 | 恢复路径 |
|---|---|---|---|
| `CHILD_PARTIAL_FAILED` | 主权限 save 成功，部分 add-child/remove-child 失败 | `failedChildren` overlay 保留；`saveError` 提示"主权限已保存，部分子权限保存失败" | `retryFailedChildren` |
| `MAIN_FAILED` | saveRolePermission 抛错（未到 add-child） | 恢复原 `failedChildren` 快照（failedSnapshot），草稿不变 | 修正后 saveAll |
| `SAVE_OUTCOME_UNKNOWN` | save 请求超时/断网，服务端可能已提交但响应未返回（E17b/E20） | **不盲目重试**；snapshot 期望投影 + failedChildren 快照保留 | 先 fetchBaseline+reconcile（见 error-flow §2.5 P1-1），再续传未落库项；全落库->CLEAN，部分未落库->DIRTY |
| `RELOAD_FAILED` | 保存已提交但 reloadBaseline 失败 | `baselineStale=true`，保存已落库但本地事实陈旧 | 重新选角色刷新（进入 STALE） |

> `saveAll` 返回 `Promise<boolean>`：`true`=已开始保存（通过门禁），`false`=前置门禁拦截（只读/saving 中/stale）。区分"未开始"与"开始后失败"，避免重试入口丢失失败元数据（第六轮 P2 修复）。

### 2.2 并发修改与版本冲突（关键澄清 + 已知缺口）

**当前实现状态**：

| 机制 | 是否实现 | 说明 |
|---|---|---|
| 离开保护（路由） | ✅ | `onBeforeRouteLeave`：有草稿时弹确认 |
| 离开保护（刷新/关闭） | ✅ | `beforeunload`：有草稿拦截 |
| 保存期间只读 | ✅ | `saving` 门禁，防重复提交与草稿变化 |
| baseline 过期检测（reload 失败） | ✅ | `baselineStale` 阻止继续保存 |
| **乐观锁 / configVersion / updatedAt** | ❌ **未实现** | [`permission-grant.md`](./permission-grant.md) §9.3 与 §13.2 缺口 9 明确登记为暂缓 |
| **多人并发编辑检测** | ❌ **未实现** | 无版本字段，无法识别"他人已改" |
| 保存前冲突/冗余警告 | ⚠️ 部分 | §9.2 列为警告类（ALL 覆盖实例、级联删子权限、自动补全），但 conflict_rule 实际评估在后端查询侧 |

**当前并发模型**：`save` 是 `add/update/remove` **增量补丁**（同事务），非整份快照替换，因此**不是整角色 last-write-wins**。并发冲突按**稳定权限键**逐键判定，详表见 [`permission-grant-error-flow.md`](./permission-grant-error-flow.md) §2.4：

- A、B 改动**互不相交键** -> 自然合并，无冲突。
- A、B **同键 update**（如都改 conditionCode） -> B 后写覆盖 A（last-write-wins，仅此键）。
- A 新增键 X、B 基于旧 baseline 也新增键 X -> **取决于 conditionCode**：相同条件触发唯一约束 B 回滚；不同条件（condition_id 不同）**不冲突，两条并存**（见下条模型差异）。
- A 移除键 X、B update/remove 键 X -> B 命中 0 行，无操作或 not found。
- A 移除键 X、B 新增键 X -> X 被重新创建。

> **P1-2 多条件授权模型（已决策：选项 A，允许同键多条件）**：后端 `uk_role_resource_permission` 唯一索引含 `condition_id`（+granted_bits+grant_source+depend_on），同一稳定键下**多个条件记录并存**。这与后端运行时 OR 语义一致：查询引擎逐条评估条件，满足任一即保留（`PermissionConditionDomainServiceImpl`）；Gateway 快照按 `(resourceEntityId, conditionId)` 保留多条 OR 记录（`SnapshotAssembler`）；查询 API 返回全部记录，更新/撤销以权限 `id` 为粒度（`PermissionGrantAppServiceImpl`）。前端模型须对齐：
>
> 1. **OR 分支语义**：同一单元格（PermCellKey）的多条授权是独立 OR 分支；`conditionCode=null` 是无条件分支（始终生效）。运行时有效 = 任一分支条件满足。
> 2. **PermCellKey 六维不变**：`domainCode+resourceTypeCode+scopeMode+resourceCode+codeType+operationCode` 仅作矩阵单元坐标和能力查询键，**不含 conditionCode**。
> 3. **身份与存储**：授权事实以服务端 `id` 为身份；新增草稿用临时 UUID。采用规范化存储 `Map<GrantVariantId, DraftPermission>`（GrantVariantId = 服务端 id 或临时 UUID），另建 `Map<PermCellKey, GrantVariantId[]>` 索引，比直接维护可变数组更稳。
> 4. **展示与编辑**：单元格聚合展示（符号反映"任一分支有效"），展开后逐条件分支编辑/撤销。**重复条件处理（确定行为）**：提交草稿前前端按 `PermCellKey + 规范化 conditionCode`（null 视为无条件分支键）检测同单元格同条件重复分支，**就地阻断**并提示"该条件分支已存在"；后端唯一约束冲突作为**并发兜底**（他人刚创建同分支），整批回滚时映射回对应分支提示用户刷新后重试。
> 5. **子权限 dependOn**：必须关联**具体父授权变体**（GrantVariantId），不能只关联六维 PermCellKey（因一单元格可有多条父变体）。
> 6. **canGrant**：保留为每条授权记录的属性，不在前端压平覆盖（不同分支可有不同 canGrant）。
>
> 当前后端已支持多条件；前端 `Map<PermCellKey, DraftPermission>` 单条件模型为**待重构缺口**，落地前须按上述模型扩展。

- `baselineStale` **仅**覆盖"reload 请求本身失败"的场景，**不能**检测"他人已成功修改"（需 configVersion，见缺口）。

**状态模型中需显式建模的目标态**（当前为缺口，建议补）：

```
STALE 之外应增加：
  CONCURRENT_MODIFIED（他人已修改，本地 baseline 过期但本地草稿仍有效）
    进入条件：保存时后端返回版本不一致（需 configVersion/updatedAt 契约）
    退出条件：用户选择「合并」/「用我的覆盖」/「丢弃本地重新加载」
    UI：停止覆盖，提供 diff 对比 + 三选一
```

在 T-PERM-034 补 `configVersion` 前，此态 **不可达**；当前用 STALE + 离开保护近似兜底。这是状态模型里有"洞"的地方，应在文档中标注为 **不可达但已预留**。

## 3. 授权弹窗内部状态机（D6，单项/批量统一模型）

对应需求中的 **单项修改和批量修改**。两者用 **同一弹窗 + 同一任务快照模型**，仅初始状态不同（R11 收敛）。

### 3.1 弹窗顶层态

```
              open-grant(payload)            open-adjust(payload)
CLOSED ──────────────► GRANT_MODE        ──────────────► ADJUST_MODE
   ▲                      │                                   │
   │  close/cancel        │ onConfirm                         │ onConfirm
   │  (新建取消=无草稿)    ▼                                   ▼
   │              commitGrantTask(grant)           commitGrantTask(adjust)
   │              emit committed+close             emit committed+close
   │                                                         │
   │                  onRemove (仅 ADJUST)                    │
   │                      ▼                                   │
   │              commitGrantTask(remove)                     │
   └──────────────────────────────────────────────────────────┘
```

| 态 | 触发 | 预填 | 取消语义 |
|---|---|---|---|
| `CLOSED` | trigger=null | - | - |
| `GRANT_MODE` | open-grant | 操作∅、资源∅、条件null（新建授权） | **不产生草稿** |
| `ADJUST_MODE` | open-adjust | 单操作、单资源、原 conditionCode/canGrant | **保留原记录**（编辑取消不丢） |

> 编辑已有任务（T-FE-028）重开弹窗 = ADJUST_MODE 的扩展，`replaceGrantTask` 保持原 taskId/位置/createdAt。

### 3.2 步骤子态（step 0–3，线性 + 可跳过）

```
step0 选择操作 ──next(ops非空)──► step1 选择资源 ──next(res非空 or ALL)──►
step2 条件/canGrant/R11开关 ──next──► step3 子权限(可跳过) ──onConfirm──► commit
   ▲                                                                                  │
   └──────────────────────────── prev ────────────────────────────────────────────────┘
```

| step | 守卫（canNext） | 关键本地态 | 能力门控 |
|---|---|---|---|
| 0 操作多选 | `selectedOps.size>0` | selectedOps:Set | 不可授予操作置灰 + denyReason |
| 1 资源多选 | `isAllScope \|\| selectedResources.length>0` | selectedResourceKeys:Set、resKeyword | 已有/部分已有标识（baseline 统计） |
| 2 条件/转授权 | 恒 true | conditionCode、canGrant、keepDirectWhenAllCovered | supportsCondition/supportsDelegation 归一化（不支持强制 null/false） |
| 3 子权限 | 恒 true（可跳过） | localChildDrafts:Map、expandedChildParents:Set、editingChild | supportsChildren + childResourceTypeCodes；按主权限逐项 |

### 3.3 确认门禁（onConfirm）

按顺序短路：

1. `selectedOps.size>0`，否则回 step0
2. 非 ALL 时 `selectedResources.length>0`，否则回 step1
3. supportsCondition 时 `pickerRef.validate()`（条件缺失/停用阻断）
4. `editingChild===null`（子权限附加设置面板未关闭阻断）
5. `validateTaskContext`（任务上下文与当前角色一致，防过期弹窗提交）
6. buildTask -> commitGrantTask -> emit committed+close

### 3.4 R11 重叠语义（批量与已有授权重叠）

| 情形 | 处理 | 任务效果 effect |
|---|---|---|
| 同 PermCellKey + 同 conditionCode（含都为 null）+ 同 canGrant | 不进草稿 | `noChange` |
| 同 **GrantVariantId**（同 id）canGrant 变化、conditionCode 不变 | 修改该变体 | `update` |
| 同 PermCellKey + **不同 conditionCode**（新条件） | **新增 OR 分支**（不覆盖原变体） | `add`（新变体） |
| 同 PermCellKey + 同 conditionCode + 不同 canGrant | 按 GrantVariantId update 该变体 | `update` |
| 被 ALL 覆盖 + 无直接记录 + 未显式保留 | 默认跳过 | `redundantSkipped` |
| 被 ALL 覆盖 + 无直接记录 + 开启 keepDirectWhenAllCovered | 创建直接记录 | `add` |
| 移除具体变体（按 permissionId/临时 UUID） | 删除该 OR 分支 | `remove` |

> **方案 A 关键**："添加分支"动作产生 **add**（新 GrantVariantId）。编辑**已有分支**的 conditionCode 或 canGrant **保持 GrantVariantId 不变**，执行 **update**（后端按 id 更新 conditionId/canGrant，不级联删子权限）。**禁止用 remove+add 表达条件编辑**--remove 会级联删除该父变体的全部子权限并产生新 id。更新后条件与另一分支重复：前端阻断，后端唯一约束兜底。一条任务快照可同时含 add/update/remove/noChange/redundantSkipped 多种 effect（R11：分组不强制单一类型）。

### 3.5 单项 vs 批量的统一

| 维度 | 单项（adjust） | 批量（grant） |
|---|---|---|
| 操作数 | 1（预填） | N（多选） |
| 资源数 | 1（预填）或 ALL | N（多选）或 ALL |
| 条件/canGrant | 单组 | 一组统一应用全部（多操作需不同条件->提示拆分多条任务） |
| 子权限 | 单组 parentContext | 按主权限组合逐项独立 parentContext（不跨主权限共用草稿） |
| 产物 | 1 个 GrantTaskSnapshot | 1 个 GrantTaskSnapshot（内含 N×M 组合） |

两者**统一**为 `GrantTaskSnapshot`，差异仅在 `intent` 与预填值。这是 [`permission-grant.md`](./permission-grant.md) §16 第二轮重构的核心：弹窗本身就是批量入口，不再有独立"批量模式"。

## 4. 权限依赖状态（横切）

对应需求中的 **权限依赖**。两类依赖，状态行为不同：

### 4.1 资源依赖 `resource_dependency`（自动补全）

- 语义：授权源资源 A 时，后端自动补全 A 依赖的目标资源 B 的权限。
- 前端表现：补全产生的权限 `grantSource=AUTO_DEP`，`SummaryEffective=DERIVED`，**只读**，展示来源规则。
- 状态：当前 normalizer **不产生** DERIVED（R1/R7 待 T-PERM-034 后端提供来源）。§8.3 自动授权预览为 T-PERM-035，Phase 1 仅留入口标注"后端能力未启用"。
- **当前态：DERIVED 不可达（已预留渲染能力）**。

### 4.2 子权限 `dependOn`（主-子挂载）

- 语义：子权限键 = `parentVariantId + "|" + childPermCellKey`，只一层。
- 状态约束：
  - 子权限 **依赖父权限存在**，且 `dependOn` 必须关联**具体父授权变体**（GrantVariantId），不能只关联六维 PermCellKey（多条件模型下一单元格有多条父变体，见 §2.2 P1-2）。replay 时 `if (!main.has(group.parentVariantId)) continue`--父权限未进投影（如被 R11 跳过）时，子权限配置不应用，避免孤儿草稿。
  - 删除主权限 **级联**删除子权限：replay 删 `parentVariantId + "|"` 前缀（仅删该变体的子权限，不影响同单元格其他变体）；保存层沿用 `dependOn ∈ mainRemove` 过滤，不重复 remove-child。
  - 子权限 **完整集合替换**语义：`TaskChildGroup` 存在时 children 为最终期望全集，replay 先删前缀再写入；group 缺失不改；`children=[]` 明确移除全部。
- 保存时序（两步非原子，决策点 5）：
  1. save 主权限 -> 响应回新 id
  2. 匹配新主权限 id：**按 `PermCellKey + normalized conditionCode` 匹配 save 响应记录**（已决策选项1，无后端改动：`GrantAddItem` 不传临时 UUID、响应不回显 client 标识；方案 A 下同单元格不同 conditionCode 是不同分支，响应含六维+conditionCode+id 可唯一匹配；匹配后把该分支前端 GrantVariantId 从临时 UUID 替换为服务端 id）。不采用扩展 API `clientVariantId`（选项2）。
  3. add-child 子权限
  4. 处理子权限 update/remove
  5. reload baseline
- 失败处理：主成功子失败 -> CHILD_PARTIAL_FAILED，保留 failedChildren overlay + 重试（见 §2.1）。

### 4.3 grantSource 来源态（决定可编辑性）

| grantSource | 含义 | 可编辑 |
|---|---|---|
| `MANUAL` | 直接配置 | 是 |
| `AUTO_DEP` | 资源依赖自动补全 | 否 |
| `COMPOSED` | 角色组合产生 | 否 |

## 5. 事件清单与转换条件（汇总）

### 5.1 页面/上下文事件

| 事件 | 守卫 | 转换 | 动作 |
|---|---|---|---|
| `mount` | canView | -> 加载 | loadStaticData + loadRoleTree |
| `selectRole(role)` | 非虚拟根 | hasDraft?->离开确认 -> UNSELECTED->LOADING | load snapshot；成功->READY+CLEAN；失败->旧上下文不变 |
| `switchResourceType(code)` | - | - | reloadResourceContext（不影响草稿） |
| `switchDomain` | - | 同 selectRole 保护 | 重新加载 |
| `leave(route)` | hasDraft | 确认 -> 放行；取消 -> 阻止 | onBeforeRouteLeave |
| `beforeunload` | hasDraft | 拦截 | - |
| `unmount` | - | - | cleanup 移除监听 |

### 5.2 草稿/任务事件

| 事件 | 守卫 | 转换 | 动作 |
|---|---|---|---|
| `open-grant` | !readonly | D6->GRANT_MODE | - |
| `open-adjust` | 有直接记录 | D6->ADJUST_MODE | 预填 |
| `dialog.confirm` | §3.3 门禁全过 | CLEAN->DIRTY | commitGrantTask |
| `dialog.remove` | ADJUST_MODE | CLEAN->DIRTY | commitGrantTask(remove) |
| `dialog.cancel(new)` | GRANT_MODE | 不变（无草稿） | - |
| `dialog.cancel(edit)` | ADJUST_MODE | 保留原记录 | - |
| `editTask(id)` | 任务存在 | DIRTY->DIRTY | replaceGrantTask（保位置/taskId/createdAt） |
| `undoTask(id)` | 任务存在 | DIRTY->DIRTY/CLEAN | removeGrantTask |
| `discardAll` | - | *->CLEAN | clearGrantTasks + 清 overlay + 清 saveError（不清 baselineStale） |

### 5.3 保存事件

| 事件 | 守卫 | 转换 | 动作 |
|---|---|---|---|
| `saveAll` | !readonly && !saving && !baselineStale && hasDraft | DIRTY->SAVING | 捕获 diff -> 清 failedChildren -> 主 save -> add-child -> remove-child -> reload |
| `saveAll` 成功 | reload OK | SAVING->CLEAN | message success |
| `saveAll` 子失败 | childFailureOccurred | SAVING->SAVE_FAILED(CHILD_PARTIAL) | failedChildren overlay + 重试入口 |
| `saveAll` 主失败-业务拒绝 | saveRolePermission 抛业务错 | SAVING->SAVE_FAILED(MAIN) | 恢复 failedSnapshot（服务端未提交，可重试） |
| `saveAll` 主失败-结果未知 | save 网络超时/断网（E17b/E20） | SAVING->SAVE_OUTCOME_UNKNOWN | snapshot 保留，fetchBaseline+reconcile 后续传 |
| `saveAll` reload 失败 | !reloadOk | SAVING->STALE | baselineStale=true |
| `retryFailedChildren` | failedChildren>0 | 见 §2.1 | 非 stale->直接 saveAll；stale->**fetchBaseline+reconcile**（非 reloadBaseline，避免清草稿）->恢复 overlay->saveAll |
| `reloadBaseline` 成功 | - | STALE->CLEAN | 清 grantTasks + overlay + stale（**不用于 STALE_WITH_CHILD_FAILURE**，后者须用 fetchBaseline 保留待重试数据） |
| `reloadBaseline` 失败 | - | ->STALE | baselineStale=true |

### 5.4 单元格能力事件

| 事件 | 守卫 | 结果 |
|---|---|---|
| `isGrantableByOperator(type,op)` | canManage && oc.canManage && type∈grantableTypes && op∈grantableOps | grantable=true；否则 fail-closed + reason |
| toggle 新增 | grantableByOperator | 允许；否则禁用 + 提示 |
| toggle 回收（adjust/remove 既有） | **不检查** canGrant | 允许（canGrant 不约束回收） |

> **P1-1 修正：授权能力为粗筛，非精确校验**。`isGrantableByOperator` 仅用 `grantableResourceTypeCodes`+`grantableOperationCodes` 两个集合判定，但后端 `PermissionGrantDomainService.checkCanGrant(GrantCheckKey(resourceTypeCode, resourceCode, **codeType**, operationCode, scopeAll))` 按 **resourceCode+codeType+scopeAll 精确校验**（实际 Java 代码 `PermissionGrantDomainService.java:88-91`；implementation.md §7.4 的 4 字段定义已过时，实际含 codeType）。当操作者只能转授某 MENU 实例时，前端粗筛会误判同类型所有 MENU 可授予，批量保存被后端整笔回滚。类型/操作集合**最多作为粗筛**，精确能力需 T-PERM-034 返回以稳定权限键索引的逐单元能力，或在 SAVE_PREVIEW 调用批量预校验接口。**稳定键必须包含 domainCode、resourceTypeCode、scopeMode、resourceCode、codeType、operationCode 全六维**（与前端 `permCellKey` 一致），否则同一 resourceCode 在不同 codeType 下会发生能力串格。

## 6. UI 状态映射

### 6.1 D1+D2+D3 页面级

| 组合 | 中栏 | 右栏 | 保存区 |
|---|---|---|---|
| NO_VIEW | 整页 `<el-empty>` 无权 | - | - |
| UNSELECTED | 关联空状态 | 关联空状态 | - |
| READY+READONLY | 只读摘要 + "授权"按钮禁用 + readonlyReason | 只读变更 | 无保存按钮 |
| READY+EDITABLE+CLEAN | 摘要 + "授权"按钮可用 | "已同步" | 保存禁用 |
| READY+EDITABLE+DIRTY | 摘要（含 ＋/－/✎ 草稿标记） | 本次变更分组 | 保存可用 |
| READY+EDITABLE+SAVING | 三栏只读 | 三栏只读 | loading |
| READY+EDITABLE+SAVE_FAILED | 摘要 + 失败项标记 | 失败项 + "重试未完成项" | 保存可用 |
| READY+EDITABLE+SAVE_OUTCOME_UNKNOWN | 置灰 + "正在确认保存结果…" | 确认中 | 保存禁用（禁止盲目重试） |
| READY+EDITABLE+STALE | 提示过期 | 提示重新选角色 | 保存禁用 |
| READY+EDITABLE+STALE_WITH_CHILD_FAILURE | 置灰 + 失败项标记 | "刷新并重试" + 失败项 | 保存禁用（一键刷新并重试） |

### 6.2 D4 单元格摘要（色+符+字三合一，R2）

| effective | draftChange | 符号 | el-tag type | 副标记 |
|---|---|---|---|---|
| DIRECT | null | ✓ | success | - |
| CONDITIONAL | null | ◑ | warning | 条件摘要 |
| ALL_COVERED | null | ★ | primary | "● 有直接记录"（若 hasBaselineDirectRecord） |
| INHERITED | null | ⊙ | info | 来源操作（如"MANAGE 覆盖"，暂不产生，依赖 T-PERM-034） |
| DERIVED | null | ⊕ | info | 来源（暂不产生） |
| * | ADD | ＋ | success | - |
| * | REMOVE | － | danger | - |
| * | MODIFY | ✎ | warning | - |
| NOT_GRANTABLE | null | ⊘ | info | denyReason |
| UNAUTHORIZED | null | · | 无色 | - |

排序权重（R3）：直接/条件/ALL覆盖(0) > 操作继承(1) > 派生(2) > 草稿变更(3) > 不可授予(4) > 未授权(5)；默认显示 3 个 + "+N" popover。INHERITED 点击跳转来源操作（只读，见 interaction §4.1）。

### 6.3 D6 弹窗步骤 UI

| step | 不可授予操作 | 已有资源 | R11 开关 | 子权限面板 |
|---|---|---|---|---|
| 0 | 置灰 + tooltip denyReason | 已拥有资源数 tag | - | - |
| 1 | - | ✓全部已有 / ◑部分已有 | - | - |
| 2 | - | - | 仅 hasRedundantCandidate 时显示 keepDirectWhenAllCovered | - |
| 3 | - | - | - | el-collapse 逐项；editingChild 时阻断确认 |

## 7. 不可能状态（不变量 Invariants）

这些组合在正确实现下 **不可达**，出现即 bug：

### 7.1 单元格层（方案 A：互斥限定同一 GrantVariantId，非 PermCellKey）
- 同一 **GrantVariantId** 既 GRANTED 又 PENDING_ADD（同一变体不能既在 baseline 又是新增）
- 同一 **GrantVariantId** PENDING_ADD + PENDING_REMOVE
- 同一 **GrantVariantId** 的 conditionCode 同时 null 与非 null（互斥）
- ALL scope 单元格 `allCovered=true`（ALL 不会被 ALL 覆盖，仅 INSTANCE 可被覆盖）
- `DERIVED` 且可编辑（派生/自动补全只读）
- `grantSource=MANUAL` 且 `DERIVED`（来源矛盾）
- 子权限键存在但 `parentVariantId` 不在 mainDraft 投影（孤儿——replay `if(!main.has) continue` 防止）

> **方案 A 合法组合（非不可能）**：同一 PermCellKey 可同时含 baseline 分支 + PENDING_ADD 新分支、一分支 ADD + 另一分支 REMOVE、无条件分支 + 条件分支并存。**聚合摘要规则**：单元格有效态按分支聚合--存在无条件分支（conditionCode=null）则 DIRECT；否则存在条件分支则 CONDITIONAL；全部分支待移除则 PENDING_REMOVE；无分支则 UNAUTHORIZED。草稿副状态取最显著（ADD>REMOVE>MODIFY）。

### 7.2 能力层
- `directGrantable=false` 且 EDITABLE
- 虚拟根角色 `__virtual_root_*` 且 EDITABLE
- `conditionCode≠null` 且 `supportsCondition=false`（能力门控归一化防止）
- `canGrant=true` 且 `supportsDelegation=false`（归一化防止）
- `NOT_GRANTABLE` 且进入 `ADD` 草稿（grantableByOperator 检查阻止）；但 `NOT_GRANTABLE` 的既有权限可进入 `REMOVE`（canGrant 不约束回收）

### 7.3 草稿/保存层
- `SAVING=true` 且 `CLEAN`（saving 前必有草稿）
- `SAVING=true` 且可编辑（保存期间三栏只读）
- `baselineStale=true` 且 saveAll 通过门禁（stale 阻止保存）
- `failedChildren` 非空 且 `CLEAN`（overlay 存在意味着有未完成子操作）
- `grantTasks=[]` 且 `failedChildren=[]` 且 `allDiff.length>0`（无任务无 overlay 则无 diff——除非 stale，但 stale 不产生 diff）
- 主请求失败后 `failedChildren` 丢失（MAIN_FAILED 必须用 failedSnapshot 恢复）

### 7.4 任务层
- 同一 `taskId` 出现两次（commit 追加、replace 原位、remove 过滤）
- `replaceGrantTask` 改变 taskId 或数组位置（必须保持原 taskId/createdAt/位置）
- 任务上下文（roleExternalId/roleTypeCode/domainCode）与当前角色不一致时提交（validateTaskContext 阻止）
- `intent=remove` 的任务产生 add-child 请求（remove 不写主权限、级联子权限由保存层过滤）
- 编辑取消（ADJUST_MODE cancel）产生新草稿（必须保留原记录）

### 7.5 并发层（当前实现的不变量）
- 无 `configVersion` 却检测到"他人已修改"（当前无此能力——`CONCURRENT_MODIFIED` 态不可达）
- `STALE` 由"他人成功修改"触发（当前 STALE 仅由 reload 请求失败触发）

## 8. 已知缺口与状态模型预留

| 缺口 | 当前态 | 预留态 | 阻塞任务 |
|---|---|---|---|
| 乐观并发 configVersion/updatedAt | 未实现 | `CONCURRENT_MODIFIED`（保存时版本不一致） | T-PERM-034 / §13.2 缺口 9 |
| 派生/自动补全来源 | normalizer 不产生 | `DERIVED` effective + grantSource=AUTO_DEP/COMPOSED | T-PERM-034（R1/R7） |
| 自动授权预览 | 入口预留 | §8.3「系统将自动补全」只读分组 | T-PERM-035 |
| 右栏任务分组展示 + 任务粒度撤销/编辑恢复 | grantTasks 已建模，UI 未接 | T-FE-028 | - |
| GROUP_ROLE 有效权限展开 | 只读空状态 | 有效来源展开 | T-PERM-034（R7） |
| 冲突规则在授权页的警告 | §9.2 列为警告类，未实装 | 保存前冲突警告态 | conflict-rule 联动 |
| **授权能力粒度（P1-1）** | `isGrantableByOperator` 仅类型/操作集合粗筛，后端按 resourceCode+scopeAll 精确校验，批量保存被整笔回滚 | 逐单元能力字段（稳定键索引） | T-PERM-034；或 SAVE_PREVIEW 批量预校验 |
| **操作继承建模（P1-3）** | `SummaryEffective` 无 INHERITED，被 effectiveBits 覆盖的单元格误显示 UNAUTHORIZED 并可冗余授权 | `INHERITED` 有效态（只读，撤销来源操作） | T-PERM-034 返回结构化操作继承关系 |
| **多条件授权模型（P1-2）** | 前端 `Map<PermCellKey, DraftPermission>` 单条件，后端允许多条件 OR；按键去重覆盖隐藏其他分支 | 已决策 A：`Map<GrantVariantId, DraftPermission>` + `Map<PermCellKey, GrantVariantId[]>` 索引；子权限 dependOn 关联父变体 | 前端模型重构任务 |

## 9. 小结

- **未配置/允许/拒绝/继承/覆盖/冲突** = D4 正交两维（有效主态 + 草稿副态）+ 正交标记；"拒绝"与"冲突"在允许模型下分别落在编辑门控（NOT_GRANTABLE）和运行时查询侧（conflict_rule），**不是**草稿态。
- **权限依赖** = 资源依赖（AUTO_DEP，只读，暂不产生）+ 子权限 dependOn（级联、完整集合替换、两步保存）。
- **单项/批量** = 同一 GrantDialog + 同一 GrantTaskSnapshot，仅 intent 与预填不同（R11 重叠语义统一）。
- **未保存/保存中/保存失败** = D5 六态（CLEAN/DIRTY/SAVING/SAVE_FAILED/SAVE_OUTCOME_UNKNOWN/STALE）+ SAVE_FAILED 四子态（CHILD_PARTIAL / MAIN / SAVE_OUTCOME_UNKNOWN / RELOAD）+ STALE_WITH_CHILD_FAILURE 组合态。
- **并发/版本冲突** = save 增量补丁，按稳定权限键逐键冲突（互不相交键自然合并 / 同键 update 局部 last-write-wins / 同键 add 取决于 conditionCode 是否相同），**非整角色 LWW**；离开保护 + STALE(reload 失败)；真正乐观锁未实现，`CONCURRENT_MODIFIED` 态已预留但不可达。
