# Plan: Permission View Enhancement

## Summary

Enhance the permission view page with complete API methods and multiple sub-views including effective roles, resource permissions, recent changes, and user resource tree.

## User Story

As a permission administrator, I want a comprehensive permission view dashboard, so that I can understand the complete permission landscape from multiple perspectives (user roles, resource access, recent changes).

## Problem → Solution

Backend provides complete permission view APIs, but `permissionView.ts` only has partial methods and the view page lacks multiple sub-views → Complete the API methods and add all sub-view components.

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-phase3-development.plan.md`
- **Estimated Files**: 6
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |
| `frontend-perm-completed-modules-improvement.plan.md` | Partial overlap | May have initial permissionView.ts |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/PermissionViewController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/permissionView.ts` | Existing API file to enhance |
| P1 | `frontend/src/views/perm/view/index.vue` | Existing view page to enhance |

---

## Patterns to Mirror

### API Enhancement Pattern

Add missing methods to existing API file following the same pattern.

---

## Files to Create/Update

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/permissionView.ts` | UPDATE | Add missing API methods |
| `views/perm/view/components/EffectiveRolesPanel.vue` | CREATE | Effective roles view |
| `views/perm/view/components/ResourcePermissionsPanel.vue` | CREATE | Resource permission distribution view |
| `views/perm/view/components/RecentChangesPanel.vue` | CREATE | Recent changes view |
| `views/perm/view/components/UserResourceTreePanel.vue` | CREATE | User resource tree view |
| `views/perm/view/index.vue` | UPDATE | Add tabs for all sub-views |

---

## Step-by-Step Tasks

### Task 1: Complete Permission View API (permissionView.ts)

- **ACTION**: Add missing methods to permissionView.ts
- **IMPLEMENT**:
  ```typescript
  // Add to api/perm/permissionView.ts

  // Query effective roles
  export interface UserEffectiveRolesRequest {
    subjectTypeCode?: string;
    subjectExternalId?: string;
    domainCode?: string;
    pageNum?: number;
    pageSize?: number;
  }

  export interface EffectiveRoleItem {
    roleTypeCode: string;
    roleExternalId: string;
    roleName: string;
    via: Array<string>;
    source: string;
  }

  export interface EffectiveRolesResult {
    success: boolean;
    data: {
      items: Array<EffectiveRoleItem>;
      total: number;
    };
  }

  export const getEffectiveRoles = (data: UserEffectiveRolesRequest) => {
    return http.request<EffectiveRolesResult>(
      "post",
      "/api/perm/permission-view/effective-roles",
      { data }
    );
  };

  // Query resource permission distribution
  export interface ResourcePermissionViewRequest {
    domainCode?: string;
    resourceTypeCode: string;
    resourceCode: string;
    codeType?: string;
  }

  export interface ResourcePermissionItem {
    resourceTypeCode: string;
    resourceCode: string;
    resourceName: string;
    users: Array<{
      subjectTypeCode: string;
      subjectExternalId: string;
      username: string;
      operationCodes: Array<string>;
    }>;
  }

  export interface ResourcePermissionsResult {
    success: boolean;
    data: ResourcePermissionItem;
  }

  export const getResourcePermissions = (data: ResourcePermissionViewRequest) => {
    return http.request<ResourcePermissionsResult>(
      "post",
      "/api/perm/permission-view/resource-users",
      { data }
    );
  };

  // Query user resource tree
  export interface UserResourceTreeRequest {
    subjectTypeCode: string;
    subjectExternalId: string;
    domainCode?: string;
    resourceTypeCode?: string;
  }

  export interface UserResourceTreeItem {
    resourceTypeCode: string;
    resourceCode: string;
    resourceName: string;
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
      { data }
    );
  };

  // Query recent changes
  export interface RecentChangesRequest {
    days?: number;
    resourceTypeCode?: string;
  }

  export interface RecentChangesResult {
    success: boolean;
    data: {
      items: Array<{
        entityType: string;
        entityId: number;
        changeType: string;
        operatorId: number;
        createdAt: string;
      }>;
    };
  }

  export const getRecentChanges = (data: RecentChangesRequest) => {
    return http.request<RecentChangesResult>(
      "post",
      "/api/perm/permission-view/recent-changes",
      { data }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Effective Roles Panel

- **ACTION**: Create effective roles view component
- **IMPLEMENT**:
  - User selector (subject type + external ID)
  - Domain filter (optional)
  - Table showing effective roles with inheritance path
  - Source indicator (direct, inherited, etc.)
  - Pagination support

### Task 3: Create Resource Permissions Panel

- **ACTION**: Create resource permission distribution view
- **IMPLEMENT**:
  - Resource type selector
  - Resource selector (by code)
  - Table showing users with permissions on this resource
  - Operation codes display
  - Filter by operation

### Task 4: Create Recent Changes Panel

- **ACTION**: Create recent changes view
- **IMPLEMENT**:
  - Days filter (default 7, 30, 90 days)
  - Resource type filter
  - Timeline or table display of changes
  - Change type indicators

### Task 5: Create User Resource Tree Panel

- **ACTION**: Create user resource tree view
- **IMPLEMENT**:
  - User selector
  - Resource type filter
  - Tree display of resources with operation codes
  - Expand/collapse functionality
  - Search within tree

### Task 6: Update Permission View Page

- **ACTION**: Complete permission view page with all sub-views
- **IMPLEMENT**:
  - Use Tabs component to organize multiple views
  - Effective roles view with pagination
  - Resource permission distribution with filters
  - Recent changes with days filter
  - User resource tree with expand/collapse
- **CHECKLIST**:
  - [ ] Use Tabs component for view organization
  - [ ] Effective roles view with pagination
  - [ ] Resource permission distribution with filtering
  - [ ] Recent changes with days filter
  - [ ] User resource tree with expand/collapse

### Task 7: Update Constants

- **ACTION**: Add fine-grained permission codes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_VIEW_EFFECTIVE_ROLES = "perm:view:effective-roles";
  export const PERM_VIEW_RESOURCE_PERMISSIONS = "perm:view:resource-permissions";
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

| Test | Input | Expected Output |
|------|-------|-----------------|
| Effective roles | Select user | Show user's effective roles |
| Resource permissions | Select resource | Show users with permissions |
| Recent changes | Select days filter | Show changes in timeframe |
| User resource tree | Select user | Show resource tree |

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

- [ ] `api/perm/permissionView.ts` complete with all API methods
- [ ] `views/perm/view/` contains all sub-view components
- [ ] Permission view page has all sub-views
- [ ] Permission codes added to constants
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Large data volume | Medium | Medium | Support pagination and lazy loading |
| Tree component complexity | Medium | Low | Use existing tree component or simple implementation |
