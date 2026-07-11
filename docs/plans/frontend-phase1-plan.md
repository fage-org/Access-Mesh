---
doc_type: plan
title: 前端 Phase 1 — 页面实现 + API 核对（mock 驱动）
status: active
domain: frontend
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/implementation.md
  - docs/design/permission-center/core-flows.md
  - docs/design/architecture.md
  - docs/design/project-rules.md
  - docs/design/frontend/README.md
tasks:
  - T-FE-001
  - T-FE-002
  - T-FE-003
  - T-FE-004
  - T-FE-005
  - T-FE-006
  - T-FE-007
  - T-FE-008
  - T-FE-009
  - T-FE-010
  - T-FE-011
  - T-FE-012
  - T-FE-013
  - T-FE-014
acceptance: "13 个页面前端实现完成（mock 数据可交互）；每页 API 核对清单产出（接口标记 ✅/🔧/❌，🔧❌ 项登记为 Phase 2 后端任务 T-PERM-022~034）；页面级 UI 设计回写至 docs/design/frontend/<page>.md；hasPerms 门控 + 无权降级就绪。"
last_updated: 2026-06-29
---

# 前端 Phase 1 — 页面实现 + API 核对（mock 驱动）

> 状态：active
> 来源：`docs/plans/improvement-plan.md` §4.1 Phase 1 拆分
> 路线图本体：improvement-plan.md（roadmap，tasks:[] 保持空），本 plan 是 Phase 1 的执行编排

## 目标

完成 13 个未实现页面的前端实现（mock 数据驱动，可交互），同步产出后端 API 核对清单（✅满足/🔧需改造/❌缺失）。这是 D/E/F 工作单（T-PERM-019~021）重启前提「前端 Phase 1 收尾」的关键路径。

## 非目标

- 不在本 Phase 改后端（🔧❌ 项登记为 T-PERM 后端任务，归 Phase 2）。
- 不做前后端联调（mock 驱动，归 Phase 3）。
- 不实现自动授权 / 动态数据权限（design-review §11 暂缓，归 Phase 2）。

## 准入条件

- ✅ pure-admin-thin 模板就绪（frontend/）
- ✅ 2.1 组织与用户页已完成（P0/P1/P2 范式 + perms.ts SSOT 可复用）
- ✅ scopeMode 前端类型 + composable 已落地（T-PERM-015，`frontend/src/utils/scope-mode.ts`）

## 任务清单（引用 tasks/README 看板）

### 前端页面任务（T-FE）

执行顺序按 improvement-plan §4.1（简单→复杂，3 批）：

| ID | 页面 | 复杂度 | 批次 | 独立文件 |
|---|---|---|---|---|
| T-FE-002 | 2.2 角色管理 | 🟡 中 | 1 | 否（看板行） |
| T-FE-003 | 6.1 类型定义 | 🟢 低 | 1 | 否 |
| T-FE-004 | 6.2 系统配置 | 🟢 低 | 1 | 否 |
| T-FE-005 | 7.1 操作日志 | 🟢 低 | 1 | 否 |
| T-FE-006 | 5.1 业务域 | 🟡 中 | 2 | 否 |
| T-FE-007 | 5.2 服务+接口映射 | 🟡 中 | 2 | 否 |
| T-FE-008 | 3.1 资源+操作定义 | 🟡 中 | 2 | 否 |
| T-FE-009 | 3.2 权限条件 | 🟡 中 | 2 | 否 |
| T-FE-010 | 3.3 冲突规则 | 🔴 高 | 3 | 是 |
| T-FE-011 | 3.4 资源依赖 | 🔴 高 | 3 | 是 |
| T-FE-012 | 7.2 权限变更日志 | 🔴 高 | 3 | 是 |
| T-FE-013 | 4.2 权限查询/校验 | 🔴 高 | 3 | 是 |
| T-FE-014 | 4.1 权限授予 | 🔴 高 | 3 | 是 |

### API 核对清单 → Phase 2 后端任务（T-PERM-022~034）

> Phase 1 **不改后端**。各前端任务在 Step 3/4 产出 API 核对清单（✅/🔧/❌），🔧❌ 项登记为对应 Phase 2 后端任务。后端任务归属 `docs/plans/frontend-phase2-plan.md`（tasks:[]），本 plan 不持有它们——Phase 1 归档只要求 T-FE 任务 done + 清单产出，不要求后端任务 done。

13 个后端任务（T-PERM-022~034，逐页对应 13 个前端页面）归 permission-center；2.2 角色管理的「功能角色列表聚合」可选 admin-service。各后端任务 acceptance 依赖前端 API 核对产出的 🔧❌ 清单。

| ID | 页面 | depends_on（前端） | 归属 |
|---|---|---|---|
| T-PERM-022 | 2.2 角色管理后端 | T-FE-002 | Phase 2 |
| T-PERM-023 | 6.1 类型定义后端 | T-FE-003 | Phase 2 |
| T-PERM-024 | 6.2 系统配置后端 | T-FE-004 | Phase 2 |
| T-PERM-025 | 7.1 操作日志后端 | T-FE-005 | Phase 2 |
| T-PERM-026 | 5.1 业务域后端 | T-FE-006 | Phase 2 |
| T-PERM-027 | 5.2 服务+接口映射后端 | T-FE-007 | Phase 2 |
| T-PERM-028 | 3.1 资源+操作后端 | T-FE-008 | Phase 2 |
| T-PERM-029 | 3.2 权限条件后端 | T-FE-009 | Phase 2 |
| T-PERM-030 | 3.3 冲突规则后端 | T-FE-010 | Phase 2 |
| T-PERM-031 | 3.4 资源依赖后端 | T-FE-011 | Phase 2 |
| T-PERM-032 | 7.2 变更日志后端 | T-FE-012 | Phase 2 |
| T-PERM-033 | 4.2 权限查询后端 | T-FE-013 | Phase 2 |
| T-PERM-034 | 4.1 权限授予后端 | T-FE-014 | Phase 2 |

### 组件抽取任务（T-FE，单列）

| ID | 组件 | 前置 |
|---|---|---|
| T-FE-001 | 跨页组件抽象池（清单维护 + 派生子任务） | 2+ 页确认后抽取 |

## 跨页组件抽象池

> improvement-plan §4.1 Step 1.5：组件抽取必须先与用户确认，2+ 页使用才抽取。本清单由 T-FE-001 维护。

| 候选组件 | 场景 | 确认状态 | 对应任务 |
|---|---|---|---|
| ReOrgTreePanel | 组织与用户（左树） | ✅ 已确认（2.1 已实现） | — |
| 权限条件选择器 | 权限授予（内联新建）+ 条件管理（独立 CRUD） | ⏳ 待确认（T-FE-009 / T-FE-014 推进时） | 待建 |
| 资源树选择器 | 权限授予 + 资源管理 + 权限查询 | ⏳ 待确认（T-FE-008 / T-FE-013 / T-FE-014） | 待建 |
| 角色选择器 | 权限授予 + 功能角色分配 + 角色管理（父角色选择） | ⏳ 待确认（T-FE-002 已识别父角色选择器内联实现，T-FE-014 推进时确认抽取） | 待建 |
| Diff 对比面板 | 权限变更日志 + 操作日志详情 | ⏳ 待确认（T-FE-005 / T-FE-012） | 待建 |

## 页面任务统一 acceptance 范式

每个 T-FE 页面任务 acceptance（参照 2.1 P0/P1/P2 收敛为单任务）：

1. **P0 前端骨架**：路由/标题/页面壳/核心交互，mock 可交互
2. **Step 1.5 组件识别**：识别可复用组件→登记组件池→确认（不内嵌抽取）
3. **Step 3/4 API 核对**：以 mock 请求/响应为基准核对现有接口，标 ✅/🔧/❌；🔧❌ 项登记对应后端任务
4. **P2 权限接线**：hasPerms 门控（perms.ts SSOT）+ 无权降级
5. **design_writeback**：页面级 UI 设计回写至 `docs/design/frontend/<page>.md`

## 归档条件

- 13 页 T-FE 任务全部 done（含 UI 设计回写）
- 13 页 API 核对清单产出（🔧❌ 项已登记为 Phase 2 后端任务 T-PERM-022~034，**后端任务不在本 plan 闭环**）
- 组件池确认状态更新
- 稳定结论沉淀至 `docs/design/frontend/`
- 移入 `docs/archive/YYYY-MM-DD/`

## 当前进度

- 2026-06-29：建立本 plan + 拆分 13 页 T-FE 任务 + 组件池任务。T-PERM-022~034 后端任务归 Phase 2（本 plan 只产 API 核对清单，不持有后端任务）。所有任务 proposed，待按批次推进。
- 2026-06-29：**T-FE-002（2.2 角色管理）完成** ✅。左右分栏（角色树+详情/表单）+ 分组角色额外基本角色管理 + 拖拽移动 + hasPerms 门控（ROLE:VIEW/CREATE/UPDATE/DELETE/MANAGE）；mock 角色矩阵补 sec 全权/hr+auditor 只读。UI 设计回写 `docs/design/frontend/role-manage.md`（draft→adopted 待评审）。API 核对：tree/list/create/update/move/remove/extra-roles/* ✅；🔧 `/detail` 用 IdReq 内部主键、应切业务键二元组 `roleTypeCode+externalId`（核实：schema 唯一索引 `uk_abstract_role_external(tenant_id,role_type,external_id)` 已保证租户内唯一、表无 domain 字段、旧 `RoleDetailReq` 带 domainCode 是 bizDomainId 旧时代遗留需废弃；前端树已返回 roleTypeCode+externalId 前提满足），登记 T-PERM-022。组件识别：角色选择器候选登记 T-FE-001 池（待 T-FE-014 推进确认）。
- 2026-07-11：**T-FE-007（5.2 服务+接口映射）完成** ✅。服务库存 + 当前服务工作区；服务登记/编辑/删除、固定 FULL 的接口清单同步、手工 API 映射 CRUD 与三维筛选均由 mock 驱动；hasPerms 接线 `SERVICE:VIEW/MANAGE/SYNC_INTERFACE/MANAGE_API_MAPPING`，admin/sec 全权、hr/auditor 只读。UI 设计回写 `docs/design/frontend/service-interface-mapping.md`。API 核对：service-config 基础 CRUD 与映射 create/update 响应 ✅；服务 list 分页、service remove 级联及失效、apis 树形资源字段、sync FULL 收敛、mapping list VIEW 校验、资源业务键选择与同步元数据等 🔧 已登记 T-PERM-027。
- 2026-07-11：**T-FE-008（3.1 资源+操作定义）完成** ✅。左右布局（左资源树 + 右资源信息条 + 操作权限表）；资源树 CRUD + 移动（弹窗选目标父节点，防环 + 跨类型拦截）、操作权限 CRUD（binaryBit 2 的幂次校验 + 同类型内唯一性）均由 mock 驱动；hasPerms 接线 `RESOURCE:VIEW/CREATE/MANAGE` + `OPERATION:VIEW/CREATE/MANAGE`，admin/sec 全权、hr/auditor 只读。UI 设计回写 `docs/design/frontend/resource-operation.md`。API 核对：tree/create（resource+operation）✅；detail/update/move/remove 用内部主键 id 🔧 切业务键、list/tree 未见 VIEW 门禁种子缺失 🔧，已登记 T-PERM-028。组件识别：资源树选择器（popover+el-tree 父选择器）登记 T-FE-001 池，待 T-FE-013/014 推进确认抽取。
- 2026-07-11：**T-FE-009（3.2 权限条件）完成** ✅。单表格扁平 CRUD（非树）+ 条件规则可视化编辑器（logic AND/OR + items 行 type 下拉 4 类型 DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST + params 动态表单 date/time-picker 与 cidrs tag 输入）+ gatewayEvaluable 开关（T-PERM-017，前端预校验白名单）+ 规则摘要 popover；hasPerms 接线 `CONDITION:VIEW/CREATE/UPDATE/DELETE` 三档独立非 MANAGE（对齐后端 ConditionAppServiceImpl），admin/sec 全权、hr/auditor 只读。UI 设计回写 `docs/design/frontend/permission-condition.md`。API 核对：create ✅；list 用 EmptyReq 无分页无筛选 🔧、detail/update/remove 用内部主键 id 🔧 切业务键 code、list/detail 无 VIEW 校验 🔧、ConditionResp 缺 updatedAt 🔧、§5.6 缺字段契约 🔧，已登记 T-PERM-029。组件识别：权限条件选择器登记 T-FE-001 池，待 T-FE-014 推进确认抽取。**第 2 批（006-009）全部完成，解锁第 3 批 🔴（T-FE-010 起）。**
- 2026-07-11：**T-FE-010（3.3 冲突规则）完成** ✅。单表格 CRUD + 表单弹窗（冲突类型 ROLE_MUTEX/PERM_MUTEX 切换动态字段：角色互斥用角色对选择器，权限互斥用操作权限对+资源类型选择器）+ 冲突检测对话框（仅操作权限对，双向匹配，UI 标注"仅检测权限互斥"）；表格规则内容名称映射（加载 role/operation/type-def 建立映射，缺失回退 #ID）；hasPerms 接线 `CONFLICT_RULE:VIEW/CREATE/UPDATE/DELETE` 三档独立非 MANAGE（对齐后端 ConflictRuleAppServiceImpl），admin/sec 全权、hr/auditor 只读。UI 设计回写 `docs/design/frontend/conflict-rule.md`。API 核对：6 端点 ✅；detail/update/remove 用内部主键 id（冲突规则无业务键，可接受）🔧、list 无分页无 VIEW 校验 🔧、Resp 缺 updatedAt 🔧、ConflictRuleDetailReq 死代码 ❌、conflictType 注释不一致 🔧、detect 无权限校验 🔧，已登记 T-PERM-030。**第 3 批首项完成，下一项 T-FE-011 资源依赖。**

- 2026-07-11：**T-FE-011（3.4 资源依赖）完成** ✅。依赖 CRUD + 依赖图 echarts graph 力导向布局全屏抽屉 + 环检测对话框（DFS 双向可达）；资源类型下拉+资源下拉联动选择器；bits->操作码位运算拆解 + id->资源映射应对 Resp 字段不全；编辑资源对可改全量替换；batch-sync P0 标 TODO；maintainSource 按 schema 4 种；hasPerms 接线 DEPENDENCY:VIEW/CREATE/UPDATE/DELETE 三档独立非 MANAGE + SYNC（batch-sync 专用 P0 不暴露），admin/sec 全权、hr/auditor 只读。UI 设计回写 docs/design/frontend/resource-dependency.md。API 核对：🔧 8 项登记 T-PERM-031。**评审 P1/P2 修复**：P2 bitsToOpNames 按类型取名称（commit 4647bcd65）+ P1 全局操作解析全链路修复阶段1 专属优先+全局 fallback（commit 810c167c3，T-PERM-031 阶段2-4 待做）+ 删除无类型查询方法（commit 7561ea4fd）。**第 3 批第 2 项完成，下一项 T-FE-012 变更日志。**
- 2026-07-11：**T-FE-012（7.2 权限变更日志）完成** ✅。分页表格（服务端分页 + entityType/entityId 筛选）+ 详情抽屉（基本信息 el-descriptions + DiffSnapshotPanel 结构化 diff：eventType tag + items[] changeType tag + permission/role/resource 业务键卡片 + before/after 状态对比 + old/new 原始快照折叠 + 影响范围受影响用户/角色 tags）；hasPerms 接线复用 SYSTEM_CONFIG:VIEW（无独立 PERMISSION_CHANGE_LOG 权限码，与 7.1 操作日志同源），admin/sec/hr/auditor 均可查（审计员必须能查）。UI 设计回写 docs/design/frontend/permission-change-log.md。API 核对：list 端点 ✅、diff_snapshot §6.8 规范 ✅、无 detail 设计合理 ✅；契约路径错误 🔧（§5.8 写 /api/perm/permission-change-log/list，后端实际 /api/perm/log/change/list）、缺字段契约章节 🔧、筛选维度不足 🔧（仅 entityType/entityId）、Resp 缺操作人 🔧、changeSource 枚举 schema 不符 🔧（schema ADMIN/SYNC/API/SYSTEM vs 代码 MANUAL/SERVICE_SYNC）、独立权限码审计语义 🔧，已登记 T-PERM-032。组件识别：DiffSnapshotPanel 登记 T-FE-001 池待确认（T-FE-005 当前无 diffSnapshot 字段，共用性待后续扩展确认），本页内联不抽取。**第 3 批第 3 项完成，下一项 T-FE-013 4.2 权限查询/校验。**
- 2026-07-11：**T-FE-012 评审修复**（commit d327af4b7）。1 P1 + 3 P2 全部属实并修复：P1 `loadTable` 并发请求覆盖（`reqSeq` 请求序号 + 过期丢弃 + 最新失败清空旧数据）；P2 `entityId` `:min=0` + mock 补 2 条 `entityId=0` 批量聚合日志（对齐后端 BATCH_DELETE/BATCH_REMOVE）；P2 `parseDiffSnapshot` 逐项校验 items（非空对象 + `changeType`∈ADD/REMOVE/UPDATE，防 `[null]` 崩溃）；P2 acceptance 措辞调整（Diff 面板「待 T-FE-005 扩展后确认」，与组件池一致）。附带发现后端 `ROLE_BATCH_DELETE` eventType 超 §6.8 7 枚举，追加登记 T-PERM-032。
- 2026-07-11：**T-FE-013（4.2 权限排查）完成** ✅。三 Tab 布局（当前有效权限 effective-permissions §6.8 / 范围权限四态 query-scopes §6.7 / 单权限解释 explain §6.8）；主体模型 Tab1/3 支持 targetType=USER/ROLE、Tab2 仅 USER（核实 PermissionQueryAppServiceImpl:126 / PermissionViewAppServiceImpl:698/674）；角色类型码对齐 RoleType.java（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE，ORG/POSITION domainCode 必填，不存在 ORG_ROLE/POSITION_ROLE）；scopeMode 四态矩阵展示（资源类型×操作笛卡尔积 + tooltip 实例 + 图例）；权限门控临时复用 SYSTEM_CONFIG:VIEW + 整页无权状态 + hook 短路（路由框架不消费 meta.auths）；reqSeq 过期请求静默丢弃（对齐 T-FE-012）；前瞻性采用 admin-service 聚合路径 /permission-query/*（Phase 1 mock 模拟，T-PERM-033 后对接真聚合层，前端无需改路径）。**接口定位纠正**：原验收点名 query-resources（§6.6 运行时 SDK，不分页仅用户）与管理排查冲突，Tab1 改用 effective-permissions（§6.8 分页排查视图 USER/ROLE+来源角色），query-resources 降为 API 核对。UI 设计回写 docs/design/frontend/permission-query.md（draft->adopted）。API 核对 🔧 7 项登记 T-PERM-033（admin-service 聚合入口/统一门禁 PERMISSION_QUERY:VIEW 全链路方案 A-B/explain DTO 扩展条件评估冲突详情/recentChanges 按权限键 6 字段过滤/ADMIN_USER 主体语义/query-resources+treeMode/permission-view/* 契约差异）。组件识别：资源键输入（resourceTypeCode+resourceCode+codeType）+ SubjectInputBar 候选登记 T-FE-001 池待 T-FE-014 确认抽取；treeMode 结果树不做 UI。**第 3 批第 4 项完成，下一项 T-FE-014 4.1 权限授予（第 3 批最后一项）。**
