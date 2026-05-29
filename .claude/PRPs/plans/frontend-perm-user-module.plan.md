# Plan: Abstract User Management Module

## Summary

Develop the abstract user management module for the frontend, including the API layer and complete CRUD page components with user synchronization functionality.

## User Story

As a permission administrator, I want to manage abstract users through the frontend interface, so that I can synchronize users from external systems and manage their basic information.

## Problem → Solution

Backend provides complete abstract user APIs (list, sync, create, update, delete), but frontend lacks corresponding API files and management pages → Develop the missing API layer and page components, while keeping UI labels separate from backend field names.

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-phase1-development.plan.md`
- **Estimated Files**: 4
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan                                       | Relation     | Description                       |
| ------------------------------------------ | ------------ | --------------------------------- |
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |

---

## Mandatory Reading

| Priority | File                                                   | Why                     |
| -------- | ------------------------------------------------------ | ----------------------- |
| P0       | `permission-center/.../controller/UserController.java` | Backend API reference   |
| P1       | `frontend/src/api/perm/role.ts`                        | API file template       |
| P1       | `frontend/src/views/perm/role/index.vue`               | Page template reference |

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

// 4. Constants
export const XXX_STATUS_TAG = { ... };
```

---

## Files to Create

| File                                      | Action | Justification           |
| ----------------------------------------- | ------ | ----------------------- |
| `api/perm/user.ts`                        | CREATE | Abstract user API layer |
| `views/perm/user/index.vue`               | CREATE | User management page    |
| `views/perm/user/components/UserForm.vue` | CREATE | User form component     |
| `views/perm/user/components/UserList.vue` | CREATE | User list component     |

---

## Step-by-Step Tasks

### Task 1: Create Abstract User API (user.ts)

- **ACTION**: Create abstract user management API file aligned with backend DTOs
- **IMPLEMENT**:

  ```typescript
  // api/perm/user.ts
  import { http } from "@/utils/http";

  export interface UserItem {
    id: number;
    tenantId: number;
    subjectTypeCode: string;
    externalId: string;
    name: string;
    enabled: boolean | null;
    extra: string | null;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface UserListResult {
    success: boolean;
    data: {
      items: Array<UserItem>;
      total: number;
      pageNum: number;
      pageSize: number;
      hasNext: boolean;
    };
  }

  export interface UserDetailResult {
    success: boolean;
    data: UserItem;
  }

  export interface UserActionResult {
    success: boolean;
    data: UserItem;
  }

  export interface UserSyncRequest {
    subjectTypeCode: string;
    externalId: string;
    name?: string;
    enabled?: boolean;
    extra?: string;
    version: string;
  }

  export interface UserCreateRequest {
    subjectTypeCode: string;
    externalId: string;
    name?: string;
    enabled?: boolean;
    extra?: string;
  }

  export interface UserUpdateRequest {
    userId: number;
    name?: string;
    enabled?: boolean;
    extra?: string;
  }

  export const getUserList = (data: {
    subjectTypeCode?: string;
    domainCode?: string;
    keyword?: string;
    pageNum?: number;
    pageSize?: number;
    sort?: string;
  }) => {
    return http.request<UserListResult>(
      "post",
      "/api/perm/abstract-user/list",
      { data },
    );
  };

  export const getUserDetail = (data: { id: number }) => {
    return http.request<UserDetailResult>(
      "post",
      "/api/perm/abstract-user/detail",
      { data },
    );
  };

  export const syncUser = (data: UserSyncRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/sync",
      { data },
    );
  };

  export const createUser = (data: UserCreateRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/create",
      { data },
    );
  };

  export const updateUser = (data: UserUpdateRequest) => {
    return http.request<UserActionResult>(
      "post",
      "/api/perm/abstract-user/update",
      { data },
    );
  };

  export const deleteUser = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/abstract-user/remove",
      { data },
    );
  };
  ```

- **NOTE**: 页面可以展示“用户名/显示名”等友好文案，但接口字段必须保持 `name`、`enabled`、`userId`、`version`。
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create User List Component

- **ACTION**: Create user list component with pagination
- **IMPLEMENT**:
  - Support pagination
  - Support keyword search
  - Subject type filtering
  - Display `enabled` instead of旧 `status`

### Task 3: Create User Form Component

- **ACTION**: Create user form dialog component
- **IMPLEMENT**:
  - Support create and edit modes
  - Support sync mode with explicit `version` input or externally injected version
  - Form fields map directly to backend request DTOs

### Task 4: Create User Management Page

- **ACTION**: Create abstract user management page
- **IMPLEMENT**:
  - Page layout with search/filter bar
  - User list with pagination
  - CRUD operations
  - User sync functionality with explicit version handling
- **CHECKLIST**:
  - [ ] Support pagination query
  - [ ] Support keyword search
  - [ ] Support user sync functionality with `version`
  - [ ] Support CRUD operations

### Task 5: Update Constants and Router

- **ACTION**: Add permission codes and routes
- **IMPLEMENT**:

  ```typescript
  // constants/permission.ts
  export const PERM_USER_VIEW = "perm:user:view";
  export const PERM_USER_CREATE = "perm:user:create";
  export const PERM_USER_UPDATE = "perm:user:update";
  export const PERM_USER_DELETE = "perm:user:delete";
  export const PERM_USER_SYNC = "perm:user:sync";

  // router/modules/perm.ts
  {
    path: "/perm/user",
    name: "PermUser",
    component: () => import("@/views/perm/user/index.vue"),
    meta: {
      title: "Abstract User",
      auths: [PERM_CODES.PERM_USER_VIEW]
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

| Test        | Input                           | Expected Output             |
| ----------- | ------------------------------- | --------------------------- |
| User list   | Visit `/perm/user`              | Display paginated user list |
| User sync   | Submit sync form with `version` | Sync success                |
| User create | Submit form                     | Create success              |
| User delete | Click delete                    | Confirm then delete success |

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

- [ ] `api/perm/user.ts` created with backend-aligned CRUD and sync
- [ ] `views/perm/user/` page components created
- [ ] Sync mode的 `version` 来源已明确记录
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk                          | Likelihood | Impact | Mitigation                               |
| ----------------------------- | ---------- | ------ | ---------------------------------------- |
| Backend API changes           | Low        | High   | Confirm API stability before development |
| Sync functionality complexity | Low        | Medium | Clear form design for sync parameters    |
