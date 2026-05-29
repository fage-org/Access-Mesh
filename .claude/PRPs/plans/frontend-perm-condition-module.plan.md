# Plan: Permission Condition Management Module

## Summary

开发权限条件管理模块，包括 API 层、列表/表单页面以及前端条件模板体验层。后端真实契约以 `code`、`name`、`conditionRules`、`enabled`、`description` 为准。

## User Story

As a permission administrator, I want 在前端管理权限条件, so that 我可以维护时间范围、组织归属等条件规则并在角色授权中复用。

## Problem → Solution

Backend 已提供权限条件的 list / detail / create / update / remove 能力，但 frontend 缺少对应 API 文件和页面组件 → 按后端真实 DTO 落地，并把条件模板选择器限制在前端包装层，不向后端伪造 `conditionType` 等字段。

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-phase1-development.plan.md`
- **Estimated Files**: 4
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan                                       | Relation     | Description                    |
| ------------------------------------------ | ------------ | ------------------------------ |
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | 先统一前端编码规范             |
| `frontend-perm-phase1-development.plan.md` | Parent       | Phase 1 聚合协调计划           |
| `frontend-perm-api-mock.plan.md`           | Downstream   | 页面与 API 稳定后纳入统一 mock |

---

## Mandatory Reading

| Priority | File                                                        | Why                                      |
| -------- | ----------------------------------------------------------- | ---------------------------------------- |
| P0       | `permission-center/.../controller/ConditionController.java` | 后端接口真相来源                         |
| P0       | `frontend-perm-backend-api-gap-tracker.md`                  | 查看条件模板与真实契约之间的差异处理方式 |
| P1       | `frontend/src/api/perm/role.ts`                             | API 文件模板                             |
| P1       | `frontend/src/views/perm/role/index.vue`                    | 页面组织方式参考                         |

---

## Backend Contract Snapshot

### Endpoints

- `POST /api/perm/permission-condition/list`
- `POST /api/perm/permission-condition/detail`
- `POST /api/perm/permission-condition/create`
- `POST /api/perm/permission-condition/update`
- `POST /api/perm/permission-condition/remove`

### Core Fields

- `ConditionItem`: `id`、`tenantId`、`code`、`name`、`conditionRules`、`enabled`、`description`、`createdAt`
- `ConditionCreateReq`: `code`、`name`、`conditionRules`、`enabled?`、`description?`
- `ConditionUpdateReq`: `conditionId`、`name?`、`conditionRules?`、`enabled?`、`description?`

### Frontend Wrapper Boundary

- 可以提供 `CONDITION_TEMPLATE_OPTIONS` 作为模板选择器。
- 模板选择器只负责生成或辅助编辑 `conditionRules`。
- 不得在 API 类型或提交负载中引入 `conditionType`、`status`、`config` 等旧字段。

---

## Files to Create

| File                                                             | Action | Justification    |
| ---------------------------------------------------------------- | ------ | ---------------- |
| `frontend/src/api/perm/condition.ts`                             | CREATE | 权限条件 API 层  |
| `frontend/src/views/perm/condition/index.vue`                    | CREATE | 权限条件管理页面 |
| `frontend/src/views/perm/condition/components/ConditionForm.vue` | CREATE | 条件表单组件     |
| `frontend/src/views/perm/condition/components/ConditionList.vue` | CREATE | 条件列表组件     |

---

## Step-by-Step Tasks

### Task 1: 创建 API 层

- **ACTION**: 创建 `frontend/src/api/perm/condition.ts`
- **IMPLEMENT**:
  - 定义 `ConditionItem`、`ConditionListResult`、`ConditionDetailResult`
  - 定义 `ConditionCreateRequest` 和 `ConditionUpdateRequest`
  - 导出 `getConditionList`、`getConditionDetail`、`createCondition`、`updateCondition`、`deleteCondition`
  - 额外导出 `CONDITION_TEMPLATE_OPTIONS` 作为前端模板元数据
- **VALIDATE**: `pnpm typecheck`

### Task 2: 创建列表和表单组件

- **ACTION**: 创建 `ConditionList.vue` 和 `ConditionForm.vue`
- **IMPLEMENT**:
  - 列表展示字段使用 `code`、`name`、`enabled`、`description`、`createdAt`
  - 表单字段直接映射到 `code`、`name`、`conditionRules`、`enabled`、`description`
  - 模板选择器用于辅助生成 `conditionRules` 初始 JSON
  - 提交前校验 `conditionRules` 的 JSON 格式

### Task 3: 创建管理页面

- **ACTION**: 创建 `views/perm/condition/index.vue`
- **IMPLEMENT**:
  - 列表 + 详情/说明布局
  - 新增、编辑、删除流程
  - 空态、加载态、错误态提示
  - 按权限码控制新增/编辑/删除按钮

### Task 4: 更新权限码和路由

- **ACTION**: 补充权限码与路由入口
- **IMPLEMENT**:
  - 在 `constants/permission.ts` 中补充 `perm:condition:view/create/update/delete`
  - 在 `router/modules/perm.ts` 中增加 `/perm/condition`

### Task 5: 准备统一 mock 交付

- **ACTION**: 为最终 mock 计划输出条件模块场景
- **IMPLEMENT**:
  - 提供至少 1 组正常条件数据
  - 提供至少 1 组禁用条件数据
  - 提供至少 1 组空列表或空详情场景
  - 把这些场景登记到 `frontend-perm-api-mock.plan.md`

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

| Test             | Input                                         | Expected Output                                                       |
| ---------------- | --------------------------------------------- | --------------------------------------------------------------------- |
| Create condition | Fill `code/name/conditionRules` and submit    | Create success                                                        |
| Edit condition   | Change `conditionRules` or `enabled` and save | Update success                                                        |
| Template mode    | Select template                               | Generate local `conditionRules` draft without changing payload schema |
| Delete condition | Click delete                                  | Confirm then delete success                                           |

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

- [ ] `frontend/src/api/perm/condition.ts` 创建完成，且契约与后端 DTO 一致
- [ ] `frontend/src/views/perm/condition/` 页面组件创建完成
- [ ] 模板选择器明确标记为前端包装层，不污染后端提交字段
- [ ] 权限码补充完整，路由配置更新完成
- [ ] 已为 `frontend-perm-api-mock.plan.md` 输出条件模块 mock 场景
- [ ] 全量编译通过

---

## Risks

| Risk                         | Likelihood | Impact | Mitigation                                     |
| ---------------------------- | ---------- | ------ | ---------------------------------------------- |
| 条件模板再次被误当作后端字段 | Medium     | High   | 文档和代码都以 `conditionRules` 为唯一提交载体 |
| JSON 规则编辑体验复杂        | Medium     | Medium | 先提供模板与基础校验，再逐步增强编辑器体验     |
| mock 数据不可读              | Medium     | Medium | 使用稳定、可解释的规则示例而不是随机数据       |
