# Plan: Permission Explanation (Diagnosis) Module

## Summary

开发 `/perm/explain` 权限排查页面的当前可落地能力，围绕现有 `explain` 接口展示权限判定结果、来源角色、匹配权限和最近变更，不在当前计划内引入矩阵视图或双主体对比能力。

## User Story

As a permission administrator, I want 在前端查看单次权限判定的解释结果, so that 我能够知道某个用户或角色为什么被允许或拒绝，并看到与该判定直接相关的来源角色和最近变更。

## Problem → Solution

后端已经提供 `POST /api/perm/permission-view/explain`，但前端当前只有占位页 → 先按现有请求 / 响应契约完成 explain 页面，把当前接口已经返回的 `allowed`、`reason`、`sourceRoles`、`matchedPermissionIds`、`recentChanges` 用清晰的方式展示出来；矩阵视图和权限对比留待未来增强。

## Current Scope

- 当前范围只覆盖 explain 接口对应的单次权限排查。
- 支持用户和角色两种查询模式。
- 支持可选的 `includeSourceRoles`、`includeRecentChanges`、`recentDays` 查询参数。
- 页面可以提供文案映射、查询预设和友好展示，但不得发明后端不存在的比较或矩阵输入。

## Future Scope

以下能力不在当前计划落地范围内：

- 资源-操作矩阵视图。
- 双主体 / 双角色权限对比。
- 需要跨多个接口拼装或后端新增聚合契约的 richer 诊断能力。

如后续需要恢复这些能力，应先在 `frontend-perm-backend-api-gap-tracker.md` 中明确记录后端增强边界，再新增后续计划承接。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `README.md` 执行顺序（P3） + 用户决策（问题5）的当前可落地范围
- **Estimated Files**: 4-5
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan                                                  | Relation        | Description                                          |
| ----------------------------------------------------- | --------------- | ---------------------------------------------------- |
| `frontend-perm-structure-analysis.plan.md`            | Prerequisite    | 先统一编码规范与组件组织方式                         |
| `frontend-perm-completed-modules-improvement.plan.md` | Partial overlap | explain 页面壳层和共享 `permissionView.ts` 先收敛    |
| `frontend-perm-view-improvement.plan.md`              | Prerequisite    | 权限视图共享 API 与当前子视图能力先稳定              |
| `frontend-perm-api-mock.plan.md`                      | Downstream      | explain 页面稳定后需输出 mock 场景供最终演示计划复用 |

---

## Mandatory Reading

| Priority | File                                                             | Why                                 |
| -------- | ---------------------------------------------------------------- | ----------------------------------- |
| P0       | `permission-center/.../controller/PermissionViewController.java` | 确认 explain 真实接口路径与范围     |
| P0       | `permission-center/.../dto/req/PermissionExplainReq.java`        | 确认 explain 请求字段               |
| P0       | `permission-center/.../dto/resp/PermissionExplainResp.java`      | 确认 explain 响应字段               |
| P1       | `frontend/src/api/perm/permissionView.ts`                        | 复用现有 explain API 文件           |
| P1       | `frontend/src/views/perm/explain/index.vue`                      | 当前占位页与壳层入口                |
| P1       | `frontend-perm-backend-api-gap-tracker.md`                       | 记录未来矩阵 / 对比等增强能力的边界 |

---

## Backend Contract Snapshot

### Endpoint

- `POST /api/perm/permission-view/explain`

### Request Fields

- `targetType`
- `subjectTypeCode?`
- `subjectExternalId?`
- `roleTypeCode?`
- `roleExternalId?`
- `domainCode?`
- `resourceTypeCode`
- `resourceCode`
- `codeType?`
- `operationCode`
- `includeSourceRoles?`
- `includeRecentChanges?`
- `recentDays?`

### Response Fields

- `targetType`
- `allowed`
- `reason`
- `permission`
- `sourceRoles`
- `matchedPermissionIds`
- `recentChanges`

### Frontend Wrapper Boundary

- 可以提供查询预设、字段说明、状态标签和时间范围快捷项。
- 可以把 `sourceRoles`、`recentChanges` 做可折叠展示。
- 不得在当前计划中新增 `compareTargets`、`matrixMode` 等不存在的请求字段。
- 不得把矩阵、对比等未来能力写回当前验收标准。

---

## Files to Create / Update

| File                                                               | Action | Justification                          |
| ------------------------------------------------------------------ | ------ | -------------------------------------- |
| `frontend/src/api/perm/permissionView.ts`                          | UPDATE | 校准 explain 请求 / 响应类型与辅助映射 |
| `frontend/src/views/perm/explain/index.vue`                        | UPDATE | 将占位页收敛为当前 explain 主页面      |
| `frontend/src/views/perm/explain/components/QueryPanel.vue`        | CREATE | explain 查询条件面板                   |
| `frontend/src/views/perm/explain/components/ExplainResult.vue`     | CREATE | explain 结果展示组件                   |
| `frontend/src/views/perm/explain/components/RecentChangesList.vue` | CREATE | 最近变更列表展示，避免主结果组件过重   |

---

## Step-by-Step Tasks

### Task 1: 校准 explain API 类型

- **ACTION**: 对齐 `frontend/src/api/perm/permissionView.ts` 中 explain 相关类型
- **IMPLEMENT**:
  - 校准 `PermissionExplainRequest` 字段命名
  - 校准 `PermissionExplainResult` 的 `permission`、`sourceRoles`、`recentChanges` 结构
  - 保留展示标签辅助函数，但不在 explain API 上新增矩阵或对比接口包装
- **VALIDATE**: `pnpm typecheck`

### Task 2: 创建查询面板

- **ACTION**: 创建 explain 查询条件面板
- **IMPLEMENT**:
  - 支持用户和角色两种查询模式
  - 支持资源类型、资源编码、操作编码输入
  - 支持 `includeSourceRoles`、`includeRecentChanges` 和 `recentDays`
  - 明确参数校验和空值处理

### Task 3: 创建 explain 结果展示组件

- **ACTION**: 创建 explain 结果组件
- **IMPLEMENT**:
  - 展示 allow / deny 结果和 `reason`
  - 展示 `permission` 键信息
  - 展示 `sourceRoles`
  - 展示 `matchedPermissionIds`
  - 复用单独的最近变更列表子组件

### Task 4: 完成 explain 页面

- **ACTION**: 把 `views/perm/explain/index.vue` 从占位页升级为当前 explain 页面
- **IMPLEMENT**:
  - 使用查询面板发起 explain 请求
  - 使用结果组件展示当前响应
  - 增加空态、加载态、错误态
  - 不在当前页面引入矩阵 tab 或 compare tab

### Task 5: 准备统一 mock 交付

- **ACTION**: 为最终 mock 计划输出 explain 场景
- **IMPLEMENT**:
  - 提供至少 1 组允许访问场景
  - 提供至少 1 组拒绝访问场景
  - 提供至少 1 组带 `recentChanges` 的场景
  - 把这些场景交给 `frontend-perm-api-mock.plan.md`

---

## Testing Strategy

### Static Analysis

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm lint:prettier
```

### Functional Testing

| Test           | Input                  | Expected Output                         |
| -------------- | ---------------------- | --------------------------------------- |
| User explain   | 用户模式 + 资源 + 操作 | 展示 allow / deny、reason、source roles |
| Role explain   | 角色模式 + 资源 + 操作 | 展示 allow / deny、reason、matched ids  |
| Recent changes | 勾选最近变更并设置天数 | 返回最近变更列表并正常展示              |
| Empty / denied | 输入无权限或无命中场景 | 页面展示拒绝或空态，不报错              |

---

## Validation Commands

```bash
cd frontend
pnpm typecheck
pnpm lint:eslint
pnpm build
```

EXPECT: Zero errors, build success

---

## Acceptance Criteria

- [ ] explain 页面按当前 `/api/perm/permission-view/explain` 契约完成
- [ ] 页面支持用户和角色两种 explain 查询模式
- [ ] 页面可展示 `reason`、`sourceRoles`、`matchedPermissionIds`、`recentChanges`
- [ ] 当前计划不再把矩阵视图和双主体对比写入交付范围
- [ ] 已为 `frontend-perm-api-mock.plan.md` 输出 explain mock 场景
- [ ] 全量编译通过

---

## Risks

| Risk                           | Likelihood | Impact | Mitigation                                                     |
| ------------------------------ | ---------- | ------ | -------------------------------------------------------------- |
| 未来增强能力再次回流到当前范围 | Medium     | High   | 当前计划只以 explain endpoint 为边界，未来能力先入 gap tracker |
| recentChanges 展示信息理解偏差 | Medium     | Medium | 页面明确标注其来自 explain 响应而非独立审计查询结果            |
| 查询参数模式切换复杂           | Medium     | Medium | 先支持最小必要输入，再做字段联动优化                           |
