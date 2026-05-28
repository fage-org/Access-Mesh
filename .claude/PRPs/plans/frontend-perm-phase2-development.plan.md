# Plan: Permission Center Frontend 缺失模块开发计划 - Phase 2

## Summary

开发权限中心前端缺失的高级功能模块（Phase 2），包括冲突规则管理、资源API映射管理和域配置管理三个模块。

## User Story

As a 权限管理员, I want 在前端界面管理冲突规则、资源API映射和域配置, So that 我能够处理复杂的权限场景和细粒度配置。

## Problem → Solution

后端已提供冲突规则、资源API映射和域配置的完整API，但前端缺少对应页面 → 开发缺失的API文件、页面组件和路由配置。

## Metadata

- **Complexity**: High
- **Source PRD**: `frontend-perm-structure-analysis.plan.md`
- **Estimated Files**: 20-25
- **前置条件**: Phase 1完成, `frontend-perm-domain-type-quick.plan.md` 可选（域配置功能）

---

## Dependencies

| 计划 | 关系 | 说明 |
|------|------|------|
| `frontend-perm-phase1-development.plan.md` | 前置依赖 | Phase 1核心功能应先完成 |
| `frontend-perm-domain-type-quick.plan.md` | 部分重叠 | 域配置功能可与本计划并行或优先完成 |

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `permission-center/.../controller/ConflictRuleController.java` | 冲突规则后端API |
| P0 | `permission-center/.../controller/ResourceApiMappingController.java` | API映射后端API |
| P0 | `permission-center/.../controller/DomainConfigController.java` | 域配置后端API |
| P1 | `frontend/src/api/perm/resource.ts` | API文件参考模板 |
| P1 | `frontend/src/views/perm/resource/index.vue` | 页面参考模板 |

---

## Files to Create

### Phase 2.1: 冲突规则管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/conflictRule.ts` | CREATE | 冲突规则API层 |
| `views/perm/conflict/index.vue` | CREATE | 冲突规则管理页面 |
| `views/perm/conflict/components/ConflictRuleForm.vue` | CREATE | 规则表单组件 |
| `views/perm/conflict/components/ConflictDetectPanel.vue` | CREATE | 冲突检测组件 |

### Phase 2.2: 资源API映射管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/apiMapping.ts` | CREATE | API映射API层 |
| `views/perm/resource/components/ApiMappingPanel.vue` | CREATE | API映射面板组件 |
| `views/perm/resource/components/ApiMappingForm.vue` | CREATE | API映射表单组件 |

### Phase 2.3: 域配置管理模块

| File | Action | Justification |
|------|--------|---------------|
| `api/perm/domainConfig.ts` | CREATE | 域配置API层 |
| `views/perm/domain/components/DomainConfigPanel.vue` | CREATE | 域配置面板组件 |

### Phase 2.4: 路由和权限配置

| File | Action | Justification |
|------|--------|---------------|
| `constants/permission.ts` | UPDATE | 补充Phase 2权限码 |
| `router/modules/perm.ts` | UPDATE | 添加Phase 2路由 |

---

## Step-by-Step Tasks

### Task 1: 创建冲突规则API (conflictRule.ts)

- **ACTION**: 创建冲突规则管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/conflictRule.ts
  import { http } from "@/utils/http";

  export interface ConflictRuleItem {
    id: number;
    tenantId: number;
    ruleType: string;
    name: string;
    config: string; // JSON配置
    description: string | null;
    priority: number;
    status: number;
    createdAt: string;
    updatedAt: string | null;
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

  // API函数
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

  export const createConflictRule = (data: ConflictRuleCreateRequest) => {
    return http.request<ConflictRuleActionResult>(
      "post",
      "/api/perm/conflict-rule/create",
      { data }
    );
  };

  export const updateConflictRule = (data: ConflictRuleUpdateRequest) => {
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

  // 常量
  export const CONFLICT_RULE_TYPES = [
    { label: "角色互斥", value: "MUTEX" },
    { label: "权限合并", value: "MERGE" },
    { label: "拒绝优先", value: "DENY_FIRST" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 2: 创建冲突规则管理页面

- **ACTION**: 创建冲突规则管理页面
- **IMPLEMENT**:
  - 左侧规则列表
  - 右侧规则详情
  - 冲突检测功能（支持选择用户和角色进行检测）
- **CHECKLIST**:
  - [ ] 规则CRUD功能完整
  - [ ] 冲突检测界面友好
  - [ ] 检测结果可视化展示

### Task 3: 创建资源API映射API (apiMapping.ts)

- **ACTION**: 创建资源API映射API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/apiMapping.ts
  import { http } from "@/utils/http";

  export interface ApiMappingItem {
    id: number;
    tenantId: number;
    resourceId: number;
    resourceCode: string;
    resourceName: string;
    serviceCode: string;
    httpMethod: string;
    path: string;
    requirePermission: boolean;
    description: string | null;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface ApiMappingListRequest {
    resourceId?: number;
    serviceCode?: string;
  }

  export interface ApiMappingCreateRequest {
    resourceId: number;
    serviceCode: string;
    httpMethod: string;
    path: string;
    requirePermission?: boolean;
    description?: string;
  }

  export interface ApiMappingUpdateRequest {
    id: number;
    httpMethod?: string;
    path?: string;
    requirePermission?: boolean;
    description?: string;
  }

  // API函数
  export const getApiMappingList = (data: ApiMappingListRequest) => {
    return http.request<ApiMappingListResult>(
      "post",
      "/api/perm/resource-api-mapping/list",
      { data }
    );
  };

  export const createApiMapping = (data: ApiMappingCreateRequest) => {
    return http.request<ApiMappingActionResult>(
      "post",
      "/api/perm/resource-api-mapping/create",
      { data }
    );
  };

  export const updateApiMapping = (data: ApiMappingUpdateRequest) => {
    return http.request<ApiMappingActionResult>(
      "post",
      "/api/perm/resource-api-mapping/update",
      { data }
    );
  };

  export const deleteApiMapping = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/resource-api-mapping/remove",
      { data }
    );
  };

  // HTTP方法常量
  export const HTTP_METHODS = [
    { label: "GET", value: "GET" },
    { label: "POST", value: "POST" },
    { label: "PUT", value: "PUT" },
    { label: "DELETE", value: "DELETE" },
    { label: "PATCH", value: "PATCH" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 4: 创建资源API映射组件

- **ACTION**: 在资源管理页面添加API映射功能
- **IMPLEMENT**:
  - 在资源详情页添加"API映射"Tab
  - 显示该资源关联的所有API映射
  - 支持添加、编辑、删除API映射
- **CHECKLIST**:
  - [ ] API映射列表展示
  - [ ] 支持添加新映射
  - [ ] 支持编辑现有映射
  - [ ] 支持删除映射

### Task 5: 创建域配置API (domainConfig.ts)

- **ACTION**: 创建域配置管理API文件
- **IMPLEMENT**:
  ```typescript
  // api/perm/domainConfig.ts
  import { http } from "@/utils/http";

  export interface DomainConfigItem {
    id: number;
    tenantId: number;
    domainCode: string;
    configType: string;
    configValue: string;
    description: string | null;
    createdAt: string;
    updatedAt: string | null;
  }

  export interface DomainConfigListRequest {
    domainCode: string;
  }

  export interface DomainConfigSaveRequest {
    domainCode: string;
    configType: string;
    configValue: string;
    description?: string;
  }

  // API函数
  export const getDomainConfigList = (data: DomainConfigListRequest) => {
    return http.request<DomainConfigListResult>(
      "post",
      "/api/perm/domain-config/list",
      { data }
    );
  };

  export const getDomainConfigDetail = (data: { 
    domainCode: string; 
    configType: string 
  }) => {
    return http.request<DomainConfigDetailResult>(
      "post",
      "/api/perm/domain-config/detail",
      { data }
    );
  };

  export const saveDomainConfig = (data: DomainConfigSaveRequest) => {
    return http.request<DomainConfigActionResult>(
      "post",
      "/api/perm/domain-config/save",
      { data }
    );
  };

  export const deleteDomainConfig = (data: { ids: Array<number> }) => {
    return http.request<{ success: boolean }>(
      "post",
      "/api/perm/domain-config/remove",
      { data }
    );
  };

  // 配置类型常量
  export const DOMAIN_CONFIG_TYPES = [
    { label: "权限策略", value: "PERMISSION_STRATEGY" },
    { label: "缓存设置", value: "CACHE_CONFIG" },
    { label: "审计配置", value: "AUDIT_CONFIG" }
  ];
  ```
- **VALIDATE**: `pnpm typecheck` 通过

### Task 6: 创建域配置组件

- **ACTION**: 在业务域管理页面添加域配置功能
- **IMPLEMENT**:
  - 在业务域详情页添加"域配置"Tab
  - 支持JSON格式的配置值编辑
  - 提供配置类型选择
- **CHECKLIST**:
  - [ ] 域配置列表展示
  - [ ] JSON配置值编辑器
  - [ ] 支持保存和删除配置

### Task 7: 补充Phase 2权限码

- **ACTION**: 补充Phase 2的权限码
- **IMPLEMENT**:
  ```typescript
  // 冲突规则管理
  export const PERM_CONFLICT_VIEW = "perm:conflict:view";
  export const PERM_CONFLICT_CREATE = "perm:conflict:create";
  export const PERM_CONFLICT_UPDATE = "perm:conflict:update";
  export const PERM_CONFLICT_DELETE = "perm:conflict:delete";
  export const PERM_CONFLICT_DETECT = "perm:conflict:detect";

  // 资源API映射
  export const PERM_API_MAPPING_VIEW = "perm:api-mapping:view";
  export const PERM_API_MAPPING_CREATE = "perm:api-mapping:create";
  export const PERM_API_MAPPING_UPDATE = "perm:api-mapping:update";
  export const PERM_API_MAPPING_DELETE = "perm:api-mapping:delete";

  // 域配置管理
  export const PERM_DOMAIN_CONFIG_VIEW = "perm:domain-config:view";
  export const PERM_DOMAIN_CONFIG_CREATE = "perm:domain-config:create";
  export const PERM_DOMAIN_CONFIG_UPDATE = "perm:domain-config:update";
  export const PERM_DOMAIN_CONFIG_DELETE = "perm:domain-config:delete";
  ```
- **VALIDATE**: 添加到 `PERM_CODES` 对象

### Task 8: 更新路由配置

- **ACTION**: 更新路由配置
- **IMPLEMENT**:
  ```typescript
  {
    path: "/perm/conflict",
    name: "PermConflict",
    component: () => import("@/views/perm/conflict/index.vue"),
    meta: {
      title: "冲突规则",
      auths: [PERM_CODES.PERM_CONFLICT_VIEW]
    }
  }
  ```
- **VALIDATE**: 路由编译通过

---

## Acceptance Criteria

- [ ] `api/perm/conflictRule.ts` 创建完成
- [ ] `views/perm/conflict/` 页面组件创建完成
- [ ] `api/perm/apiMapping.ts` 创建完成
- [ ] 资源管理页面添加API映射功能
- [ ] `api/perm/domainConfig.ts` 创建完成
- [ ] 业务域管理添加域配置功能
- [ ] Phase 2权限码补充完整
- [ ] 路由配置更新完成
- [ ] 全量编译通过

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 冲突规则配置复杂 | Medium | Medium | 提供配置模板和示例 |
| JSON编辑器集成问题 | Low | Low | 使用成熟的JSON编辑器组件 |
