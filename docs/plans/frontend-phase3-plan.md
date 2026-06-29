---
doc_type: plan
title: 前端 Phase 3 — 前后端联调
status: proposed
domain: frontend
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
tasks:
  - T-FE-015
  - T-FE-016
  - T-FE-017
  - T-FE-018
  - T-FE-019
  - T-FE-020
  - T-FE-021
  - T-FE-022
acceptance: "8 个页面 mock → 真实接口替换完成，核心流程联调通过，异常场景提示正确，页面间跳转/状态保持正确。"
last_updated: 2026-06-29
---

# 前端 Phase 3 — 前后端联调

> 状态：proposed
> 来源：`docs/plans/improvement-plan.md` §4 Phase 3 拆分
> 准入：Phase 1 收尾 + Phase 2 接口改造完成

## 目标

将 Phase 1 的 mock 页面切换到真实后端接口，端到端联调通过。

## 任务清单

8 个联调任务，每个对应一组已实现页面的 mock→真实接口替换：

| ID | 联调范围 | depends_on |
|---|---|---|
| T-FE-015 | 组织与用户（已实现 2.1） | T-PERM-037 |
| T-FE-016 | 角色管理（2.2） | T-FE-002, T-PERM-022 |
| T-FE-017 | 资源/操作定义（3.1） | T-FE-008, T-PERM-028 |
| T-FE-018 | 权限授予（4.1） | T-FE-014, T-PERM-034 |
| T-FE-019 | 权限查询/校验（4.2） | T-FE-013, T-PERM-033 |
| T-FE-020 | 条件/冲突规则（3.2/3.3） | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 |
| T-FE-021 | 业务域配置（5.1） | T-FE-006, T-PERM-026 |
| T-FE-022 | 系统/服务配置（6.x/5.2） | T-FE-003, T-FE-004, T-FE-007, T-PERM-023, T-PERM-024, T-PERM-027 |

## 联调完成标准

- 每页核心交互流程正常
- 异常场景（权限不足、数据不存在、参数校验失败）提示正确
- 页面间跳转和状态保持正确

## 归档条件

- 8 个联调任务全部 done
- 联调发现的接口问题回写 api-contract.md

## 当前进度

- 2026-06-29：建立本 plan + 拆分 8 个联调任务。全部 proposed，待 Phase 1/2 收尾。
