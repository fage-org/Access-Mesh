# Plan: Resource API Mapping Module

## Summary

Develop the resource API mapping management module for the frontend, including the API layer and integration into the resource management page.

## User Story

As a permission administrator, I want to manage API mappings for resources, so that I can control which HTTP endpoints require which permissions.

## Problem → Solution

Backend provides complete resource API mapping APIs, but frontend lacks corresponding API files and UI components → Develop the missing API layer and integrate into resource management page, while keeping display aliases separate from backend DTO fields.

## Metadata

- **Complexity**: Medium
- **Source PRD**: `frontend-perm-phase2-development.plan.md`
- **Estimated Files**: 3
- **Estimated Work**: 1-2 days

---

## Dependencies

| Plan                                       | Relation     | Description                       |
| ------------------------------------------ | ------------ | --------------------------------- |
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |
| `frontend-perm-phase1-development.plan.md` | Prerequisite | Core features should be complete  |

---

## Mandatory Reading

| Priority | File                                                                 | Why                             |
| -------- | -------------------------------------------------------------------- | ------------------------------- |
| P0       | `permission-center/.../controller/ResourceApiMappingController.java` | Backend API reference           |
| P1       | `frontend/src/api/perm/resource.ts`                                  | API file template               |
| P1       | `frontend/src/views/perm/resource/index.vue`                         | Resource page to integrate with |

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

| File                                                 | Action | Justification               |
| ---------------------------------------------------- | ------ | --------------------------- |
| `api/perm/apiMapping.ts`                             | CREATE | API mapping API layer       |
| `views/perm/resource/components/ApiMappingPanel.vue` | CREATE | API mapping panel component |
| `views/perm/resource/components/ApiMappingForm.vue`  | CREATE | API mapping form component  |

---

## Step-by-Step Tasks

### Task 1: Create Resource API Mapping API (apiMapping.ts)

- **ACTION**: Create API mapping API file aligned with backend DTOs
- **IMPLEMENT**:

  ```typescript
  // api/perm/apiMapping.ts
  import { http } from "@/utils/http";

  export interface ApiMappingItem {
    id: number;
    tenantId: number;
    resourceEntityId: number;
    serviceCode: string;
    httpMethod: string;
    pathPattern: string;
    matchOrder: number | null;
    enabled: boolean | null;
    extra: string | null;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface ApiMappingListRequest {
    resourceId?: number;
    serviceCode?: string;
  }

  export interface ApiMappingListResult {
    success: boolean;
    data: {
      items: Array<ApiMappingItem>;
    };
  }

  export interface ApiMappingActionResult {
    success: boolean;
    data: ApiMappingItem;
  }

  export interface ApiMappingCreateRequest {
    resourceId: number;
    serviceCode: string;
    httpMethod: string;
    pathPattern: string;
    matchOrder?: number;
    enabled?: boolean;
    extra?: string;
  }

  export interface ApiMappingUpdateRequest {
    resourceId: number;
    mappingId: number;
    httpMethod?: string;
    pathPattern?: string;
    matchOrder?: number;
    enabled?: boolean;
    extra?: string;
  }

  export const getApiMappingList = (data: ApiMappingListRequest) => {
    return http.request<ApiMappingListResult>(
      "post",
      "/api/perm/resource-api-mapping/list",
      { data },
    );
  };

  export const createApiMapping = (data: ApiMappingCreateRequest) => {
    return http.request<ApiMappingActionResult>(
      "post",
      "/api/perm/resource-api-mapping/create",
      { data },
    );
  };

  export const updateApiMapping = (data: ApiMappingUpdateRequest) => {
    return http.request<ApiMappingActionResult>(
      "post",
      "/api/perm/resource-api-mapping/update",
      { data },
    );
  };

  export const deleteApiMapping = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/resource-api-mapping/remove",
      { data },
    );
  };

  export const HTTP_METHODS = [
    { label: "GET", value: "GET" },
    { label: "POST", value: "POST" },
    { label: "PUT", value: "PUT" },
    { label: "DELETE", value: "DELETE" },
    { label: "PATCH", value: "PATCH" },
  ];
  ```

- **NOTE**: `resourceCode`、`resourceName` 和展示型描述由当前资源上下文与前端元数据补齐，不要求映射接口直接返回这些字段。
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create API Mapping Panel Component

- **ACTION**: Create API mapping panel for resource detail page
- **IMPLEMENT**:
  - Display list of API mappings for selected resource
  - Add/Edit/Delete buttons
  - HTTP method display with tags
  - Show `pathPattern`, `matchOrder`, `enabled`

### Task 3: Create API Mapping Form Component

- **ACTION**: Create API mapping form dialog
- **IMPLEMENT**:
  - Service code input
  - HTTP method selector
  - `pathPattern` input with validation
  - Optional `matchOrder`, `enabled`, `extra`

### Task 4: Integrate into Resource Page

- **ACTION**: Add API mapping tab to resource management page
- **IMPLEMENT**:
  - Add "API Mapping" tab to resource detail page
  - Show API mappings for selected resource
  - Support add, edit, delete API mappings
- **CHECKLIST**:
  - [ ] API mapping list display aligns with backend DTO
  - [ ] Support adding new mapping
  - [ ] Support editing existing mapping
  - [ ] Support deleting mapping

### Task 5: Update Constants

- **ACTION**: Add permission codes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_API_MAPPING_VIEW = "perm:api-mapping:view";
  export const PERM_API_MAPPING_CREATE = "perm:api-mapping:create";
  export const PERM_API_MAPPING_UPDATE = "perm:api-mapping:update";
  export const PERM_API_MAPPING_DELETE = "perm:api-mapping:delete";
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

| Test             | Input                             | Expected Output             |
| ---------------- | --------------------------------- | --------------------------- |
| API mapping list | Select resource → API Mapping tab | Show mappings for resource  |
| Create mapping   | Fill form and submit              | Create success              |
| Delete mapping   | Click delete                      | Confirm then delete success |

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

- [ ] `api/perm/apiMapping.ts` created with backend-aligned contracts
- [ ] `views/perm/resource/components/ApiMappingPanel.vue` created
- [ ] `views/perm/resource/components/ApiMappingForm.vue` created
- [ ] Resource management page integrated with API mapping tab
- [ ] Display aliases come from resource context, not fabricated backend fields
- [ ] Permission codes added to constants
- [ ] Full compilation passes

---

## Risks

| Risk            | Likelihood | Impact | Mitigation                           |
| --------------- | ---------- | ------ | ------------------------------------ |
| Path validation | Low        | Low    | Use regex validation for path format |
