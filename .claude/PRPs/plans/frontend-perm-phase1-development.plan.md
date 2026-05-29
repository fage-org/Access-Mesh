# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 1

## Summary

Phase 1 聚焦三个核心模块：权限条件、抽象用户、日志审计。本文件只做协调与收口，不再承载字段级 API 示例。

## User Story

As a 权限管理员, I want 在前端完成权限条件、抽象用户和日志审计三条核心链路, so that 我能够先把权限中心的基础运营能力跑通。

## Problem → Solution

后端已提供完整的权限条件、抽象用户和日志查询 API，但前端缺少对应页面与 API 层 → 将 Phase 1 拆成三个独立模块计划推进，本文件只负责执行顺序、共享配置和契约差异收口。

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 12-16（按拆分模块聚合统计）
- **前置条件**: `frontend-perm-completed-modules-improvement.plan.md` 完成后再推进

---

## Dependencies

| Plan                                                  | Relation     | Description                           |
| ----------------------------------------------------- | ------------ | ------------------------------------- |
| `frontend-perm-completed-modules-improvement.plan.md` | Prerequisite | 先稳定共享层、权限码和路由基线        |
| `frontend-perm-api-mock.plan.md`                      | Downstream   | Phase 1 接口和页面稳定后统一纳入 mock |

---

## Mandatory Reading

| Priority | File                                       | Why                                |
| -------- | ------------------------------------------ | ---------------------------------- |
| P0       | `frontend-perm-condition-module.plan.md`   | 权限条件模块详细计划               |
| P0       | `frontend-perm-user-module.plan.md`        | 抽象用户模块详细计划               |
| P0       | `frontend-perm-log-module.plan.md`         | 日志审计模块详细计划               |
| P1       | `frontend-perm-backend-api-gap-tracker.md` | 记录需前端适配或等待后端增强的差异 |

---

## Delegated Plans

| 模块     | 详细计划                                 | 当前职责                                   |
| -------- | ---------------------------------------- | ------------------------------------------ |
| 权限条件 | `frontend-perm-condition-module.plan.md` | 按后端真实契约落地条件管理和前端模板层     |
| 抽象用户 | `frontend-perm-user-module.plan.md`      | 完成 CRUD + sync，并明确 sync version 来源 |
| 日志审计 | `frontend-perm-log-module.plan.md`       | 完成审计日志 API 与页面实现                |

---

## Coordination Tasks

### Task 1: 按拆分计划推进核心模块

- **ACTION**: 依次推进 condition、user、log 三个独立计划
- **IMPLEMENT**:
  - 先完成 `frontend-perm-condition-module.plan.md`
  - 再完成 `frontend-perm-user-module.plan.md`
  - 日志模块可与 user 模块并行推进

### Task 2: 合并共享权限与路由

- **ACTION**: 在三个模块稳定后统一补权限码和路由
- **IMPLEMENT**:
  - 合并 `frontend/src/constants/permission.ts`
  - 合并 `frontend/src/router/modules/perm.ts`
  - 复用现有列表/表单/详情布局模式，避免重复实现

### Task 3: 收口契约差异

- **ACTION**: 把前端包装层和真实后端契约分开记录
- **IMPLEMENT**:
  - 条件模板体验记录到 gap tracker
  - 抽象用户 sync version 的来源记录到 gap tracker
  - 当前实现按现有 controller / DTO 落地，不等待后端改造

### Task 4: 输出 mock 交付清单

- **ACTION**: 为最终统一 mock 计划提供 Phase 1 的接口与场景清单
- **IMPLEMENT**:
  - 汇总 condition / user / log 的请求与响应样例
  - 标记每个页面至少一个 happy path 和一个边界场景
  - 交给 `frontend-perm-api-mock.plan.md` 统一实现

---

## Acceptance Criteria

- [ ] `frontend-perm-condition-module.plan.md` 完成并通过前端校验
- [ ] `frontend-perm-user-module.plan.md` 完成并通过前端校验
- [ ] `frontend-perm-log-module.plan.md` 完成并通过前端校验
- [ ] Phase 1 的共享权限码和路由已合并完成
- [ ] Phase 1 不再保留字段级 API 示例，避免与拆分模块计划冲突
- [ ] 已输出给 `frontend-perm-api-mock.plan.md` 的接口与场景清单

---

## Risks

| Risk                        | Likelihood | Impact | Mitigation                                  |
| --------------------------- | ---------- | ------ | ------------------------------------------- |
| 聚合计划再次回流字段级示例  | Medium     | High   | 本文件只保留协调职责，字段级契约全部下放    |
| 条件 / 用户模块契约再次漂移 | Medium     | High   | 统一以 controller / DTO 和 gap tracker 为准 |
| 最终 mock 场景覆盖不足      | Medium     | Medium | 在本阶段就同步整理接口样例和边界场景        |
