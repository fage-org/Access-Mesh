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
