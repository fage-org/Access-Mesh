---
doc_type: plan
title: 前端 Phase 2 — 核心功能补齐 + 后端接口改造
status: proposed
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
tasks:
  - T-PERM-022
  - T-PERM-023
  - T-PERM-024
  - T-PERM-025
  - T-PERM-026
  - T-PERM-027
  - T-PERM-028
  - T-PERM-029
  - T-PERM-030
  - T-PERM-031
  - T-PERM-032
  - T-PERM-033
  - T-PERM-034
  - T-PERM-035
  - T-PERM-036
  - T-PERM-037
acceptance: "13 页后端接口改造完成（T-PERM-022~034 逐页实现）；跨页共性接口改造 + api-contract 回写完成（T-PERM-037）；自动授权实现 + 测试通过（T-PERM-035）；动态数据权限链路验证通过（T-PERM-036）。注：T-PERM-035/036 受 design-review §11 暂缓门禁约束，需 PM 重申后才能进入 in-progress。"
last_updated: 2026-06-29
---

# 前端 Phase 2 — 核心功能补齐 + 后端接口改造

> 状态：proposed
> 来源：`docs/plans/improvement-plan.md` §4 Phase 2 拆分
> ⚠️ 暂缓门禁：自动授权（T-PERM-035）/ 动态数据权限（T-PERM-036）受 design-review §11 E4 / Q7-B 决策约束，近期不推进，需 PM 重申后才能重启。

## decision_refs（暂缓依据，非实现依据）

> 以下归档文档仅作决策溯源，**不作为实现依据**，不进入 design_refs 回写范围。

- `docs/archive/2026-06-17/design-review.md` §11 — E4（auto-grant 保留 TODO + Phase X 未排期）、Q7/B（动态数据权限延后到 example-service 暂不实现）

## 目标

- 实现 Phase 1 各页标记的 🔧❌ 接口改造（T-PERM-022~034 逐页实现）
- 跨页共性接口改造 + api-contract 回写收尾（T-PERM-037，不重复逐页改造）
- 实现自动授权（T-PERM-035，暂缓）
- 验证动态数据权限端到端链路（T-PERM-036，暂缓）

## 非目标

- 不在本 Phase 做前后端联调（归 Phase 3）
- 不实现 example-service（design-review §11 Q7/B 决策暂不实现）

## 准入条件

- Phase 1 收尾（13 页 API 核对清单产出 🔧❌ 项）
- **暂缓项额外门禁**：design-review §11 E4（auto-grant Phase X 未排期）/ Q7-B（动态数据权限延后到 example-service 暂不实现）需 PM 重申解除

## 任务清单

### 逐页后端接口改造（T-PERM-022~034，depends_on 对应 Phase 1 前端任务）

| ID | 页面 | depends_on | 门禁 |
|---|---|---|---|
| T-PERM-022 | 2.2 角色管理后端 | T-FE-002 | Phase 1 该页 API 核对清单产出 |
| T-PERM-023 | 6.1 类型定义后端 | T-FE-003 | 同上 |
| T-PERM-024 | 6.2 系统配置后端 | T-FE-004 | 同上 |
| T-PERM-025 | 7.1 操作日志后端 | T-FE-005 | 同上 |
| T-PERM-026 | 5.1 业务域后端 | T-FE-006 | 同上 |
| T-PERM-027 | 5.2 服务+接口映射后端 | T-FE-007 | 同上 |
| T-PERM-028 | 3.1 资源+操作后端 | T-FE-008 | 同上 |
| T-PERM-029 | 3.2 权限条件后端 | T-FE-009 | 同上 |
| T-PERM-030 | 3.3 冲突规则后端 | T-FE-010 | 同上 |
| T-PERM-031 | 3.4 资源依赖后端 | T-FE-011 | 同上 |
| T-PERM-032 | 7.2 变更日志后端 | T-FE-012 | 同上 |
| T-PERM-033 | 4.2 权限查询后端 | T-FE-013 | 同上 |
| T-PERM-034 | 4.1 权限授予后端 | T-FE-014 | 同上 |

### 暂缓核心能力（T-PERM-035/036）

| ID | 内容 | status | 门禁 |
|---|---|---|---|
| T-PERM-035 | 自动授权（resolveAutoGrants + autoGrantForInsert + 循环依赖检测） | proposed | design-review §11 E4：Phase X 未排期，需 PM 重申 |
| T-PERM-036 | 动态数据权限端到端验证（scopeMode → SQL 映射链路） | proposed | design-review §11 Q7/B：延后到 example-service 暂不实现 |

### 共性收尾（T-PERM-037）

| ID | 内容 | status | 门禁 |
|---|---|---|---|
| T-PERM-037 | 跨页共性接口改造 + api-contract 回写收尾（**不重复逐页改造**，仅处理多页共用接口与契约回写） | proposed | depends_on T-PERM-022~034 |

## 归档条件

- T-PERM-022~034 + 037 done（或暂缓项 035/036 cancelled，需 PM 决策）
- 改造接口回写 `docs/design/permission-center/api-contract.md`
- 自动授权流程回写 `core-flows.md`（若 035 推进）

## 当前进度

- 2026-06-29：建立本 plan + 拆分 16 个任务（13 逐页后端 + 035/036 暂缓 + 037 共性收尾）。全部 proposed，暂缓项带门禁，待 PM 重申。
