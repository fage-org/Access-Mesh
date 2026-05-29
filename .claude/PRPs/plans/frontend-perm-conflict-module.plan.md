# Plan: Conflict Rule Management Module

## Summary

Develop the conflict rule management module for the frontend, including the API layer, CRUD pages, and the conflict detection capability currently supported by the backend.

## User Story

As a permission administrator, I want to manage conflict rules and detect permission conflicts, so that I can prevent users from having conflicting roles and maintain permission integrity.

## Problem → Solution

Backend provides conflict rule CRUD and a rule-level detect endpoint, but frontend lacks corresponding API files and management pages → Develop the missing API layer and page components for the currently supported scope.

## Current Scope

- Current scope includes conflict rule CRUD.
- Current scope includes detect by operation-permission pair.
- User/role-level conflict diagnosis is future scope and should be tracked separately.

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-phase2-development.plan.md`
- **Estimated Files**: 5
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan                                       | Relation     | Description                       |
| ------------------------------------------ | ------------ | --------------------------------- |
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |
| `frontend-perm-phase1-development.plan.md` | Prerequisite | Core features should be complete  |

---

## Mandatory Reading

| Priority | File                                                           | Why                   |
| -------- | -------------------------------------------------------------- | --------------------- |
| P0       | `permission-center/.../controller/ConflictRuleController.java` | Backend API reference |
| P1       | `frontend/src/api/perm/resource.ts`                            | API file template     |

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

| File                                                     | Action | Justification                 |
| -------------------------------------------------------- | ------ | ----------------------------- |
| `api/perm/conflictRule.ts`                               | CREATE | Conflict rule API layer       |
| `views/perm/conflict/index.vue`                          | CREATE | Conflict rule management page |
| `views/perm/conflict/components/ConflictRuleForm.vue`    | CREATE | Rule form component           |
| `views/perm/conflict/components/ConflictRuleList.vue`    | CREATE | Rule list component           |
| `views/perm/conflict/components/ConflictDetectPanel.vue` | CREATE | Conflict detection component  |

---

## Step-by-Step Tasks

### Task 1: Create Conflict Rule API (conflictRule.ts)

- **ACTION**: Create conflict rule management API file aligned with backend DTOs
- **IMPLEMENT**:

  ```typescript
  // api/perm/conflictRule.ts
  import { http } from "@/utils/http";

  export interface ConflictRuleItem {
    id: number;
    tenantId: number;
    conflictType: string;
    firstOperationPermissionId: number | null;
    secondOperationPermissionId: number | null;
    resourceTypeValue: number | null;
    firstAbstractRoleId: number | null;
    secondAbstractRoleId: number | null;
    description: string | null;
    createdAt: string;
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
    data: ConflictRuleItem;
  }

  export interface ConflictRuleCreateRequest {
    conflictType: string;
    firstOperationPermissionId?: number;
    secondOperationPermissionId?: number;
    resourceTypeValue?: number;
    firstAbstractRoleId?: number;
    secondAbstractRoleId?: number;
    description?: string;
  }

  export interface ConflictRuleUpdateRequest {
    id: number;
    conflictType?: string;
    firstOperationPermissionId?: number;
    secondOperationPermissionId?: number;
    resourceTypeValue?: number;
    firstAbstractRoleId?: number;
    secondAbstractRoleId?: number;
    description?: string;
  }

  export interface ConflictDetectRequest {
    firstOperationPermissionId: number;
    secondOperationPermissionId: number;
    resourceTypeValue?: number;
  }

  export interface ConflictDetectResult {
    success: boolean;
    data: {
      conflictDetected: boolean;
      matchedRules: Array<ConflictRuleItem>;
    };
  }

  export const getConflictRuleList = () => {
    return http.request<ConflictRuleListResult>(
      "post",
      "/api/perm/conflict-rule/list",
      { data: {} },
    );
  };

  export const getConflictRuleDetail = (data: { id: number }) => {
    return http.request<ConflictRuleDetailResult>(
      "post",
      "/api/perm/conflict-rule/detail",
      { data },
    );
  };

  export const createConflictRule = (data: ConflictRuleCreateRequest) => {
    return http.request<ConflictRuleActionResult>(
      "post",
      "/api/perm/conflict-rule/create",
      { data },
    );
  };

  export const updateConflictRule = (data: ConflictRuleUpdateRequest) => {
    return http.request<ConflictRuleActionResult>(
      "post",
      "/api/perm/conflict-rule/update",
      { data },
    );
  };

  export const deleteConflictRule = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/conflict-rule/remove",
      { data },
    );
  };

  export const detectConflict = (data: ConflictDetectRequest) => {
    return http.request<ConflictDetectResult>(
      "post",
      "/api/perm/conflict-rule/detect",
      { data },
    );
  };

  export const CONFLICT_RULE_TYPES = [
    { label: "角色互斥", value: "ROLE_MUTEX" },
    { label: "权限互斥", value: "PERM_MUTEX" },
  ];
  ```

- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Conflict Rule Form Component

- **ACTION**: Create rule form dialog component
- **IMPLEMENT**:
  - Support create and edit modes
  - Use selector inputs for operation permission IDs / role IDs / resource type value
  - Do not use JSON config editor in current scope

### Task 3: Create Conflict Detection Panel

- **ACTION**: Create conflict detection UI panel
- **IMPLEMENT**:
  - Select first / second operation permission IDs
  - Optional resource type value selector
  - Display `conflictDetected` and matched rules
  - Do not model user + role diagnostic inputs in current scope

### Task 4: Create Conflict Rule Management Page

- **ACTION**: Create conflict rule management page
- **IMPLEMENT**:
  - Left rule list
  - Right rule details
  - Conflict detection feature based on operation-permission pairs
- **CHECKLIST**:
  - [ ] Complete rule CRUD functionality
  - [ ] Detect panel matches current backend request / response shape
  - [ ] Future user/role diagnosis is marked out of scope

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

| Test            | Input                            | Expected Output           |
| --------------- | -------------------------------- | ------------------------- |
| Rule list       | Visit `/perm/conflict`           | Display rule list         |
| Rule create     | Submit form                      | Create success            |
| Conflict detect | Select two operation permissions | Show matched rules if any |

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

- [ ] `api/perm/conflictRule.ts` created with backend-aligned CRUD and detect
- [ ] `views/perm/conflict/` page components created
- [ ] Current scope is limited to rule management and rule-level detect
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk                            | Likelihood | Impact | Mitigation                            |
| ------------------------------- | ---------- | ------ | ------------------------------------- |
| Conflict rule config complexity | Medium     | Medium | Provide config templates and examples |
| JSON editor integration         | Low        | Low    | Use mature JSON editor component      |
