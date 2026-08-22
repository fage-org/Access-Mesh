---
doc_type: plan
title: 前端 Phase 3 — 前后端联调
status: proposed
domain: frontend
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/frontend/permission-grant.md
tasks:
  - T-FE-015
  - T-FE-016
  - T-FE-017
  - T-FE-018
  - T-FE-037
  - T-FE-019
  - T-FE-020
  - T-FE-021
  - T-FE-022
acceptance: "9 个有效联调任务 mock -> 真实接口替换完成（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿；T-FE-018 角色联调依赖 T-FE-036 + T-FE-038 + T-FE-039 + **T-FE-040** + T-PERM-040 + T-PERM-041 + T-PERM-034 + T-PERM-022/028/029/031（2026-08-05 评审：T-FE-038 mock 先行，T-FE-018 汇合单类型矩阵/图标模型/条件转授链路；2026-08-08 补记录级聚焦编辑 T-FE-040），T-ADMIN-021 不阻塞首期；T-FE-037 组织联调二期依赖 T-FE-018 + T-ADMIN-021；统一提交主通道 = apply-grant-plan，无 CAS/幂等表/clientRequestId），核心流程联调通过，异常场景提示正确，页面间跳转/状态保持正确。"
last_updated: 2026-08-08
---

# 前端 Phase 3 — 前后端联调

> 状态：proposed
> 来源：`docs/plans/improvement-plan.md` §4 Phase 3 拆分
> 准入：Phase 1 收尾 + Phase 2 接口改造完成

## 目标

将 Phase 1 的 mock 页面切换到真实后端接口，端到端联调通过。

## 任务清单

9 个有效联调任务（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿），每个有效任务对应一组已实现页面的 mock->真实接口替换：

| ID | 联调范围 | depends_on |
|---|---|---|
| T-FE-015 | 组织与用户（已实现 2.1） | T-PERM-037 |
| T-FE-016 | 角色管理（2.2） | T-FE-002, T-PERM-022 |
| T-FE-017 | 资源/操作定义（3.1） | T-FE-008, T-PERM-028 |
| T-FE-018 | 权限授予（4.1）- 角色联调（首期） | T-FE-036, T-FE-038, T-FE-039, **T-FE-040**, T-PERM-040, T-PERM-041, T-PERM-034, T-PERM-022, T-PERM-028, T-PERM-029, T-PERM-031 |
| T-FE-037 | 权限授予（4.1）- 组织联调（二期） | T-FE-018, T-ADMIN-021 |
| T-FE-019 | 权限查询/校验（4.2） | T-FE-013, T-PERM-033 |
| T-FE-020 | 条件/冲突规则（3.2/3.3） | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 |
| T-FE-021 | 业务域配置（5.1） | T-FE-006, T-PERM-026 |
| T-FE-022 | 系统/服务配置（6.x/5.2） | T-FE-003, T-FE-004, T-FE-007, T-PERM-023, T-PERM-024, T-PERM-027 |

## 联调完成标准

- 每页核心交互流程正常
- 异常场景（权限不足、数据不存在、参数校验失败）提示正确
- 页面间跳转和状态保持正确

## 归档条件

- 9 个有效联调任务全部 done（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿）
- 联调发现的接口问题回写 api-contract.md

## 当前进度

- 2026-06-29：建立本 plan + 拆分 8 个联调任务。全部 proposed，待 Phase 1/2 收尾。
- 2026-07-12：权限授予 UX 设计变更，T-FE-018 重连依赖 T-FE-027；联调以 `design/frontend/permission-grant.md` §16 为准。
- 2026-07-12：T-FE-018 关键路径增加 UX 重构链 T-FE-025 → T-FE-026 → T-FE-027，且仍依赖 T-PERM-034，是 Phase 3 最晚启动项；该延后用于避免旧矩阵联调后再次返工。
- 2026-07-26：权限授予页 v1+v2 整体删除重做，T-FE-018 标 cancelled；设计文档已归档至 `docs/archive/2026-07-26/`，待新页面设计完成后重新立项联调。
- 2026-08-01：v3 设计定稿（`permission-grant.md`）后 **T-FE-018 恢复待排期**（依赖 T-FE-036 + T-PERM-034 完成后启动），本 plan 有效联调任务由 7 个恢复为 8 个；frontmatter acceptance/归档条件同步更新。
- 2026-08-05：**评审方案 B 落地**——T-FE-018 依赖补 T-FE-038 + T-PERM-040（单类型矩阵链路汇合点）；T-FE-038 mock 先行、T-PERM-040 非前置；联调验收补多类型矩阵场景（类型切换/操作列隔离/list 类型过滤/20008 含嵌套反例）。
- 2026-08-05（评审）：**T-FE-018 再补 T-FE-039 + T-PERM-041 依赖**（汇合图标正交模型与条件转授 20041 链路）；联调验收补图标映射真实数据验证与条件+canGrant 场景（前端阻止 + 20041 兜底）。
