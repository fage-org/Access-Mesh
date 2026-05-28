# Plan: Conflict Rule Management Module

## Summary

Develop the conflict rule management module for the frontend, including the API layer, CRUD pages, and conflict detection functionality.

## User Story

As a permission administrator, I want to manage conflict rules and detect permission conflicts, so that I can prevent users from having conflicting roles and maintain permission integrity.

## Problem → Solution

Backend provides complete conflict rule APIs and conflict detection, but frontend lacks corresponding API files and management pages → Develop the missing API layer and page components.

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-phase2-development.plan.md`
- **Estimated Files**: 5
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |
| `frontend-perm-phase1-development.plan.md` | Prerequisite | Core features should be complete |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/ConflictRuleController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/resource.ts` | API file template |

---

## Patterns to Mirror

### API_FILE_STRUCTURE

```typescript
// Source: frontend/src/api/perm/resource.ts

export interface XxxItem { ... }
export interface XxxResult { ... }
export interface XxxRequest { ... }

export const getXxxList = (data?: XxxRequest) => {
  return http.request<XxxResult>("post", "/api/perm/xxx/list", { data });
};
```

---

## Files to Create

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/conflictRule.ts` | CREATE | Conflict rule API layer |
| `views/perm/conflict/index.vue` | CREATE | Conflict rule management page |
| `views/perm/conflict/components/ConflictRuleForm.vue` | CREATE | Rule form component |
| `views/perm/conflict/components/ConflictRuleList.vue` | CREATE | Rule list component |
| `views/perm/conflict/components/ConflictDetectPanel.vue` | CREATE | Conflict detection component |

---

## Step-by-Step Tasks

### Task 1: Create Conflict Rule API (conflictRule.ts)

- **ACTION**: Create conflict rule management API file
- **IMPLEMENT**:
  ```typescript
  // api/perm/conflictRule.ts
  import { http } from "@/utils/http";

  export interface ConflictRuleItem {
    id: number;
    tenantId: number;
    ruleType: string;
    name: string;
    config: string; // JSON configuration
    description: string | null;
    priority: number;
    status: number;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface ConflictRuleListResult {
    success: boolean;
    data: {
      items: Array<ConflictRuleItem>;
    };
  }

  export interface ConflictRuleDetailResult {
    success: boolean;
    data: ConflictRuleItem;
  }

  export interface ConflictRuleActionResult {
    success: boolean;
    data: { id: number };
  }

  export interface ConflictDetectRequest {
    userId: number;
    roleIds: Array<number>;
  }

  export interface ConflictDetectResult {
    success: boolean;
    data: {
      hasConflict: boolean;
      conflicts: Array<{
        ruleId: number;
        ruleName: string;
        conflictType: string;
        affectedRoles: Array<{
          roleId: number;
          roleName: string;
        }>;
        suggestion: string;
      }>;
    };
  }

  // API functions
  export const getConflictRuleList = () => {
    return http.request<ConflictRuleListResult>(
      "post",
      "/api/perm/conflict-rule/list",
      { data: {} }
    );
  };

  export const getConflictRuleDetail = (data: { id: number }) => {
    return http.request<ConflictRuleDetailResult>(
      "post",
      "/api/perm/conflict-rule/detail",
      { data }
    );
  };

  export const createConflictRule = (data: {
    ruleType: string;
    name: string;
    config: string;
    description?: string;
    priority?: number;
    status?: number;
  }) => {
    return http.request<ConflictRuleActionResult>(
      "post",
      "/api/perm/conflict-rule/create",
      { data }
    );
  };

  export const updateConflictRule = (data: {
    id: number;
    ruleType?: string;
    name?: string;
    config?: string;
    description?: string;
    priority?: number;
    status?: number;
  }) => {
    return http.request<ConflictRuleActionResult>(
      "post",
      "/api/perm/conflict-rule/update",
      { data }
    );
  };

  export const deleteConflictRule = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/conflict-rule/remove",
      { data }
    );
  };

  export const detectConflict = (data: ConflictDetectRequest) => {
    return http.request<ConflictDetectResult>(
      "post",
      "/api/perm/conflict-rule/detect",
      { data }
    );
  };

  // Constants
  export const CONFLICT_RULE_TYPES = [
    { label: "角色互斥", value: "MUTEX" },
    { label: "权限合并", value: "MERGE" },
    { label: "拒绝优先", value: "DENY_FIRST" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Conflict Rule Form Component

- **ACTION**: Create rule form dialog component
- **IMPLEMENT**:
  - Support create and edit modes
  - JSON config editor with validation
  - Rule type selection
  - Priority input
  - Config template suggestions

### Task 3: Create Conflict Detection Panel

- **ACTION**: Create conflict detection UI panel
- **IMPLEMENT**:
  - User selection
  - Role multi-selection
  - Detection trigger button
  - Results display with conflict details
  - Suggestions for resolution

### Task 4: Create Conflict Rule Management Page

- **ACTION**: Create conflict rule management page
- **IMPLEMENT**:
  - Left rule list
  - Right rule details
  - Conflict detection feature (user + role selection)
- **CHECKLIST**:
  - [ ] Complete rule CRUD functionality
  - [ ] User-friendly conflict detection interface
  - [ ] Visualized conflict results display

### Task 5: Update Constants and Router

- **ACTION**: Add permission codes and routes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_CONFLICT_VIEW = "perm:conflict:view";
  export const PERM_CONFLICT_CREATE = "perm:conflict:create";
  export const PERM_CONFLICT_UPDATE = "perm:conflict:update";
  export const PERM_CONFLICT_DELETE = "perm:conflict:delete";
  export const PERM_CONFLICT_DETECT = "perm:conflict:detect";

  // router/modules/perm.ts
  {
    path: "/perm/conflict",
    name: "PermConflict",
    component: () => import("@/views/perm/conflict/index.vue"),
    meta: {
      title: "Conflict Rule",
      auths: [PERM_CODES.PERM_CONFLICT_VIEW]
    }
  }
  ```

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

| Test | Input | Expected Output |
|------|-------|-----------------|
| Rule list | Visit `/perm/conflict` | Display rule list |
| Rule create | Submit form | Create success |
| Conflict detect | Select user + roles | Show conflicts if any |

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

- [ ] `api/perm/conflictRule.ts` created with complete CRUD and detect
- [ ] `views/perm/conflict/` page components created
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Conflict rule config complexity | Medium | Medium | Provide config templates and examples |
| JSON editor integration | Low | Low | Use mature JSON editor component |
