# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 3

## Summary

开发权限中心前端缺失的高级功能模块（Phase 3），包括权限版本查询、系统配置管理和权限视图完善三个模块。

## User Story

As a 系统管理员, I want 在前端界面查看权限版本状态、配置系统参数和查看完整的权限视图, So that 我能够监控缓存一致性状态并进行系统级配置。

## Problem → Solution

后端已提供权限版本查询、系统配置和权限视图相关API，但前端缺少对应页面；其中权限版本当前仅支持查询 → 本阶段聚焦当前可落地能力，并把未来增强单独记录。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 10-12（按拆分模块聚合统计）
- **前置条件**: Phase 2完成, `frontend-perm-completed-modules-improvement.plan.md` 中的权限视图完善

---

## Dependencies

| 计划                                                  | 关系     | 说明                            |
| ----------------------------------------------------- | -------- | ------------------------------- |
| `frontend-perm-phase2-development.plan.md`            | 前置依赖 | Phase 2应先完成                 |
| `frontend-perm-completed-modules-improvement.plan.md` | 部分重叠 | 权限视图API完善应在本计划前完成 |

---

## Mandatory Reading

| Priority | File                                         | Why                                  |
| -------- | -------------------------------------------- | ------------------------------------ |
| P0       | `frontend-perm-version-module.plan.md`       | 权限版本模块的当前范围与未来增强     |
| P0       | `frontend-perm-system-config-module.plan.md` | 系统配置模块的详细执行计划           |
| P0       | `frontend-perm-view-improvement.plan.md`     | 权限视图模块的详细执行计划           |
| P1       | `frontend-perm-backend-api-gap-tracker.md`   | 记录当前前端包装层与未来后端增强范围 |

---

## Delegated Plans

| 模块     | 详细计划                                     | 当前职责                                        |
| -------- | -------------------------------------------- | ----------------------------------------------- |
| 权限版本 | `frontend-perm-version-module.plan.md`       | 当前仅实现 query 与可选轻量页面，未来增强单列   |
| 系统配置 | `frontend-perm-system-config-module.plan.md` | API按后端契约收敛，分类与只读态由前端元数据承接 |
| 权限视图 | `frontend-perm-view-improvement.plan.md`     | 接口按后端真实契约收敛，页面可做前端包装层      |

---

## Coordination Tasks

### Task 1: 执行当前可落地模块

- **ACTION**: 按拆分模块计划推进 Phase 3
- **IMPLEMENT**:
  - 先完成 `frontend-perm-version-module.plan.md` 的当前范围
  - 再完成 `frontend-perm-system-config-module.plan.md`
  - 最后完成 `frontend-perm-view-improvement.plan.md` 的当前后端支持范围

### Task 2: 管理未来增强范围

- **ACTION**: 把当前不可落地但未来有价值的能力单独记录
- **IMPLEMENT**:
  - 版本 compare / rollback / list / detail / create 留在未来增强
  - 权限视图中无法由现有接口推导的 richer 信息（如来源链细节、用户级资源分布）留在未来增强
  - 所有后续后端改造点记录到 `frontend-perm-backend-api-gap-tracker.md`

### Task 3: 合并共享权限和路由

- **ACTION**: 在三个模块稳定后统一合并权限码和路由
- **IMPLEMENT**:
  - 版本页面仅添加查询入口
  - 系统配置页面按当前模块计划增加路由
  - 权限视图在现有页面内扩展子视图，不新增独立主路由

---

## Acceptance Criteria

- [ ] `frontend-perm-version-module.plan.md` 完成当前范围并通过前端校验
- [ ] `frontend-perm-system-config-module.plan.md` 完成并通过前端校验
- [ ] `frontend-perm-view-improvement.plan.md` 完成当前范围并通过前端校验
- [ ] 未来增强项已与当前范围明确拆分
- [ ] Phase 3 不再保留字段级API示例，避免与拆分模块计划冲突

---

## Risks

| Risk                         | Likelihood | Impact | Mitigation                                                 |
| ---------------------------- | ---------- | ------ | ---------------------------------------------------------- |
| 未来增强项被误当作当前范围   | Medium     | High   | 在模块计划和 gap tracker 中明确标记 current / future scope |
| 权限视图包装层与后端契约混淆 | Medium     | Medium | API 类型以拆分模块计划和 controller/DTO 为准               |
