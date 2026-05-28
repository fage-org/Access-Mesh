# Plan: Permission Explanation (Diagnosis) Module

## Summary

Develop the complete permission explanation and diagnosis page (`/perm/explain`), including permission analysis, role traceability, permission visualization charts, and comparison features.

## User Story

As a permission administrator, I want a complete permission diagnosis tool, so that I can understand why a user has or doesn't have certain permissions and compare permissions between different subjects.

## Problem → Solution

Backend provides permission explanation APIs, but frontend only has placeholder page → Develop the complete diagnosis system with visualization and comparison features.

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-phase1-development.plan.md`
- **Estimated Files**: 5
- **Estimated Work**: 2-3 days

---

## Dependencies

| Plan | Relation | Description |
|------|----------|-------------|
| `frontend-perm-structure-analysis.plan.md` | Prerequisite | Code standards analysis completed |
| `frontend-perm-completed-modules-improvement.plan.md` | Partial overlap | May use permissionView.ts APIs |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/PermissionViewController.java` | Backend API reference |
| P1 | `frontend/src/api/perm/permissionView.ts` | Existing permission view APIs |
| P1 | `frontend/src/views/perm/explain/index.vue` | Existing placeholder page |

---

## Patterns to Mirror

### API Integration

Uses existing `permissionView.ts` APIs:

```typescript
// From api/perm/permissionView.ts
export const explainPermission = (data: PermissionExplainRequest) => {
  return http.request<PermissionExplainResult>(...);
};
```

---

## Files to Create/Update

| File | Action | Justification |
|------|--------|---------------|
| `views/perm/explain/index.vue` | UPDATE | Complete diagnosis page |
| `views/perm/explain/components/QueryPanel.vue` | CREATE | Query mode selector |
| `views/perm/explain/components/ExplainResult.vue` | CREATE | Explanation result display |
| `views/perm/explain/components/PermissionMatrix.vue` | CREATE | Resource-operation matrix |
| `views/perm/explain/components/ComparePanel.vue` | CREATE | Permission comparison panel |

---

## Step-by-Step Tasks

### Task 1: Create Query Panel Component

- **ACTION**: Create query mode selector component
- **IMPLEMENT**:
  - Support user query mode (by subject type + external ID)
  - Support role query mode (by role ID)
  - Resource type and operation selection
  - Query form validation

### Task 2: Create Explanation Result Component

- **ACTION**: Create explanation result display component
- **IMPLEMENT**:
  - Display authorization check result (allow/deny)
  - Show reason for the decision
  - Role traceability (show inheritance chain)
  - Recent change history display

### Task 3: Create Permission Matrix Component

- **ACTION**: Create resource-operation permission matrix visualization
- **IMPLEMENT**:
  - Matrix/grid display of resources vs operations
  - Visual indication of granted/denied permissions
  - Color coding for different permission sources
  - Support for expanding resource details

### Task 4: Create Compare Panel Component

- **ACTION**: Create permission comparison panel
- **IMPLEMENT**:
  - Support selecting two subjects/roles to compare
  - Show permission differences (added, removed, common)
  - Side-by-side comparison view
  - Highlight differences

### Task 5: Complete Explain Page

- **ACTION**: Implement complete permission explanation page
- **IMPLEMENT**:
  ```vue
  <!-- views/perm/explain/index.vue -->
  <script setup lang="ts">
  defineOptions({ name: "PermExplain" });

  import { ref } from "vue";
  import { explainPermission } from "@/api/perm/permissionView";
  import QueryPanel from "./components/QueryPanel.vue";
  import ExplainResult from "./components/ExplainResult.vue";
  import PermissionMatrix from "./components/PermissionMatrix.vue";
  import ComparePanel from "./components/ComparePanel.vue";

  const activeTab = ref("explain"); // explain | matrix | compare
  const explainResult = ref(null);

  const handleExplain = async (params) => {
    const res = await explainPermission(params);
    if (res.success) {
      explainResult.value = res.data;
    }
  };
  </script>

  <template>
    <div class="permission-explain">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="Permission Explain" name="explain">
          <QueryPanel @query="handleExplain" />
          <ExplainResult v-if="explainResult" :data="explainResult" />
        </el-tab-pane>
        <el-tab-pane label="Permission Matrix" name="matrix">
          <PermissionMatrix />
        </el-tab-pane>
        <el-tab-pane label="Compare" name="compare">
          <ComparePanel />
        </el-tab-pane>
      </el-tabs>
    </div>
  </template>
  ```
- **CHECKLIST**:
  - [ ] Support user/role query modes
  - [ ] Complete permission explanation analysis (result, reason, source)
  - [ ] Role traceability (via inheritance chain)
  - [ ] Recent change history display
  - [ ] **Permission visualization matrix (resource-operation)**
  - [ ] **Permission comparison feature (two subjects/roles)**

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
| User query | Select user + resource + operation | Show permission explanation |
| Role query | Select role + resource + operation | Show permission explanation |
| Permission matrix | Switch to matrix tab | Show resource-operation grid |
| Compare | Select two subjects | Show permission differences |

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

- [ ] `views/perm/explain/index.vue` updated with complete functionality
- [ ] Query panel component created
- [ ] Explanation result component created
- [ ] Permission matrix component created
- [ ] Compare panel component created
- [ ] Full compilation passes

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Visualization complexity | Medium | Medium | Use table/grid instead of complex charts |
| Comparison logic complexity | Medium | Low | Clear diff algorithm |
