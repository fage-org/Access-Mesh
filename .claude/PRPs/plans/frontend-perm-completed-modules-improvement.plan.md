# Plan: Permission Center Frontend 已完成模块完善计划

## Summary

对权限中心前端已完成模块做一次共享层与规范层收敛，重点修正 API 包装、权限码、路由绑定、空态/错误态壳层和占位页面边界，为后续缺失模块与最终 mock 演示提供稳定基座。

## User Story

As a 前端开发者, I want 已完成模块的共享层和壳层先稳定下来, so that 后续新增模块、权限视图增强和统一 mock 不会建立在漂移的基线之上。

## Problem → Solution

已完成模块虽然可用，但仍存在类型导入不统一、共享权限码缺口、`permissionView.ts` / `view` / `explain` 边界不清等问题 → 先清理共享层和占位页面，再由专门模块计划承接完整功能。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 10-14
- **前置条件**: 代码规范分析完成

---

## Dependencies

| Plan                                       | Relation     | Description                        |
| ------------------------------------------ | ------------ | ---------------------------------- |
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | 先确定统一规范与问题清单           |
| `frontend-perm-view-improvement.plan.md`   | Downstream   | 权限视图完整能力由独立计划承接     |
| `frontend-perm-explain-module.plan.md`     | Downstream   | 权限排查完整能力由独立计划承接     |
| `frontend-perm-api-mock.plan.md`           | Downstream   | 已完成模块稳定后统一纳入 mock 计划 |

---

## Current Scope

- 规范化现有 `api/perm/*.ts` 的类型导入、返回类型和工具函数风格。
- 补齐已完成模块使用到的共享权限码、共享路由配置和基础空态/错误态。
- 对 `permissionView.ts`、`views/perm/view/index.vue`、`views/perm/explain/index.vue` 做壳层和边界整理。
- 明确哪些能力继续留在独立模块计划中，不在本计划内重复展开。

## Out of Scope

- 不在本计划完成权限视图的 4 个完整子视图。
- 不在本计划完成权限排查的可视化、矩阵和对比能力。
- 不新增任何后端契约假设，不补写未来字段。

---

## Files to Change

| File                                          | Action | Justification                          |
| --------------------------------------------- | ------ | -------------------------------------- |
| `frontend/src/api/perm/domain.ts`             | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/operation.ts`          | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/permissionView.ts`     | UPDATE | 只保留共享层与现有后端可支持的公共包装 |
| `frontend/src/api/perm/resourceDependency.ts` | UPDATE | 收敛共享 API 包装形式                  |
| `frontend/src/api/perm/resource.ts`           | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/rolePermission.ts`     | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/role.ts`               | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/service.ts`            | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/type.ts`               | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/api/perm/userRole.ts`           | UPDATE | 统一类型导入和 API 风格                |
| `frontend/src/views/perm/view/index.vue`      | UPDATE | 收敛壳层和入口结构                     |
| `frontend/src/views/perm/explain/index.vue`   | UPDATE | 收敛壳层和入口结构                     |
| `frontend/src/constants/permission.ts`        | UPDATE | 补齐共享权限码                         |
| `frontend/src/router/modules/perm.ts`         | UPDATE | 补齐已有页面的权限绑定                 |

---

## Coordination Tasks

### Task 1: 统一共享 API 规范

- **ACTION**: 把现有 `api/perm/*.ts` 的导入、命名和返回包装统一到当前规范
- **IMPLEMENT**:
  - 使用内联类型导入 `{ type X }`
  - 保持 `http.request<T>("post", path, { data })` 模式一致
  - 清理只在旧计划中出现、但当前代码不需要的额外包装层

### Task 2: 补齐共享权限码和路由绑定

- **ACTION**: 为现有已完成页面补齐准确的菜单权限与按钮权限常量
- **IMPLEMENT**:
  - 修正 `constants/permission.ts`
  - 修正 `router/modules/perm.ts`
  - 确保现有页面在权限受控和 mock 演示环境下都能正常展示

### Task 3: 收敛 view / explain 的壳层边界

- **ACTION**: 让权限视图和权限排查页面只保留当前阶段需要的壳层能力
- **IMPLEMENT**:
  - `views/perm/view/index.vue` 保持为权限视图增强计划的主入口壳层
  - `views/perm/explain/index.vue` 保持为权限排查计划的主入口壳层
  - 避免在本计划中重复定义完整子视图和字段级契约

### Task 4: 为后续计划输出稳定基线

- **ACTION**: 给下游功能计划和统一 mock 计划提供稳定共享层
- **IMPLEMENT**:
  - 记录仍需独立计划承接的能力
  - 保证 `permissionView.ts`、权限码、路由入口可以被后续模块直接复用
  - 为 `frontend-perm-api-mock.plan.md` 输出接口清单和场景清单

---

## Acceptance Criteria

- [ ] 现有 `api/perm/*.ts` 使用统一的导入与请求模式
- [ ] 现有权限中心页面的共享权限码和路由绑定已补齐
- [ ] `permissionView.ts`、`views/perm/view/index.vue`、`views/perm/explain/index.vue` 的边界清晰
- [ ] 本计划不再与 `frontend-perm-view-improvement.plan.md`、`frontend-perm-explain-module.plan.md` 重复抢占范围
- [ ] 已为最终 `frontend-perm-api-mock.plan.md` 输出稳定的共享层基线

---

## Risks

| Risk                             | Likelihood | Impact | Mitigation                                             |
| -------------------------------- | ---------- | ------ | ------------------------------------------------------ |
| 已完成模块与下游模块仍然重复改动 | Medium     | Medium | 在本计划中只收敛共享层，不展开完整功能                 |
| 壳层改动再次带入字段级契约假设   | Medium     | High   | 任何字段级契约都以下游模块计划和 controller / DTO 为准 |
| mock 基线不稳定                  | Medium     | High   | 本计划结束时同步输出统一接口清单给 mock 计划           |
