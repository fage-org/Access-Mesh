# Plan: Log Audit Module

## Summary

Develop the log audit module for the frontend, including the API layer and audit log viewing pages for change logs and operation logs.

## User Story

As a system administrator, I want to view audit logs through the frontend interface, so that I can track permission changes and system operations for compliance and troubleshooting.

## Problem → Solution

Backend provides complete log query APIs (change logs, operation logs), but frontend lacks corresponding API files and viewing pages → Develop the missing API layer and page components.

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-phase1-development.plan.md`
- **Estimated Files**: 4
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/LogQueryController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/role.ts` | API file template |

---

## Patterns to Mirror

### API_FILE_STRUCTURE

```typescript
// Source: frontend/src/api/perm/role.ts

// 1. Type definitions (exported interfaces)
export interface XxxItem { ... }
export interface XxxResult { ... }
export interface XxxRequest { ... }

// 2. API functions
export const getXxxList = (data?: XxxRequest) => {
  return http.request<XxxResult>("post", "/api/perm/xxx/list", { data });
};

// 3. Utility functions
export const transformXxxResponse = (response: XxxResult) => { ... };
```

---

## Files to Create

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/log.ts` | CREATE | Log query API layer |
| `views/perm/log/index.vue` | CREATE | Log audit page |
| `views/perm/log/components/ChangeLogTable.vue` | CREATE | Change log component |
| `views/perm/log/components/OperationLogTable.vue` | CREATE | Operation log component |

---

## Step-by-Step Tasks

### Task 1: Create Log Query API (log.ts)

- **ACTION**: Create log query API file
- **IMPLEMENT**:
  ```typescript
  // api/perm/log.ts
  import { http } from "@/utils/http";

  // Change log types
  export interface ChangeLogItem {
    id: number;
    tenantId: number;
    entityType: string;
    entityId: number;
    eventType: string;
    changeType: string;
    diffSnapshot: string;
    operatorId: number;
    source: string;
    createdAt: string;
  }

  export interface ChangeLogListRequest {
    entityType?: string;
    entityId?: number;
    pageNum?: number;
    pageSize?: number;
  }

  export interface ChangeLogListResult {
    success: boolean;
    data: {
      items: Array<ChangeLogItem>;
      total: number;
      pageNum: number;
      pageSize: number;
    };
  }

  // Operation log types
  export interface OperationLogItem {
    id: number;
    tenantId: number;
    module: string;
    action: string;
    targetType: string;
    targetId: string;
    summary: string;
    operatorId: number;
    createdAt: string;
  }

  export interface OperationLogListRequest {
    module?: string;
    action?: string;
    pageNum?: number;
    pageSize?: number;
  }

  export interface OperationLogListResult {
    success: boolean;
    data: {
      items: Array<OperationLogItem>;
      total: number;
      pageNum: number;
      pageSize: number;
    };
  }

  // API functions
  export const getChangeLogs = (data: ChangeLogListRequest) => {
    return http.request<ChangeLogListResult>(
      "post",
      "/api/perm/log/change/list",
      { data }
    );
  };

  export const getOperationLogs = (data: OperationLogListRequest) => {
    return http.request<OperationLogListResult>(
      "post",
      "/api/perm/log/operation/list",
      { data }
    );
  };

  // Entity type options
  export const ENTITY_TYPE_OPTIONS = [
    { label: "抽象角色", value: "abstract_role" },
    { label: "资源实体", value: "resource_entity" },
    { label: "用户角色", value: "user_role" },
    { label: "角色权限", value: "role_permission" }
  ];

  // Module options
  export const MODULE_OPTIONS = [
    { label: "权限", value: "perm" },
    { label: "角色", value: "role" },
    { label: "资源", value: "resource" },
    { label: "用户", value: "user" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Change Log Table Component

- **ACTION**: Create change log table component
- **IMPLEMENT**:
  - Table display with entity type, entity ID, change type, operator, timestamp
  - Diff snapshot display (collapsible or modal)
  - Pagination support
  - Entity type/ID filtering

### Task 3: Create Operation Log Table Component

- **ACTION**: Create operation log table component
- **IMPLEMENT**:
  - Table display with module, action, target type, summary, operator, timestamp
  - Pagination support
  - Module/action filtering

### Task 4: Create Log Audit Page

- **ACTION**: Create log audit page with tab switching
- **IMPLEMENT**:
  - Tab component to switch between change logs and operation logs
  - Support pagination
  - Support conditional filtering
- **CHECKLIST**:
  - [ ] Use Tabs component to switch log types
  - [ ] Support filtering by entity type/ID for change logs
  - [ ] Support filtering by module/action for operation logs
  - [ ] Table displays complete log information

### Task 5: Update Constants and Router

- **ACTION**: Add permission codes and routes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_LOG_VIEW = "perm:log:view";

  // router/modules/perm.ts
  {
    path: "/perm/log",
    name: "PermLog",
    component: () => import("@/views/perm/log/index.vue"),
    meta: {
      title: "Log Audit",
      auths: [PERM_CODES.PERM_LOG_VIEW]
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
| Change logs | Visit `/perm/log` | Display change log list |
| Operation logs | Switch tab | Display operation log list |
| Filter by entity | Select entity type | Filtered results |

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

- [ ] `api/perm/log.ts` created with both log types
- [ ] `views/perm/log/` page components created
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Large log data volume | Medium | Medium | Implement pagination and lazy loading |
| Diff display complexity | Low | Low | Use JSON formatter for diff snapshot |
