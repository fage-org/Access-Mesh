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
  - T-PERM-040
  - T-PERM-041
  - T-FE-036
  - T-FE-038
  - T-FE-039
  - T-ADMIN-021
acceptance: "13 页后端接口改造完成（T-PERM-022~034 逐页实现）；跨页共性接口改造 + api-contract 回写完成（T-PERM-037）；自动授权实现 + 测试通过（T-PERM-035）；动态数据权限链路验证通过（T-PERM-036）；T-FE-036 前端权限授予页（mock 驱动）实现完成（本 plan 关联的前端部分，见正文前端重建任务节；含 DoD：api-contract 对齐/引擎 fixtures 比对/四态状态机）；单类型矩阵上下文完成（T-FE-038 前端 + T-PERM-040 后端，2026-08-03 定稿，2026-08-05 评审扩展 list 类型过滤 + 嵌套 20008）；条件权限不可转授完成（T-PERM-041，20041 + DDL CHECK）；矩阵图标正交模型完成（T-FE-039）；T-ADMIN-021 org-tree includePositions **二期**（首期只角色入口，第十四轮收窄）。注：T-PERM-035/036 受 design-review §11 暂缓门禁约束，需 PM 重申后才能进入 in-progress。"
last_updated: 2026-08-05
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
| T-PERM-034 | 4.1 权限授予后端（见任务卡，第十四轮收窄） | frontend-phase2 | api-contract §5.5/§6.4/§6.5/§6.5.1；implementation §4/§7.7；core-flows §6；design/frontend/permission-grant.md §12；permission-center.sql | T-PERM-031 | ⚙️ | ⏳ |

### 单类型矩阵后端任务（T-PERM-040/041，2026-08-03 定稿；范围见任务卡）

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-PERM-040](../tasks/T-PERM-040.md) | 4.1 权限授予单资源类型后端支持 | ⏳ | T-PERM-028, T-PERM-034 |
| [T-PERM-041](../tasks/T-PERM-041.md) | 条件权限不可转授 | ⏳ | T-PERM-034 |

### 前端重建任务（T-FE-036/T-FE-038/T-FE-039，本 plan 关联的前端部分）

> frontmatter `tasks` 同时登记 T-FE-036/T-FE-038/T-FE-039；前端以 **mock 数据驱动**（接口形状按 api-contract），T-FE-038 mock 先行、T-PERM-040 非前置（2026-08-05 评审方案 B），联调任务 T-FE-018 同时依赖 T-FE-038 + T-PERM-040 汇合。

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-FE-036](../tasks/T-FE-036.md) | 4.1 权限授予页重设计（v3） | 👀 | T-FE-001/002/008/009 |
| [T-FE-038](../tasks/T-FE-038.md) | 4.1 权限授予页单类型矩阵上下文 | ⏳ | T-FE-036 |
| [T-FE-039](../tasks/T-FE-039.md) | 4.1 矩阵图标正交状态模型与图标精简 | ⏳ | T-FE-038 |
| [T-ADMIN-021](../tasks/T-ADMIN-021.md) | org-tree 扩展 includePositions（组织+岗位一体树，授权页主体树数据源） | ⏳ | — |


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

- T-PERM-022~034 + 037 + 040 + 041 done（或暂缓项 035/036 cancelled，需 PM 决策）+ T-FE-036 done（前端部分，见上）+ **T-FE-038 done（单类型矩阵上下文）** + **T-FE-039 done（图标正交模型）** + **T-ADMIN-021 done（2026-08-01 第七轮 P2-2 补）**
- 改造接口回写 `docs/design/permission-center/api-contract.md`
- 自动授权流程回写 `core-flows.md`（若 035 推进）

## 当前进度

- 2026-06-29：建立本 plan + 拆分 16 个任务（13 逐页后端 + 035/036 暂缓 + 037 共性收尾）。全部 proposed，暂缓项带门禁，待 PM 重申。
- 2026-08-02：**T-FE-036 实现完成转 review**（mock 驱动 + 自验通过；设计 `permission-grant.md` 回写 adopted，含 §13 实现注记；S1~S7 待人工交互验收）。验收后本 plan 前端部分仅剩 T-ADMIN-021（二期）与 T-PERM-022~037 后端任务。
- 2026-08-03：**单类型矩阵上下文定稿**（需求确认：单权限类型 = 单个 `resourceTypeCode`），新增 T-FE-038（前端 MatrixContext + 类型切换加载 + 操作列配置按类型隔离）与 T-PERM-040（后端 operation-permission/list 类型查询 + apply-grant-plan 20008 校验）；设计回写 permission-grant.md §2.2/§3.2/§3.5/§3.6/§11（S8/S9）/§12、api-contract §5.3/§6.5.1、core-flows §6。
- 2026-08-05（二轮评审）：**图标映射定稿修正**——条纹=有条件、粗黑边框=可转授 canGrant、红/淡红=撤销（取代上轮"条纹=部分移除/粗黑边框=整格删除"映射，T-FE-039 同步重写）；子权限分叉精确投影规则（仅直接主权限记录、继承格不复制、级联撤销附红图标）；条件转授前端行为（选条件清 canGrant、20041 提示）并入 T-FE-039；T-FE-018 再补 T-FE-039/T-PERM-041 依赖；T-PERM-041 范围收窄为仅最终态建表 DDL（不考虑历史数据，用户确认）；任务行治理精简。
