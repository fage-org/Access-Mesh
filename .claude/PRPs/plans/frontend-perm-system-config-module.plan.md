# Plan: System Configuration Module

## Summary

Develop the system configuration management module for the frontend, including configuration list by category and configuration value editing.

## User Story

As a system administrator, I want to manage system configurations through the frontend interface, so that I can adjust system parameters like cache settings, security policies, and audit configurations.

## Problem → Solution

Backend provides complete system configuration APIs, but frontend lacks corresponding API files and management pages → Develop the missing API layer and page components.

## Metadata

- **Complexity**: Low
- **Source PRD**: `frontend-perm-phase3-development.plan.md`
- **Estimated Files**: 3
- **Estimated Work**: 1 day

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/SystemConfigController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/role.ts` | API file template |

---

## Patterns to Mirror

### API_FILE_STRUCTURE

```typescript
// Source: frontend/src/api/perm/role.ts

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
| `api/perm/systemConfig.ts` | CREATE | System configuration API layer |
| `views/perm/system-config/index.vue` | CREATE | System configuration page |
| `views/perm/system-config/components/ConfigEditor.vue` | CREATE | Configuration value editor component |

---

## Step-by-Step Tasks

### Task 1: Create System Configuration API (systemConfig.ts)

- **ACTION**: Create system configuration management API file
- **IMPLEMENT**:
  ```typescript
  // api/perm/systemConfig.ts
  import { http } from "@/utils/http";

  export interface SystemConfigItem {
    id: number;
    tenantId: number;
    configKey: string;
    configValue: string;
    description: string | null;
    category: string;
    editable: boolean;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface SystemConfigListRequest {
    category?: string;
    keyword?: string;
  }

  export interface SystemConfigListResult {
    success: boolean;
    data: {
      items: Array<SystemConfigItem>;
    };
  }

  export interface SystemConfigDetailRequest {
    configKey: string; // Backend uses configKey, not id
  }

  export interface SystemConfigDetailResult {
    success: boolean;
    data: SystemConfigItem;
  }

  export interface SystemConfigActionResult {
    success: boolean;
    data: SystemConfigItem;
  }

  export interface SystemConfigSaveRequest {
    configKey: string;
    configValue: string;
  }

  // API functions
  export const getSystemConfigList = () => {
    return http.request<SystemConfigListResult>(
      "post",
      "/api/perm/system-config/list",
      { data: {} }
    );
  };

  export const getSystemConfigDetail = (data: SystemConfigDetailRequest) => {
    return http.request<SystemConfigDetailResult>(
      "post",
      "/api/perm/system-config/detail",
      { data }
    );
  };

  // Backend uses /save endpoint for upsert (create or update)
  export const saveSystemConfig = (data: SystemConfigSaveRequest) => {
    return http.request<SystemConfigActionResult>(
      "post",
      "/api/perm/system-config/save",
      { data }
    );
  };

  // Config categories
  export const SYSTEM_CONFIG_CATEGORIES = [
    { label: "基础配置", value: "BASIC" },
    { label: "缓存配置", value: "CACHE" },
    { label: "安全配置", value: "SECURITY" },
    { label: "审计配置", value: "AUDIT" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` passes

### Task 2: Create Configuration Editor Component

- **ACTION**: Create configuration value editor component
- **IMPLEMENT**:
  - JSON format editor
  - Validation for JSON syntax
  - Save/Cancel buttons
  - Read-only indicator

### Task 3: Create System Configuration Page

- **ACTION**: Create system configuration management page
- **IMPLEMENT**:
  - Configuration list grouped by category
  - JSON format configuration value editing
  - Read-only config identification
- **CHECKLIST**:
  - [ ] Config list grouped by category
  - [ ] Support config value editing and saving
  - [ ] Read-only config has clear identification

### Task 4: Update Constants and Router

- **ACTION**: Add permission codes and routes
- **IMPLEMENT**:
  ```typescript
  // constants/permission.ts
  export const PERM_SYSTEM_CONFIG_VIEW = "perm:system-config:view";
  export const PERM_SYSTEM_CONFIG_SAVE = "perm:system-config:save";

  // router/modules/perm.ts
  {
    path: "/perm/system-config",
    name: "PermSystemConfig",
    component: () => import("@/views/perm/system-config/index.vue"),
    meta: {
      title: "System Config",
      auths: [PERM_CODES.PERM_SYSTEM_CONFIG_VIEW]
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
| Config list | Visit `/perm/system-config` | Display configs grouped by category |
| Config edit | Edit value and save | Save success |
| Read-only config | Try edit read-only | Edit disabled |

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

- [ ] `api/perm/systemConfig.ts` created
- [ ] `views/perm/system-config/` page components created
- [ ] Permission codes added to constants
- [ ] Router configuration updated
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| JSON editor integration | Low | Low | Use vue-json-viewer or simple textarea |
