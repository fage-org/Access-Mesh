# Plan: Permission Version Module

## Summary

Develop the permission version query module for the frontend. The backend currently only provides version query functionality for cache consistency checking.

## User Story

As a system administrator, I want to view permission version information, so that I can monitor cache consistency status and understand when permissions were last updated.

## Problem → Solution

Backend provides permission version query API (`/query`), primarily for Gateway cache consistency checking. Frontend may need a simple page to display version status → Develop a lightweight version query page based on actual backend capabilities.

## Metadata

- **Complexity**: Low
- **Source PRD**: `frontend-perm-phase3-development.plan.md`
- **Estimated Files**: 2-3
- **Estimated Work**: 0.5-1 day

---

## Backend API Status

**Current Backend Implementation** (PermissionVersionController):
- ✅ `/api/perm/permission-version/query` - Query version number (exists)
- ❌ `/list` - Not implemented
- ❌ `/detail` - Not implemented
- ❌ `/create` - Not implemented
- ❌ `/compare` - Not implemented
- ❌ `/rollback` - Not implemented

> **Note**: Permission version is primarily used for Gateway cache consistency. Version increments automatically when role permissions change. Full version management (snapshot, compare, rollback) would require backend enhancement first.

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/PermissionVersionController.java` | Backend API reference (only `/query`) |
| P1 | `frontend/src/api/perm/role.ts` | API file template |

---

## Files to Create

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/version.ts` | CREATE | Version query API layer |
| `views/perm/version/index.vue` | CREATE | Version query page (optional) |

---

## Step-by-Step Tasks

### Task 1: Create Permission Version API (version.ts)

- **ACTION**: Create permission version query API file (based on actual backend)
- **IMPLEMENT**:
  ```typescript
  // api/perm/version.ts
  import { http } from "@/utils/http";

  // Version query request
  export interface PermissionVersionQueryRequest {
    roleId?: number; // Optional: query specific role version
  }

  // Version response (from backend PermissionVersionResp)
  export interface PermissionVersionResp {
    version: number;
    updatedAt: string;
    roleId?: number;
  }

  export interface VersionQueryResult {
    success: boolean;
    data: PermissionVersionResp;
  }

  // API function - matches backend /query endpoint
  export const queryVersion = (data?: PermissionVersionQueryRequest) => {
    return http.request<VersionQueryResult>(
      "post",
      "/api/perm/permission-version/query",
      { data: data || {} }
    );
  };
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Version Query Page (Optional)

- **ACTION**: Create a simple version query page (optional feature)
- **IMPLEMENT**:
  - Display current permission version number
  - Show last update timestamp
  - Optional: query by specific role ID
- **CHECKLIST**:
  - [ ] Version number display
  - [ ] Update timestamp display
  - [ ] Optional role filter

### Task 3: Update Constants (Optional)

- **ACTION**: Add permission code if page is created
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts (optional)
  export const PERM_VERSION_VIEW = "perm:version:view";
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
| Version query | Call API | Return version number and timestamp |

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

- [ ] `api/perm/version.ts` created with `queryVersion` function
- [ ] API path `/api/perm/permission-version/query` matches backend
- [ ] Full compilation passes

---

## Future Enhancement Notes

The following features would require backend development first:
- Version list/snapshot management
- Version comparison (diff between versions)
- Version rollback functionality

If these features are needed, coordinate with backend team to implement corresponding endpoints in PermissionVersionController.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Backend API scope limited | High (confirmed) | Low | Adjust plan to match actual backend |
| Feature may not need frontend | Medium | Low | Consider if page is necessary |