---
doc_type: design
title: 权限授予 错误流规格
status: draft
domain: frontend
last_reviewed: 2026-07-17
---

# 权限授予 错误流规格

> 本文为 [`permission-grant.md`](./permission-grant.md) 的错误处理补充文档，基于 [`permission-grant-state-model.md`](./permission-grant-state-model.md) 与 [`permission-grant-interaction.md`](./permission-grant-interaction.md) 审查异常与恢复流程。
>
> 依据实现：`frontend/src/views/system/permission-grant/utils/hook.ts`（`saveAll`/`retryFailedChildren`/`reloadBaseline`/`selectRole`）。
> 覆盖：权限依赖冲突 / 继承覆盖冲突 / 批量范围错误 / 并发修改 / 部分保存 / 网络中断 / 离开保护 / 失格，共 8 类场景。
>
> 每项给出五维：**预防 / 反馈 / 数据保留 / 重试 / 恢复**。

## 1. 错误清单（Inventory）

按 8 个场景 + 衍生错误统一编号。`严重度`：🔴阻断 / 🟡警告可继续 / 🟢提示。

| ID | 场景分类 | 错误 | 触发点 | 严重度 |
|---|---|---|---|---|
| E01 | 权限依赖 | 资源依赖补全与用户手动移除冲突 | 保存后后端按 resource_dependency 补全 B，用户曾移除 B | 🟡 |
| E02 | 权限依赖 | 循环依赖触发补全异常 | resource_dependency 存在环（归 resource-dependency 页预防） | 🔴 |
| E03 | 权限依赖 | 子权限父权限未保存成功 | 新主权限 save 失败/未返回 id，add-child 无 parentId | 🔴 |
| E04 | 权限依赖 | 孤儿子权限 | 父权限被 R11 跳过未进投影，子权限配置无父 | 🟡(预防) |
| E05 | 继承覆盖 | ALL 覆盖下创建冗余直接记录 | INSTANCE 被 ALL 覆盖且无直接记录时仍授予 | 🟡 |
| E06 | 继承覆盖 | 移除被 ALL 覆盖的直接记录无效 | 移除直接记录但 ALL 仍生效，用户误以为撤销权限 | 🟡 |
| E07 | 继承覆盖 | 尝试编辑派生权限 | DERIVED（COMPOSED/AUTO_DEP）单元格点击编辑 | 🟢 |
| E08 | 继承覆盖 | GROUP_ROLE 直接配权 | directGrantable=false 角色尝试授权 | 🟢 |
| E09 | 批量范围 | 批量含不可授予操作 | 选择集含 NOT_GRANTABLE 单元格 | 🟡 |
| E10 | 批量范围 | 批量含派生/只读 | 选择集含 DERIVED | 🟡 |
| E11 | 批量范围 | 条件跨操作冲突 | 多操作选择统一条件，但语义需不同条件 | 🟡 |
| E12 | 批量范围 | ALL 与 INSTANCE 混合选择 | 批量同时选 ALL 行与 INSTANCE 单元格 | 🟡 |
| E13 | 批量范围 | 跨资源类型批量 | 试图跨资源类型选择（矩阵单类型约束） | 🟢(预防) |
| E14 | 并发 | 他人已修改（当前不可检测） | A 保存后 B 基于旧 baseline 保存，last-write-wins | 🔴(缺口) |
| E15 | 并发 | 保存中他人修改 | 保存请求 in-flight 时他人提交 | 🔴(缺口) |
| E16 | 部分保存 | 主成功子失败 | saveRolePermission OK，add-child/remove-child 部分抛错 | 🔴 |
| E17 | 部分保存 | 主请求失败 | saveRolePermission 抛错（业务/网络） | 🔴 |
| E18 | 部分保存 | reload 失败 | 保存已提交但 reloadBaseline 抛错 | 🔴 |
| E19 | 网络中断 | 加载阶段中断 | 角色树/资源树/权限事实请求失败 | 🔴 |
| E20 | 网络中断 | 保存阶段中断 | save/add-child/remove-child 网络断 | 🔴 |
| E21 | 网络中断 | 重载中断 | reloadBaseline 网络断 -> STALE | 🔴 |
| E22 | 网络中断 | 浏览器离线 | navigator.onLine=false | 🔴 |
| E23 | 离开保护 | 路由切换有草稿 | onBeforeRouteLeave，hasDraft=true | 🟡 |
| E24 | 离开保护 | 刷新/关闭有草稿 | beforeunload，hasDraft=true | 🟡 |
| E25 | 离开保护 | 切角色/域有草稿 | selectRole/switchDomain 内确认 | 🟡 |
| E26 | 离开保护 | 保存中离开 | saving=true 时导航/刷新 | 🔴(预防) |
| E27 | 失格 | 加载后失去 MANAGE | operatorCapability.canManage true->false（重新加载发现） | 🟡 |
| E28 | 失格 | 角色被禁用 | currentRole.enabled true->false | 🟡 |
| E29 | 失格 | canGrant 被回收 | grantableByOperator 变 false（fail-closed） | 🟢 |
| E30 | 失格 | 保存时后端权限拒绝 | 保存请求返回 SecurityException | 🔴 |
| E31 | 失格 | 会话过期 | 401/token 失效 | 🔴 |
| E32 | 输入校验 | 条件已停用 | 绑定的 conditionCode 在 conditions 中 enabled=false | 🟡 |
| E33 | 输入校验 | 子权限类型不符 SUB_PERM | 子资源类型不在 childResourceTypeCodes | 🟡 |
| E34 | 输入校验 | 空保存 | allDiff 为空时触发 saveAll | 🟢 |

## 2. 分场景深入规格

### 2.1 权限依赖冲突（E01–E04）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E01** 依赖补全冲突 | 保存前总览警告区列出"自动补全将新增 N 项"（T-PERM-035 启用后）；当前 Phase 1 入口标注"后端能力未启用" | 保存成功后 reload baseline，补全项以 DERIVED(⊕) 标记出现，附来源"依赖规则: A->B" | 草稿已落库；补全是后端行为，不可撤销 | 无需重试（非错误） | 若不想要补全：移除源资源 A 授权，补全项在下次 reload 后消失（由后端级联） |
| **E02** 循环依赖 | 归 resource-dependency 页 `CycleCheckDialog` 预防，授权页不直接处理 | 授权页保存时若后端返回循环错误，归入 E17 主失败 | 草稿保留 | 修正依赖规则后重试 | 跳转 resource-dependency 页修复环 |
| **E03** 父权限未保存 | replay `if(!main.has(parentVariantId)) continue` 阻止孤儿；保存层按 **parentVariantId** 解析父 id：已保存分支用 permissionId，新分支按 `PermCellKey + conditionCode` 匹配 save 响应/baseline 得到服务端 id，无法匹配时标记失败 | CHILD_PARTIAL：sheet 失败视图标红子项"父权限未保存，子权限待重试" | `failedChildren` overlay 保留 `{op:"add", child, childKey, parentVariantId}` | `retryFailedChildren`：按 parentVariantId 解析（已保存->permissionId，新分支->按 PermCellKey+conditionCode 匹配 baseline），**不从 `mainBaseline.get(parentKey)` 取任意分支** | 重试成功 -> overlay 清除；重试仍失败 -> 保留草稿，提示重新选角色 |
| **E04** 孤儿子权限 | replay 跳过（父未进投影不应用子权限）；弹窗步骤四 `isRedundantSkipped` 过滤组合；`watch(mainDraft)` 父消失时关闭子编辑面板 | 步骤四该组合不显示；若已展开则自动收起 | 子权限草稿在 `localChildDrafts` 保留，但确认时 `buildChildGroups` 仅含 `expandedChildParents` 中的，未展开不进任务 | 父权限恢复后重新展开配置 | 无需恢复（预防成功则不发生） |

> 关键不变量：**孤儿子权限永不进入投影**（replay 守卫）+ **failedChildren overlay 保留 childKey 供重试**（第六轮 P2 修复）。

### 2.2 继承和覆盖冲突（E05–E08）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E05** ALL 覆盖冗余直接记录 | 单元格就地警告"已被 ALL 覆盖，创建直接记录冗余"+ 二选一"跳过(推荐)/仍创建"；R11 `keepDirectWhenAllCovered` 默认 false，replay `redundantSkipped` | 确认摘要"N 个被 ALL 覆盖且无直接记录的组合将不创建冗余直接记录" | 选择"跳过"->不进草稿；选择"仍创建"->进草稿 ADD | 无需重试 | 保存前总览可"全部移除冗余"批量改投影 |
| **E06** 移除被 ALL 覆盖直接记录 | 移除时 info 提示"该操作存在 ALL 覆盖，移除直接记录不影响 ALL 覆盖" | 单元格显示 `★ ALL覆盖` + `－ 移除直接记录` 共存（正交标记） | 直接记录移除进草稿；ALL 不受影响 | 无需重试 | 想真正撤销权限 -> 去 ALL 行移除 ALL 授权 |
| **E07** 派生权限编辑 | DERIVED 单元格只读，点击不响应，仅展示来源 | tooltip/说明层"派生权限（来源: 角色组合/依赖规则），不可直接编辑" | 无 | 无 | 跳转来源角色/规则页调整 |
| **E08** GROUP_ROLE 直接配权 | `directGrantable=false` -> readonly=true，"授权"按钮禁用 + readonlyReason | 中栏只读 alert"组合角色不直接持有权限，请管理所含基础角色" | 无 | 无 | "管理基础角色"入口（T-PERM-034 后启用） |

> 关键不变量：**allCovered 只看计划态 mainDraft**（ALL 被草稿移除后实例不再显示覆盖）+ **DERIVED/directGrantable=false 永不可编辑**。

### 2.3 批量操作范围错误（E09–E13）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E09** 含不可授予操作 | 矩阵 NOT_GRANTABLE 单元格灰态不可选；批量工具栏"授予全部"仅对 grantable 生效 | toast"3 项已授予，2 项不可授予已跳过" | 可授予的进草稿 ADD，不可授予的不变 | 无需重试 | 单独处理不可授予项（申请 canGrant 权限后） |
| **E10** 含派生/只读 | DERIVED 单元格不可选（点击不响应） | 选择时不入选，无额外提示 | 无 | 无 | 无 |
| **E11** 条件跨操作冲突 | `showSplitHint`：多操作时 alert"如需按操作配置不同条件，请拆分多次授权" | 警告常驻步骤三/批量条件面板顶部 | 统一条件应用到全部选择集 | 无需重试 | 用户拆分为多次批量操作（按操作分组重选） |
| **E12** ALL 与 INSTANCE 混合 | ALL 行与 INSTANCE 单元格 scopeMode 不同，批量工具栏按各自 scopeMode 生成任务；建议禁用混合选择 | 选择 ALL 行时 INSTANCE 自动取消，或提示"ALL 与实例不可同批" | 无 | 无 | 分两次操作 |
| **E13** 跨资源类型批量 | 矩阵一次一资源类型，`switchResourceType` 切换不清草稿但批量选择集随类型重置 | 切资源类型时批量选择自动清空 + toast"已切换类型，批量选择已清空" | 草稿保留（跨类型），仅选择集清空 | 无需重试 | 无 |

> 关键不变量：**NOT_GRANTABLE 不进 ADD 草稿**（grantableByOperator 检查）+ **DERIVED 不可选**。

### 2.4 保存期间其他管理员修改权限（E14–E15，关键缺口）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E14** 他人已修改（不可检测） | **当前无预防**：缺 configVersion/updatedAt（§13.2 缺口 9）。缓解：保存后强制 reload baseline；离开保护；长时间编辑提示"建议刷新" | **当前无反馈**。目标态：CONCURRENT_MODIFIED sheet 按稳定权限键列冲突矩阵 | 目标态：本地草稿保留 + 服务端新 baseline 拉取 | 目标态：按键三选一（合并/覆盖/丢弃重新加载） | 见下方并发矩阵 |
| **E15** 保存中他人修改 | 保存 in-flight 时三栏只读，但他人修改在后端发生 | 保存响应返回后 reload baseline 可能与他人改动合并；若后端返回版本冲突（未来）-> E14 | 草稿已提交，不可撤回 | 无 | 同 E14 目标态 |

> **P2-2 修正：并发不是整角色 last-write-wins**。`save` 是 `add/update/remove` **增量补丁**（同事务），不是整份快照替换。多人并发编辑的冲突应按**稳定权限键**逐键判定，而非"整角色覆盖"：

| 并发情形（A 先保存，B 基于旧 baseline 保存） | 实际结果 | 风险 |
|---|---|---|
| A、B 改动**互不相交**的权限键 | 自然合并，无冲突 | 无 |
| A、B 改动**同键 update**（如都改 conditionCode） | B 后写覆盖 A（last-write-wins，仅此键） | 🟡 数据丢失（A 的属性改动静默丢失） |
| A 新增键 X，B 基于旧 baseline 也新增键 X，**相同 conditionCode** | condition_id 相同，触发唯一约束，B 整事务回滚 | 🔴 B 保存失败（可恢复，不污染数据） |
| A 新增键 X，B 基于旧 baseline 也新增键 X，**不同 conditionCode** | condition_id 不同，**不触发唯一约束，两条记录并存** | 🔴 前端 `Map<稳定键>` 按键去重只显一条，与后端不一致（见 P1-2 模型差异） |
| A 移除键 X，B 基于旧 baseline update/remove 键 X | B 的 update/remove 命中 0 行（键已不存在），无操作或报"not found" | 🟡 B 误以为已更新/已移除 |
| A 移除键 X，B 基于旧 baseline 新增键 X | X 被重新创建（A 的移除被覆盖） | 🟡 与 A 意图相反 |

> 因此"覆盖 A 全部改动且无法恢复"的结论**不准确**。`CONCURRENT_MODIFIED` 的合并 UI 应按上表逐键展示冲突，而非整角色 diff。`configVersion` 仍可用于严格检测（整体版本不一致即拒绝），但键级矩阵是合并 UI 的数据基础。
>
> **P1-2 多条件授权模型（已决策 A，允许同键多条件）**：上表"同键不同 conditionCode 并存"是后端 OR 语义的**正常行为**，非异常。后端 `uk_role_resource_permission(...COALESCE(condition_id,0)...)` 允许同键多条件记录，查询引擎逐条 OR 评估（`PermissionConditionDomainServiceImpl`），快照按 `(resourceEntityId, conditionId)` 保留多条（`SnapshotAssembler`）。前端须对齐：`Map<GrantVariantId, DraftPermission>` + `Map<PermCellKey, GrantVariantId[]>` 索引，单元格聚合展示、逐条件分支编辑/撤销，子权限 dependOn 关联具体父变体，canGrant 保留为每条记录属性。详见 [`permission-grant-state-model.md`](./permission-grant-state-model.md) §2.2。并发矩阵"同键不同条件并存"行为正确，合并 UI 须按 (PermCellKey, conditionCode) 双维度展示冲突。

> **诚实结论**：E14/E15 是当前最大风险。状态模型已预留 `CONCURRENT_MODIFIED` 态但**不可达**，因后端无版本字段。建议：
> - **短期**（无后端改动）：编辑超阈值时长（如 5 分钟）提示"事实可能已过期，建议刷新"；保存前自动 reload baseline 比对，若 baseline 变化 -> 提示"权限事实已变化，建议刷新后再保存"（弱检测，能发现部分并发）。
> - **长期**：T-PERM-034 补 configVersion，save 请求携带版本，后端不一致返回冲突 -> 启用 CONCURRENT_MODIFIED 态。

### 2.5 部分保存成功（E16–E18）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E16** 主成功子失败 | 保存前校验子权限父 id 可解析；replay 防孤儿 | sheet 失败视图"主权限已保存，2 项子权限保存失败" + 失败格红描边 | `failedChildren` overlay 保留 FailedChildOp[]；mainDiff 已落库 | `retryFailedChildren`：非 stale 直接 saveAll（overlay 在 childDraft 投影）；stale 先 **fetchBaseline+reconcile** 再恢复 overlay 再 saveAll | 重试成功 -> overlay 清；仍失败 -> 保留草稿 |
| **E17** 主失败（业务拒绝） | 保存前门禁（readonly/saving/stale/hasDraft）；空保存跳过；后端返回明确业务错误（权限不足/条件不存在/参数非法） | toast error + sheet 失败视图"保存失败：<原因>" | `failedChildren` 用 failedSnapshot 恢复（未执行 add-child，原 overlay 不丢）；草稿完整保留 | 修正后 `saveAll` 重试（业务拒绝可安全重试，服务端未提交） | 草稿不丢，可改后重试 |
| **E17b** 主失败（结果未知） | save 请求发出后**超时/断网**，服务端可能已提交事务但响应未返回 | **不能直接归 MAIN_FAILED**：先进入 `SAVE_OUTCOME_UNKNOWN`，**fetchBaseline** 后 reconcile 决定续传（禁止用 reloadBaseline，会清草稿） | reconcile 对比：已落库的不再重试 add（避免唯一约束）；未落库的作为新 diff 续传 | **必须先 fetchBaseline+reconcile 再决定**，禁止盲目重试 add；建议落地 `clientRequestId` 幂等键后端去重 | reconcile 成功 -> CLEAN；仍有未落库 -> DIRTY 续传 |
| **E18** reload 失败 | 无（网络/后端不可控） | `baselineStale=true`，sheet 提示"保存已提交但刷新失败" | 保存已落库（主+子成功部分），但本地 baseline 陈旧；grantTasks 未清（reload 失败分支不清） | `reloadBaseline` 重试；或重新选角色 | STALE 态：保存禁用，提示"重新选角色刷新"；重新选角色 -> 重新加载 baseline -> CLEAN |

> **P2-1 修正：子权限失败与 reload 失败正交，非互斥**。实现中 `saveAll` Step5 `reloadBaseline()` 在 Step6 部分失败判定**之前**执行，因此 `childFailureOccurred=true`（E16）与 `!reloadOk`（E18）可**同时发生**：一次保存可既得到 `failedChildren` 非空又得到 `baselineStale=true`。`CHILD_PARTIAL` 与 `reload 失败` 不是互斥分支，应作为**正交两维**建模：
>
> - `childFailure` 维度：子权限是否有失败（E16，`failedChildren` 非空）
> - `stale` 维度：baseline 是否过期（E18，`baselineStale=true`）
>
> 组合态 `STALE_WITH_CHILD_FAILURE`（childFailure && stale）：UI 须**同时**呈现两个恢复要求——"先重新选角色刷新事实"（stale 优先，因基于陈旧 baseline 重试子项不安全）+ "刷新后仍有 N 项子权限待重试"。恢复顺序固定：先 fetchBaseline+reconcile（清 stale，不清草稿，保留待重试数据）-> 再 retryFailedChildren（清 childFailure）。

> **P1-1 修正：reload 不得清掉待 reconcile 数据**。当前 `reloadBaseline` 成功分支会清空 `grantTasks` 和 `failedChildren`，若直接用于 E17b reconcile 或 STALE_WITH_CHILD_FAILURE 恢复，刷新后将无法判断哪些主权限未落库、哪些子权限待重试。**原子恢复流程**（须实现，替代直接 reloadBaseline）：
>
> 1. **snapshot**：先捕获当前期望投影（`mainDraft`/`childDraft` 快照）与失败操作（`failedChildren` 快照）及当前 diff。
> 2. **fetchBaseline**：调用**不清草稿**的 baseline 拉取（新增 `fetchBaseline`，仅更新 `mainBaseline`/`childBaseline`，**不动** `grantTasks`/`failedChildren`）；分离"拉取事实"与"清草稿"两个动作。
> 3. **reconcile**：对比 snapshot 期望投影与新 baseline：已落库的 add 不再重试（避免唯一约束）；未落库的作为新 diff 续传；子权限待重试项重新解析 parentId（新 baseline 已有主权限 id）。
> 4. **replay/diff**：`mainDraft`/`childDraft` 仍由 baseline+grantTasks replay 生成，failedChildren overlay 仍叠加；reconcile 后 diff 自动反映未落库项。
> 5. **清 stale**：reconcile 成功后 `baselineStale=false`，保留 `failedChildren` 供 retry。
>
> `STALE_WITH_CHILD_FAILURE` 须提供**原子化"刷新并重试"动作**（一键执行 fetchBaseline -> reconcile -> retryFailedChildren），避免分步操作导致中间态丢数据。未实现 `fetchBaseline` 前，E17b/STALE_WITH_CHILD_FAILURE 的恢复**不安全**（会丢待重试数据），为已知实现缺口。

> 关键不变量：**MAIN_FAILED 必须用 failedSnapshot 恢复 overlay**（第五轮 P1）+ **saveAll 返回 Promise<boolean> 区分"未开始"与"开始后失败"**（第六轮 P2）+ **stale 重试先备份 pendingOps 再 reload 再恢复**（第五轮 P1）。

### 2.6 网络中断（E19–E22）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E19** 加载中断 | 骨架屏占位；不阻塞其他栏 | 该栏 skeleton -> empty + "加载失败 [重试]"；旧上下文不变（P1-3） | 上一角色上下文完整保留 | "重试"按钮重发请求 | 重试成功 -> 正常；失败 -> 保留 empty+重试 |
| **E20** 保存中断 | 保存前可检测 `navigator.onLine`，离线时保存按钮禁用 + tooltip"网络不可用" | toast"网络中断，保存失败"；**归入 E17b 结果未知**（非 E17 业务拒绝，因服务端可能已提交） | 草稿完整保留（failedSnapshot 恢复） | 网络恢复后先 fetchBaseline+reconcile 再续传（见 E17b） | 同 E17b |
| **E21** 重载中断 | 无 | 归入 E18 -> STALE | 保存已落库 | `reloadBaseline` 重试 | 重新选角色刷新 |
| **E22** 浏览器离线 | `window online/offline` 事件监听；离线时全局 banner"网络已断开，操作将无法保存" | 顶部红色 banner；保存/重试按钮禁用 | 草稿保留（本地） | online 事件恢复 -> banner 消失，按钮恢复 | 网络恢复后继续编辑/保存 |

> 策略：**所有网络错误统一归入对应业务错误路径**（E20->E17b，E21->E18），不单独建网络错误态，避免状态爆炸。离线态(E22)是唯一独立态，因它影响全局可操作性。

### 2.7 用户离开时存在未保存内容（E23–E26）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E23** 路由切换 | `onBeforeRouteLeave`：hasDraft 时弹确认"当前有未保存变更，离开将丢弃" | ElMessageBox 三选（确认离开/取消） | 取消 -> 保留草稿+当前页；确认 -> 丢弃 | 无 | 取消则留页 |
| **E24** 刷新/关闭 | `beforeunload`：hasDraft 时 `e.preventDefault()` | 浏览器原生"离开此站点?" | 浏览器决定（通常丢弃） | 无 | 用户留页则继续 |
| **E25** 切角色/域 | `selectRole` 内 hasDraft 时 ElMessageBox"切换角色将丢弃" | 三选（切换/取消） | 取消 -> 留当前角色；切换 -> 丢弃草稿+加载新角色 | 无 | 取消则留 |
| **E26** 保存中离开 | `saving=true` 时 onBeforeRouteLeave **直接阻止**（不弹确认，因草稿正在提交）；beforeunload 强制拦截 | 路由阻止 + toast"保存进行中，请等待完成" | 草稿正在提交，不可丢 | 无 | 等 saving 结束 |

> 关键不变量：**saving 期间离开保护升级为强制阻止**（非确认），因草稿正在落库，丢弃会导致半提交状态。`onUnmounted` 调 `cleanup()` 移除 beforeunload 监听防泄漏。

### 2.8 用户失去权限编辑资格（E27–E31）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E27** 失去 MANAGE | `readonly` computed 含 `!operatorCapability.canManage`（fail-closed）；重新加载发现 canManage=false -> 整页只读 | 页头"只读"标记 + readonlyReason"操作者无管理能力"；保存按钮禁用 | 草稿保留（只读不阻止查看） | 无 | 重新获得 MANAGE 后自动恢复可编辑（重新加载） |
| **E28** 角色被禁用 | `readonly` 含 `!r.enabled`；角色树标灰"已禁用" | 选中禁用角色 -> 只读 + "角色已禁用" | 无 | 无 | 启用角色后恢复 |
| **E29** canGrant 被回收 | `isGrantableByOperator` fail-closed，grantableOperationCodes 不含 -> NOT_GRANTABLE | 单元格 ⊘ 标记 + denyReason"操作不可授予" | 已有权限可回收（canGrant 不约束回收） | 无 | 重新获得 canGrant |
| **E30** 保存时后端拒绝 | 前端 fail-closed 降低概率；后端 SecurityException 兜底 | 归入 E17 主失败，toast"权限不足：<原因>" | 草稿完整保留 | 重新获权后 saveAll | 重新获权 |
| **E31** 会话过期 | http 拦截器 401 -> 跳登录页（全局） | 全局 401 处理 | 草稿在内存（重新登录后若回页面已丢，因内存不持久） | 重新登录后回页面 | **缺口**：会话过期草稿丢失。建议草稿持久化到 sessionStorage（见 §5） |

> 关键不变量：**fail-closed**（能力默认拒绝，不默认全可用）+ **canGrant 不约束回收**（既有权限可移除）。

### 2.9 输入校验（E32–E34，补充）

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E32** 条件停用 | `ReConditionPicker.validate()` 确认前校验；已绑定停用条件可回显但保存前必替换 | picker 标红"条件已停用，请替换或清除" | 草稿保留停用条件码（待替换） | 无 | 替换为启用条件或清除 |
| **E32b** 清除条件 wire 不一致 | **真实 bug**：前端清除条件发 `conditionCode=null`，后端把 null 解释为"不更新" | 保存并 reload 后原条件重新出现（清除未生效） | - | 适配层把"清除"序列化为 `conditionCode=""` | 见下方 wire 约定 |

> **P2-3 修正：条件清除的 wire 约定**。后端 `PermissionGrantAppServiceImpl` update 分支语义（Java:426-437）：
> - `conditionCode == null` -> **不更新**（保留原条件）
> - `conditionCode` 空白（`isBlank()`） -> **清空** `conditionId=null`
> - `conditionCode` 非空 -> 绑定对应 conditionId
>
> 前端 `RolePermissionUpdateItem.conditionCode` 当前为 `string | null`，清除条件时设 `null` -> 后端视为"不更新" -> 清除不生效。**修复约定**：适配层（`toAddItem`/update 构造）须把"清除条件"显式序列化为 `conditionCode=""`（空串），"不修改条件"才发 `null`（或不传该字段）。add 项无"不更新"语义，无条件时发 `""` 或不传。此为**已实现代码 bug**（`hook.ts`/`permission-grant.ts`），设计文档先落 wire 约定，代码修复由后续任务执行。

| 子项 | 预防 | 反馈 | 数据保留 | 重试 | 恢复 |
|---|---|---|---|---|---|
| **E33** 子权限类型不符 | `childResourceTypeCodes` 约束；ChildPermissionInline 仅展示允许类型 | 选不到不允许的类型 | 无 | 无 | 无 |
| **E34** 空保存 | saveAll 检查 allDiff 空 -> "无变更" info；保存按钮 hasDraft=false 时禁用 | "无变更" toast / 按钮禁用 | 无 | 无 | 无 |

## 3. 错误状态图

聚焦保存与并发维度（最复杂）：

```
                    DIRTY
                      │
                  saveAll()
                      │
              ┌───────▼────────┐
              │ SAVE_PREVIEW   │  (交互规格: 变更总览+警告)
              │ (sheet 总览)   │
              └───┬────────┬───┘
            取消  │        │ 确认
              ◄───┘        ▼
                          SAVING
                 ┌──────────┬──────────┬──────────┬──────────┐
          业务拒绝 │  结果未知   │  子部分失败  │ reload失败 │ 全成功
          (E17)    │ (E17b/超时) │   (E16)     │ (E18/21)  │
                 ▼           ▼           ▼           ▼
           SAVE_FAILED  SAVE_OUTCOME_  SAVE_FAILED    STALE
           (MAIN)       UNKNOWN        (CHILD_PARTIAL)  │
                        │ reload+            │            │
                        │ reconcile          │ ◄──┐       │
                        ▼                    │    │       │
                     CLEAN 或              │    │       │
                     DIRTY(续传)           │    │       │
                                          │    │       │
                  └── E16+E18 可同时发生 ──┘    │       │
                      (STALE_WITH_CHILD_FAILURE)│       │
                                                ▼       ▼
                                             CLEAN 或 仍 STALE

  并发维度(目标态,当前不可达):
    SAVING ──后端返回版本冲突──► CONCURRENT_MODIFIED
                                   │
                   ┌───────────────┼───────────────┐
                   ▼               ▼               ▼
                合并            覆盖           丢弃重新加载
            (逐项选)      (强制save)       (reload->CLEAN)
```

加载/离线维度：

```
READY ──网络中断(E19)──► LOAD_FAILED ──重试──► READY 或 保持 LOAD_FAILED
  │
  └──离线(E22)──► OFFLINE_BANNER(全局) ──online──► READY
```

失格维度：

```
EDITABLE ──重新加载发现 canManage=false(E27)/角色禁用(E28)──► READONLY
READONLY ──重新获权/启用──► EDITABLE
任意态 ──401(E31)──► 跳登录(草稿丢,除非 sessionStorage 持久化)
```

## 4. 错误反馈矩阵

按 `feedback-patterns`，反馈分四层，从轻到重：

| 层级 | 触发 | 形式 | 示例 |
|---|---|---|---|
| L1 就地提示 | 单元格级输入/能力问题 | tooltip + 单元格标记 | NOT_GRANTABLE ⊘ + denyReason；条件停用标红 |
| L2 toast | 操作结果（非阻塞） | 右上角短暂消息 | "3 项已授予，2 项跳过"；"已加入变更"；"保存成功" |
| L3 sheet/内联警告 | 保存前警告、部分失败 | 保存前总览 sheet 警告区；失败视图 | "3 项冗余直接记录 [全部移除]"；"2 项子权限失败 [重试]" |
| L4 模态阻断 | 离开保护、严重错误确认 | ElMessageBox | "当前有未保存变更，离开将丢弃"；STALE 时"事实已过期" |

**反馈原则**：
- 本地操作（投影变更）只用 L1/L2，不用 L3/L4（避免噪音）。
- 网络/保存错误用 L3（sheet 失败视图，带可恢复动作）。
- 离开/失格用 L4 或常驻 banner。
- 错误信息含：**发生了什么 + 为什么 + 下一步动作**。不暴露 stack trace/requestId 给普通用户，但 requestId 可放"详情"折叠区供排查。
- 失败必有**可恢复动作按钮**，不把用户留在死胡同。

## 5. 数据保留策略

| 数据 | 存储位置 | 失败时保留 | 清除时机 |
|---|---|---|---|
| `mainBaseline`/`childBaseline` | 内存 ref | MAIN_FAILED 保留；reload 成功覆盖 | 选角色/重载 |
| `grantTasks` | 内存 ref | 所有失败保留；只有 reload 成功才清 | reload 成功 / discardAll |
| `failedChildren` overlay | 内存 ref | MAIN_FAILED 用 failedSnapshot 恢复；CHILD_PARTIAL 保留 | saveAll 开始时清（先捕获 diff）/ retry 成功 / reload 成功 |
| `saveError` | 内存 ref | 失败时设；成功清 | saveAll 成功 / discardAll |
| `baselineStale` | 内存 ref | reload 失败设 true | reload 成功设 false（discardAll **不清**，因事实仍陈旧） |
| 草稿（grantTasks）持久化 | **当前不持久化** | 会话过期/刷新丢失 | - |

**建议增强**（E31 会话过期场景）：
- 将 `grantTasks` + `currentRole` 上下文持久化到 `sessionStorage`（key 含 tenantId），刷新/重登回页面后提示"检测到未保存草稿，是否恢复"。
- 注意：`failedChildren` overlay 不持久化（含服务端 id，重登后可能已失效，应重新加载）。
- 持久化需处理版本：草稿 schema 变更时用版本号失效旧草稿。

## 6. 重试策略

按 `loading-states`，区分超时与重试：

| 场景 | 重试方式 | 退避 | 上限 |
|---|---|---|---|
| 加载失败(E19) | 用户点"重试"按钮 | 无（用户主动） | 无限（用户决定） |
| 保存主失败-业务拒绝(E17) | 用户修正后点"保存" | 无 | 无限（服务端未提交，可安全重试） |
| 保存主失败-结果未知(E17b/E20) | **禁止直接重试**；先 fetchBaseline+reconcile | 无 | reconcile 后续传未落库项 |
| 子权限部分失败(E16) | "重试未完成项"按钮 -> `retryFailedChildren` | 无 | 无限；stale 分支先 fetchBaseline 再 retry |
| 子操作超时(add-child/remove-child 网络断) | 归入 E16 子失败 + fetchBaseline+reconcile（**非 E17b**，因主 save 已成功返回） | 无 | reconcile 后 retry 子项 |
| reload 失败(E18/E21) | "重新选角色刷新"或自动 reload 重试 | 无 | 无限（STALE 持续到 reload 成功） |
| 网络离线(E22) | online 事件自动恢复 | - | - |
| **自动重试** | **不启用**（保存类操作不可静默重试，避免重复落库） | - | - |

**重试安全**：
- 保存类操作**禁止自动重试**（可能导致重复授权）。必须用户显式触发。
- `retryFailedChildren` 只重试 failedChildren overlay 中的项，不重试已成功的（基于 diff 重新计算）。
- 重试前检查门禁（readonly/saving/stale），不满足时恢复 failedChildren + 提示原因（第六轮 P2）。
- 超时：http 层设超时（如 30s），超时归入网络错误路径，不无限挂起 saving。

**幂等性**（P1-2 修复前置，建议后端增强）：
- save 请求携带 `clientRequestId`（幂等键），后端去重，避免网络重试导致重复授权。
- 当前未实现。E20（保存中断）与 E17b（结果未知）重试均存在重复落库风险：服务端已提交但响应未返回时，重试 add 触发唯一约束。
- 落地 `clientRequestId` 后，E17b 可安全 fetchBaseline+reconcile+续传而无需担心重复；**未落地前 E17b 必须先 fetchBaseline+reconcile 再决定续传，禁止盲目重试**。建议 T-PERM-034 一并补。

## 7. 恢复方案汇总

| 错误 | 恢复入口 | 最终态 |
|---|---|---|
| E03/E16 子权限失败 | "重试未完成项" | CLEAN（全成功）或保留 SAVE_FAILED |
| E17 主失败 | "保存"重试 | CLEAN 或 SAVE_FAILED |
| E18 reload失败 | "重新选角色" | READY+CLEAN 或 STALE |
| E19 加载失败 | "重试" | READY 或 LOAD_FAILED |
| E22 离线 | 网络恢复 | READY |
| E23/E25 离开保护 | "取消"留页 | DIRTY（保留） |
| E26 保存中离开 | 等待 saving 结束 | CLEAN/SAVE_FAILED |
| E27/E28 失格 | 重新获权/启用 | EDITABLE |
| E30 后端拒绝 | 重新获权后重试 | CLEAN |
| E31 会话过期 | 重新登录（建议 sessionStorage 恢复草稿） | READY（草稿恢复或丢） |
| E14/E15 并发（缺口） | **当前无恢复**（last-write-wins）；目标态三选一 | CONCURRENT_MODIFIED -> 合并/覆盖/丢弃 |

## 8. 关键缺口与建议优先级

| 缺口 | 影响 | 优先级 | 建议 |
|---|---|---|---|
| **E14/E15 并发检测缺失** | 多人编辑覆盖，数据丢失，不可恢复 | 🔴 高 | T-PERM-034 补 configVersion；短期加"保存前 reload 比对"弱检测 |
| **E31 会话过期草稿丢失** | 重登后草稿丢，用户体验差 | 🟡 中 | grantTasks 持久化到 sessionStorage + 恢复提示 |
| **E20 保存重试不幂等** | 网络重试可能重复落库 | 🟡 中 | save 请求加 clientRequestId 幂等键 |
| **E01 依赖补全不可见** | 用户不知后端补全了什么 | 🟡 中 | T-PERM-035 自动授权预览 |
| **E02 循环依赖跨页** | 授权页保存才发现环 | 🟢 低 | resource-dependency 页预防已足够 |
| **长期编辑无过期提示** | baseline 长期不刷新，并发风险升高 | 🟡 中 | 编辑超 5min 提示"建议刷新" |
| **保存结果未知（P1-2）** | 超时/断网时服务端可能已提交，盲目重试 add 触发唯一约束 | 🔴 高 | 新增 `SAVE_OUTCOME_UNKNOWN` 态：先 fetchBaseline+reconcile 再续传；落地 `clientRequestId` 幂等 |
| **保存重试不幂等（P1-2/P2-3 关联）** | 网络重试可能重复落库 | 🟡 中 | save 请求加 `clientRequestId` 幂等键，后端去重 |
| **清除条件 wire 不一致（P2-3）** | 前端清除发 null，后端 null=不更新，清除不生效 | 🔴 高（已实现 bug） | 适配层把清除序列化为 `conditionCode=""`；修 `hook.ts`/`permission-grant.ts` |

## 9. 与状态模型/交互规格的对应

- 本错误流**不新增**状态模型维度，所有错误态归入 D5（CLEAN/DIRTY/SAVING/SAVE_FAILED 两子态/SAVE_OUTCOME_UNKNOWN/STALE + STALE_WITH_CHILD_FAILURE 组合态）+ D3（READONLY 失格）+ 新交互态（SAVE_PREVIEW/OFFLINE_BANNER）。
- 唯一需新增的**预留态**是 `CONCURRENT_MODIFIED`（E14/E15），已在 [`permission-grant-state-model.md`](./permission-grant-state-model.md) §2.2 登记，依赖 T-PERM-034。
- 所有错误反馈映射到 [`permission-grant-interaction.md`](./permission-grant-interaction.md) §4.5 保存前总览 sheet（失败视图）+ §7 反馈四层。
