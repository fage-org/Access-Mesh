# Plan: Permission View Enhancement

## Summary

Enhance the permission view page with complete API methods and multiple sub-views including effective roles, resource permissions, recent changes, and user resource tree, while keeping the API contract aligned with the current backend controller.

## User Story

As a permission administrator, I want a comprehensive permission view dashboard, so that I can understand the complete permission landscape from multiple perspectives (user roles, resource access, recent changes).

## Problem → Solution

Backend provides permission view APIs, but `permissionView.ts` only has partial methods and the view page lacks multiple sub-views → Complete the API methods and add all sub-view components using the current backend request / response shape, with optional frontend wrapper behavior where useful.

## Current Scope

- Current scope covers `effective-roles`, `resource-users`, `resource-tree`, and `recent-changes` based on the current backend controller and DTOs.
- The page may provide local pagination, quick time filters, and label mapping.
- Richer fields that cannot be derived from current responses stay out of scope.

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-phase3-development.plan.md`
- **Estimated Files**: 6
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan                                                  | Relation        | Description                        |
| ----------------------------------------------------- | --------------- | ---------------------------------- |
| `frontend-perm-structure-analysis.plan.md`            | Prerequisite    | Code standards analysis completed  |
| `frontend-perm-completed-modules-improvement.plan.md` | Partial overlap | May have initial permissionView.ts |

---

## Mandatory Reading

| Priority | File                                                             | Why                           |
| -------- | ---------------------------------------------------------------- | ----------------------------- |
| P0       | `permission-center/.../controller/PermissionViewController.java` | Backend API reference         |
| P1       | `frontend/src/api/perm/permissionView.ts`                        | Existing API file to enhance  |
| P1       | `frontend/src/views/perm/view/index.vue`                         | Existing view page to enhance |

---

## Patterns to Mirror

### API Enhancement Pattern

Add missing methods to existing API file following the same pattern.

---

## Files to Create/Update

| File                                                      | Action | Justification                         |
| --------------------------------------------------------- | ------ | ------------------------------------- |
| `api/perm/permissionView.ts`                              | UPDATE | Add missing API methods               |
| `views/perm/view/components/EffectiveRolesPanel.vue`      | CREATE | Effective roles view                  |
| `views/perm/view/components/ResourcePermissionsPanel.vue` | CREATE | Resource permission distribution view |
| `views/perm/view/components/RecentChangesPanel.vue`       | CREATE | Recent changes view                   |
| `views/perm/view/components/UserResourceTreePanel.vue`    | CREATE | User resource tree view               |
| `views/perm/view/index.vue`                               | UPDATE | Add tabs for all sub-views            |

---

## Step-by-Step Tasks

### Task 1: Complete Permission View API (permissionView.ts)

- **ACTION**: Add missing methods to permissionView.ts
- **IMPLEMENT**:

  ```typescript
  // Add to api/perm/permissionView.ts

  export interface UserEffectiveRolesRequest {
    subjectTypeCode: string;
    subjectExternalId: string;
    domainCode?: string;
  }

  export interface EffectiveRoleItem {
    roleTypeCode: string;
    roleExternalId: string;
    roleName: string;
  }

  export interface EffectiveRolesResult {
    success: boolean;
    data: {
      items: Array<EffectiveRoleItem>;
    };
  }

  export const getEffectiveRoles = (data: UserEffectiveRolesRequest) => {
    return http.request<EffectiveRolesResult>(
      "post",
      "/api/perm/permission-view/effective-roles",
      { data },
    );
  };

  export interface ResourcePermissionViewRequest {
    domainCode?: string;
    resourceTypeCode: string;
    resourceCode: string;
    codeType?: string;
  }

  export interface ResourcePermissionItem {
    resourceEntityId: number;
    resourceCode: string;
    resourceName: string;
    roles: Array<{
      roleId: number;
      roleName: string;
      roleTypeCode: string;
      operations: Array<string>;
      grantSource: string;
    }>;
  }

  export interface ResourcePermissionsResult {
    success: boolean;
    data: ResourcePermissionItem;
  }

  export const getResourcePermissions = (
    data: ResourcePermissionViewRequest,
  ) => {
    return http.request<ResourcePermissionsResult>(
      "post",
      "/api/perm/permission-view/resource-users",
      { data },
    );
  };

  export interface UserResourceTreeRequest {
    subjectTypeCode: string;
    subjectExternalId: string;
    domainCode?: string;
    resourceTypeCodes?: Array<string>;
    operationCodes?: Array<string>;
    resourceKeyword?: string;
  }

  export interface UserResourceTreeItem {
    resourceEntityId: number;
    domainCode: string;
    resourceCode: string;
    resourceName: string;
    resourceTypeCode: string;
    codeType: string;
    scopeAll: boolean;
    operationCodes: Array<string>;
    children?: Array<UserResourceTreeItem>;
  }

  export interface UserResourceTreeResult {
    success: boolean;
    data: {
      items: Array<UserResourceTreeItem>;
    };
  }

  export const getUserResourceTree = (data: UserResourceTreeRequest) => {
    return http.request<UserResourceTreeResult>(
      "post",
      "/api/perm/permission-view/resource-tree",
      { data },
    );
  };

  export interface RecentChangesRequest {
    targetType: string;
    subjectTypeCode?: string;
    subjectExternalId?: string;
    roleTypeCode?: string;
    roleExternalId?: string;
    domainCode?: string;
    since?: string;
    until?: string;
    eventTypes?: Array<string>;
    pageNum?: number;
    pageSize?: number;
  }

  export interface RecentChangesResult {
    success: boolean;
    data: {
      items: Array<Record<string, unknown>>;
      total: number;
      pageNum: number;
      pageSize: number;
      hasNext: boolean;
    };
  }

  export const getRecentChanges = (data: RecentChangesRequest) => {
    return http.request<RecentChangesResult>(
      "post",
      "/api/perm/permission-view/recent-changes",
      { data },
    );
  };
  ```

- **NOTE**: 页面可以做本地分页、7/30/90 天快捷筛选和文案映射，但这些属于前端包装层，不代表后端已有对应字段。
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Effective Roles Panel

- **ACTION**: Create effective roles view component
- **IMPLEMENT**:
  - User selector (subject type + external ID)
  - Domain filter (optional)
  - Local pagination if list is large
  - Do not assume backend returns `via` / `source`

### Task 3: Create Resource Permissions Panel

- **ACTION**: Create resource permission distribution view
- **IMPLEMENT**:
  - Resource type selector
  - Resource selector (by code)
  - Table showing granted roles on this resource
  - Operation codes display
  - Grant source display

### Task 4: Create Recent Changes Panel

- **ACTION**: Create recent changes view
- **IMPLEMENT**:
  - Target type selector
  - Quick filters map 7/30/90 天 to `since` / `until`
  - Use backend pagination fields for table rendering

### Task 5: Create User Resource Tree Panel

- **ACTION**: Create user resource tree view
- **IMPLEMENT**:
  - User selector
  - Frontend quick filters map to `resourceTypeCodes`, `operationCodes`, `resourceKeyword`
  - Tree display includes `scopeAll` and `codeType` where useful

### Task 6: Update Permission View Page

- **ACTION**: Complete permission view page with all current-scope sub-views
- **IMPLEMENT**:
  - Use Tabs component to organize multiple views
  - Effective roles view with optional local pagination
  - Resource permission distribution with filters
  - Recent changes with current-contract pagination
  - User resource tree with expand/collapse
- **CHECKLIST**:
  - [ ] Use Tabs component for view organization
  - [ ] Effective roles view uses current backend list shape
  - [ ] Resource permission distribution displays granted roles rather than fabricated user data
  - [ ] Recent changes uses `targetType` + paging model
  - [ ] User resource tree supports expand/collapse and quick filters

### Task 7: Update Constants

- **ACTION**: Add fine-grained permission codes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_VIEW_EFFECTIVE_ROLES = "perm:view:effective-roles";
  export const PERM_VIEW_RESOURCE_PERMISSIONS =
    "perm:view:resource-permissions";
  export const PERM_VIEW_RECENT_CHANGES = "perm:view:recent-changes";
  export const PERM_VIEW_USER_RESOURCE_TREE = "perm:view:user-resource-tree";
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

| Test                 | Input                      | Expected Output                    |
| -------------------- | -------------------------- | ---------------------------------- |
| Effective roles      | Select user                | Show user's effective roles        |
| Resource permissions | Select resource            | Show granted roles on the resource |
| Recent changes       | Select target + time range | Show paged changes in timeframe    |
| User resource tree   | Select user                | Show resource tree                 |

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

- [ ] `api/perm/permissionView.ts` complete with backend-aligned API methods
- [ ] `views/perm/view/` contains all current-scope sub-view components
- [ ] Permission view page has all current-scope sub-views
- [ ] Frontend wrapper behavior is explicitly separated from backend contract assumptions
- [ ] Permission codes added to constants
- [ ] Full compilation passes

---

## Risks

| Risk                      | Likelihood | Impact | Mitigation                                           |
| ------------------------- | ---------- | ------ | ---------------------------------------------------- |
| Large data volume         | Medium     | Medium | Support pagination and lazy loading                  |
| Tree component complexity | Medium     | Low    | Use existing tree component or simple implementation |
