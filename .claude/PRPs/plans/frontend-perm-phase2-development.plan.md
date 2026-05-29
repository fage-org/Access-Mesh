# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 2

## Summary

开发权限中心前端缺失的高级功能模块（Phase 2），包括冲突规则管理和资源API映射管理两个模块。

## User Story

As a 权限管理员, I want 在前端界面管理冲突规则和资源API映射, So that 我能够处理复杂的权限场景和细粒度配置。

## Problem → Solution

后端已提供冲突规则和资源API映射的完整API，但前端缺少对应页面；域配置已并入业务域快速任务计划 → 本阶段聚焦拆分后的两个高级模块并做统一协调。

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 8-10（按拆分模块聚合统计）
- **前置条件**: Phase 1完成

---

## Dependencies

| 计划                                       | 关系     | 说明                                               |
| ------------------------------------------ | -------- | -------------------------------------------------- |
| `frontend-perm-phase1-development.plan.md` | 前置依赖 | Phase 1核心功能应先完成                            |
| `frontend-perm-domain-type-quick.plan.md`  | 关联参考 | 域配置功能已由快速任务计划拥有，本计划不再重复定义 |

---

## Mandatory Reading

| Priority | File                                       | Why                                               |
| -------- | ------------------------------------------ | ------------------------------------------------- |
| P0       | `frontend-perm-conflict-module.plan.md`    | 冲突规则模块的详细执行计划                        |
| P0       | `frontend-perm-api-mapping-module.plan.md` | 资源API映射模块的详细执行计划                     |
| P1       | `frontend-perm-backend-api-gap-tracker.md` | 记录冲突规则和API映射的契约差异与未来后端增强方向 |

---

## Delegated Plans

| 模块         | 详细计划                                   | 当前职责                                            |
| ------------ | ------------------------------------------ | --------------------------------------------------- |
| 冲突规则管理 | `frontend-perm-conflict-module.plan.md`    | 当前按后端真实规则模型收敛；高层诊断留待后续增强    |
| 资源API映射  | `frontend-perm-api-mapping-module.plan.md` | API层按后端契约收敛，资源展示信息由资源页上下文补齐 |
| 域配置       | `frontend-perm-domain-type-quick.plan.md`  | 已并入业务域快速任务计划，本文件不再重复定义        |

---

## Coordination Tasks

### Task 1: 执行拆分模块

- **ACTION**: 按拆分后的两个模块计划推进 Phase 2
- **IMPLEMENT**:
  - 先执行 `frontend-perm-conflict-module.plan.md`
  - 再执行 `frontend-perm-api-mapping-module.plan.md`
  - 域配置由 `frontend-perm-domain-type-quick.plan.md` 独立负责

### Task 2: 合并共享权限与路由

- **ACTION**: 在两个模块稳定后统一合并权限码和路由
- **IMPLEMENT**:
  - 统一补 `constants/permission.ts`
  - 在 `router/modules/perm.ts` 中补充冲突规则入口
  - API映射继续集成到资源管理页面，不额外创建独立路由

### Task 3: 处理契约差异

- **ACTION**: 把当前前端适配与后续后端增强拆开记录
- **IMPLEMENT**:
  - 冲突规则当前仅实现规则级管理和检测
  - 用户/角色级冲突诊断记录到 `frontend-perm-backend-api-gap-tracker.md`
  - API映射的展示别名和元数据在前端承接，不伪造后端字段

---

## Acceptance Criteria

- [ ] `frontend-perm-conflict-module.plan.md` 完成并通过前端校验
- [ ] `frontend-perm-api-mapping-module.plan.md` 完成并通过前端校验
- [ ] 域配置范围已从本文件移除，不再与业务域快速任务重复
- [ ] 共享权限码和路由合并完成
- [ ] Phase 2 不再保留字段级API示例，避免与拆分模块计划冲突

---

## Risks

| Risk                         | Likelihood | Impact | Mitigation                                             |
| ---------------------------- | ---------- | ------ | ------------------------------------------------------ |
| 聚合计划与拆分计划重复演化   | Medium     | Medium | 以拆分模块计划为准，本文件仅作协调参考                 |
| 高层冲突诊断误被纳入当前范围 | Medium     | High   | 当前范围收敛到后端已支持能力，增强项记录到 gap tracker |
