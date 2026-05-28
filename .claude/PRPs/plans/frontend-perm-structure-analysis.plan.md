# Plan: Permission Center Frontend 代码规范与结构分析

## Summary

对 permission-center 前端模块进行全面的代码规范和结构分析，识别当前实现中的规范合规性、结构合理性和潜在问题，为后续完善工作提供决策依据。

## User Story

As a 前端技术负责人, I want 了解当前前端代码的规范符合度和结构合理性, So that 我能够确定哪些部分需要重构、哪些部分可以复用、以及缺失哪些关键模块。

## Problem → Solution

前端代码已快速迭代实现7个功能模块，但缺乏系统性的规范审查和结构验证 → 通过全面的代码审查，识别规范偏差、结构问题和缺失模块，形成可执行的改进建议。

## Metadata

- **Complexity**: Low
- **Source PRD**: N/A (分析性任务)
- **Estimated Files**: 30+
- **前置条件**: 无

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `.claude/rules/frontend-coding-standards.md` | 前端编码规范标准 |
| P0 | `.claude/rules/permission-center-coding-standards.md` | 权限中心业务规范 |
| P1 | `frontend/src/views/perm/**` | 权限中心视图实现 |
| P1 | `frontend/src/api/perm/**` | 权限中心API实现 |
| P1 | `frontend/src/router/modules/perm.ts` | 权限中心路由配置 |
| P2 | `frontend/src/constants/permission.ts` | 权限码常量定义 |

---

## Analysis Framework

### 规范检查维度

1. **Vue组件规范**
   - [ ] 使用 `<script setup lang="ts">` + `defineOptions`
   - [ ] 组件名使用大驼峰命名
   - [ ] Props/Emits类型定义完整
   - [ ] 自闭合标签使用正确

2. **TypeScript规范**
   - [ ] 类型导入使用 `import { type X }` 内联语法
   - [ ] 避免使用 `any` 类型
   - [ ] 接口命名使用大驼峰
   - [ ] 公共API导出类型定义

3. **API层规范**
   - [ ] 导出类型定义 + API函数
   - [ ] 使用 `http.request<T>` 泛型
   - [ ] 常量定义完整（状态标签、类型映射等）
   - [ ] 工具函数配套（转换、扁平化等）

4. **目录结构规范**
   - [ ] views/{module}/{feature}/ 结构
   - [ ] components/ 子目录合理
   - [ ] hooks/ 自定义Hook抽取
   - [ ] 无重复或冗余文件

---

## Current State Analysis

### 已完成的模块（7个）

| 模块 | 页面路径 | API文件 | 状态 | 备注 |
|------|---------|---------|------|------|
| 角色管理 | `/perm/role` | `role.ts` | ✅ 实现完整 | 包含角色权限配置子页面 |
| 资源管理 | `/perm/resource` | `resource.ts` | ✅ 实现完整 | 包含资源依赖配置组件 |
| 服务配置 | `/perm/service` | `service.ts` | ✅ 实现完整 | - |
| 用户角色分配 | `/perm/user-role` | `userRole.ts` | ✅ 实现完整 | - |
| 权限视图 | `/perm/view` | `permissionView.ts` | ⚠️ 部分实现 | 页面功能待完善 |
| 权限排查 | `/perm/explain` | - | ⚠️ 仅占位 | 未实现API和完整功能 |
| 业务域管理 | - | `domain.ts` | ⚠️ 仅API | 有API无页面 |

### API层实现统计

**已实现的API文件（11个）**：
- `domain.ts` - 业务域API ✅
- `operation.ts` - 操作权限API ✅
- `permissionView.ts` - 权限视图API（部分）⚠️
- `resourceDependency.ts` - 资源依赖API ✅
- `resource.ts` - 资源API ✅
- `rolePermission.ts` - 角色权限API ✅
- `role.ts` - 角色API ✅
- `service.ts` - 服务配置API ✅
- `type.ts` - 类型定义API ✅
- `userRole.ts` - 用户角色API ✅

**缺失的API文件（8个）**：
- `condition.ts` - 权限条件管理 ❌
- `user.ts` - 抽象用户管理 ❌
- `conflictRule.ts` - 冲突规则管理 ❌
- `log.ts` - 日志查询 ❌
- `apiMapping.ts` - 资源API映射 ❌
- `domainConfig.ts` - 域配置管理 ❌
- `version.ts` - 权限版本管理 ❌
- `systemConfig.ts` - 系统配置 ❌

---

## Step-by-Step Analysis Tasks

### Task 1: Vue组件规范审查

- **ACTION**: 审查所有 perm 模块 Vue 组件的规范符合度
- **IMPLEMENT**:
  - 检查 `<script setup lang="ts">` + `defineOptions` 使用
  - 检查组件命名规范
  - 检查 Props/Emits 类型定义
  - 检查自闭合标签使用
- **CHECKLIST**:
  - [ ] `views/perm/role/index.vue` 符合规范
  - [ ] `views/perm/resource/index.vue` 符合规范
  - [ ] `views/perm/service/index.vue` 符合规范
  - [ ] `views/perm/user-role/index.vue` 符合规范
  - [ ] `views/perm/view/index.vue` 符合规范
  - [ ] `views/perm/explain/index.vue` 符合规范
  - [ ] 所有子组件符合规范
- **GOTCHA**: 注意检查是否有使用 Options API 的遗留代码

### Task 2: TypeScript类型规范审查

- **ACTION**: 审查类型导入和定义规范
- **IMPLEMENT**:
  - 检查是否使用内联类型导入 `{ type X }`
  - 检查接口命名规范
  - 检查是否有 `any` 类型滥用
- **CHECKLIST**:
  - [ ] API文件使用内联类型导入
  - [ ] 无 `import type { ... }` 语法
  - [ ] 无裸露的 `any` 类型
  - [ ] 接口使用大驼峰命名

### Task 3: API层规范审查

- **ACTION**: 审查API文件的结构和规范
- **IMPLEMENT**:
  - 检查类型定义导出
  - 检查API函数泛型使用
  - 检查常量定义完整性
  - 检查工具函数配套
- **CHECKLIST**:
  - [ ] 每个API文件导出完整类型定义
  - [ ] 使用 `http.request<T>` 泛型
  - [ ] 状态标签常量完整
  - [ ] 类型映射常量完整
  - [ ] 工具函数（transform、flatten等）配套

### Task 4: 权限码完整性审查

- **ACTION**: 审查 `constants/permission.ts` 的完整性
- **IMPLEMENT**:
  - 对比后端所有Controller的权限需求
  - 检查前端路由的auths配置
  - 识别缺失的权限码
- **CHECKLIST**:
  - [ ] 权限码覆盖所有已实现功能
  - [ ] 权限码覆盖规划中但未实现的功能
  - [ ] 权限码格式符合 `{module}:{resource}:{action}`

### Task 5: 路由配置审查

- **ACTION**: 审查路由配置的完整性和规范
- **IMPLEMENT**:
  - 检查 `router/modules/perm.ts` 结构
  - 对比后端API确认缺失路由
  - 检查权限码绑定
- **CHECKLIST**:
  - [ ] 路由路径符合 `/perm/{feature}` 规范
  - [ ] 路由name使用大驼峰
  - [ ] meta.auths权限码绑定正确
  - [ ] showLink配置合理
  - [ ] rank排序合理

### Task 6: 组件复用性分析

- **ACTION**: 分析组件的可复用性
- **IMPLEMENT**:
  - 识别通用的树形组件模式
  - 识别通用的表单组件模式
  - 识别可抽取的公共Hook
- **CHECKLIST**:
  - [ ] RoleTree和ResourceTree是否可以抽象为通用Tree
  - [ ] RoleForm和ResourceForm是否有可复用模式
  - [ ] 是否有重复的CRUD逻辑可以抽取为Hook

---

## Analysis Results (Expected)

### 规范符合度评估

| 维度 | 符合度 | 问题数 | 严重程度 |
|------|--------|--------|----------|
| Vue组件规范 | 90% | 2-3 | 低 |
| TypeScript规范 | 85% | 3-5 | 中 |
| API层规范 | 80% | 5-8 | 中 |
| 目录结构规范 | 95% | 1-2 | 低 |

### 用户决策确认 ✅

| 问题 | 决策 | 说明 |
|------|------|------|
| **问题1** | **选项B** | Tree组件抽象成公共组件 `RePermissionTree` |
| **问题2** | **选项B** | 权限视图完整功能（有效角色、资源权限分布、近期变更、用户资源树） |
| **问题3** | **选项A** | 业务域独立页面 `/perm/domain`（包含域配置管理） |
| **问题4** | **选项A** | 类型定义独立页面 `/perm/type`（包含操作权限管理） |
| **问题5** | **选项B** | 权限排查完整功能（权限解释和诊断系统） |

### 决策影响分析

1. **Tree组件抽象**: 需要创建通用组件 `components/RePermissionTree/`，支持角色树和资源树两种模式
2. **权限视图完整功能**: Phase 3需实现4个子视图组件
3. **业务域独立页面**: Phase 2需创建 `/perm/domain` 页面，包含业务域CRUD和域配置管理
4. **类型定义独立页面**: Phase 2需创建 `/perm/type` 页面，包含类型定义和操作权限管理
5. **权限排查完整功能**: Phase 1需实现完整的权限解释和诊断功能

---

## Testing Strategy

### 静态检查

```bash
# 类型检查
cd frontend && pnpm typecheck

# ESLint检查
pnpm lint:eslint

# Prettier格式检查
pnpm lint:prettier
```

### 预期结果

- 无 TypeScript 编译错误
- ESLint 警告数 < 10
- Prettier 格式合规率 > 95%

---

## Acceptance Criteria

- [ ] 完成7个模块的规范审查
- [ ] 完成11个API文件的规范审查
- [ ] 识别所有规范偏差问题
- [ ] 明确标记需要用户决策的问题
- [ ] 生成结构优化建议文档

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 分析遗漏 | Medium | Low | 多人复核 |
| 规范标准理解偏差 | Low | Medium | 参照CLUUDE.md标准 |
| 用户决策延迟 | Medium | High | 提前准备决策选项 |
