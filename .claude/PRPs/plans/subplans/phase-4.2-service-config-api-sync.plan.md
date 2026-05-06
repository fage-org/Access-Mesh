# Subplan: Phase 4.2 - Service Configuration & API Synchronization

## Summary
实现服务配置管理完整功能，包含服务注册、接口全量同步、接口资源树展示、diff 算法处理新增/删除等核心功能。

## User Story
作为权限管理员，我希望通过服务配置页面注册接入服务并同步接口资源，以便自动生成 API 资源并为权限配置提供接口基础。

## Problem → Solution
**当前状态**: frontend 缺少服务配置页面
**目标状态**: 完整的服务配置页面，支持服务注册、接口同步、接口资源树展示、diff 结果显示

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase4-resource-advanced-config.plan.md`
- **Phase**: Phase 4.2 (Service Configuration)
- **Estimated Files**: 6 files (1 page + 5 components)
- **Prerequisite**: Phase 4.1 已完成（资源管理）

---

## UX Design

### Page Layout
```
┌───────────────────────────────────────────────────────┐
│  服务配置                                              │
├───────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌─────────────────────────────────┐│
│  │  服务列表    │  │  服务详情与接口同步             ││
│  │              │  │                                 ││
│  │  服务卡片    │  │  服务详情:                      ││
│  │  ├─admin-svc │  │  服务编码: admin-service       ││
│  │  ├─perm-svc  │  │  服务名称: 管理员服务           ││
│  │  ├─gateway   │  │  基础路径: /admin/api           ││
│  │              │  │  状态: [启用]                   ││
│  │  [新增服务]  │  │                                 ││
│  └──────────────┘  │  [编辑] [同步接口]              ││
│                    │                                 ││
│                    │  接口资源树(同步后生成):        ││
│                    │  ├─ admin-service              ││
│                    │  │  ├─ 用户管理                 ││
│                    │  │  │  ├─ POST /user/page      ││
│                    │  │  │  ├─ POST /user/create    ││
│                    │  │  ├─ 组织管理                 ││
│                    │  │  │  ├─ POST /org/tree       ││
│                    │                                 ││
│                    │  同步结果(diff):                ││
│                    │  ✅ 新增接口: 5 个              ││
│                    │  ❌ 删除接口: 2 个              ││
│                    └─────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Behavior | Notes |
|---|---|---|
| 服务卡片点击 | 显示服务详情 | 右侧显示详情和接口资源树 |
| 新增服务 | 弹窗表单 | 输入服务编码、名称、基础路径 |
| 编辑服务 | 右侧表单编辑 | 修改服务名称、基础路径 |
| 同步接口 | 全量同步 | 后端做 diff，返回新增/删除数量 |
| 接口资源树 | 展示同步结果 | 自动生成的 API 资源树 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `plan/permission-center/api-contract.md` | 183-196 | 服务与接口映射 API 契约 |
| P0 | `plan/permission-center/core-flows.md` | 60-82 | 服务接入流程 |
| P1 | `frontend/src/views/perm/resource/index.vue` | 全文(Phase 4.1 创建) | 资源管理页参考 |

---

## Patterns to Mirror

### SERVICE_SYNC_PATTERN
// SOURCE: plan/permission-center/core-flows.md:60-82
```text
服务接入流程:
1. POST /api/perm/service-config/save - 注册服务
2. POST /api/perm/service-config/sync - 全量同步接口
3. 自动创建 API 资源和接口映射
4. 标记 maintainSource=SERVICE_SYNC
```
**模式要点**: FULL 全量同步，diff 算法处理新增/删除

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/api/perm/service.ts` | CREATE | 服务配置 API 接口 |
| `frontend/src/views/perm/service/index.vue` | CREATE | 服务配置主页面 |
| `frontend/src/views/perm/service/components/ServiceCard.vue` | CREATE | 服务卡片组件 |
| `frontend/src/views/perm/service/components/ServiceForm.vue` | CREATE | 服务详情表单 |
| `frontend/src/views/perm/service/components/ApiServiceTree.vue` | CREATE | 服务接口树组件 |
| `frontend/src/views/perm/service/components/SyncResultDialog.vue` | CREATE | 同步结果弹窗 |

---

## Step-by-Step Tasks

### Task 1: 创建服务配置 API 接口
- **ACTION**: 新建 `frontend/src/api/perm/service.ts`
- **IMPLEMENT**: 参考 Phase 4 主计划 Task 2 的完整代码(lines 183-240)
- **GOTCHA**: 
  - sync 使用 FULL 全量同步模式
  - 返回 addedCount 和 removedCount
  - apis 接口返回分组结构(groups + apis)
- **VALIDATE**: 类型定义完整，接口路径正确

### Task 2: 创建服务配置主页面
- **ACTION**: 新建 `frontend/src/views/perm/service/index.vue`
- **IMPLEMENT**:
  - 左侧服务卡片列表 + 右侧详情和接口树布局
  - 服务卡片点击显示详情
  - 新增服务弹窗表单
  - 同步接口按钮
  - 接口资源树展示
- **GOTCHA**: 
  - 服务卡片使用卡片式布局(不是表格)
  - 同步接口按钮触发全量同步
  - 同步成功后显示 diff 结果
- **VALIDATE**: 页面布局正确，服务列表正确显示

### Task 3-6: 组件实现要点

**Task 3: 服务卡片组件**
- 卡片式布局(服务编码 + 服务名称 + 状态)
- 点击卡片触发 select 事件
- 新增服务按钮

**Task 4: 服务详情表单**
- 服务编码、名称、基础路径、状态
- 编辑模式：服务编码不可修改
- 保存按钮调用 saveService API

**Task 5: 服务接口树组件**
- 调用 getServiceApis 获取接口树
- 分组结构(groups + apis)
- 显示接口路径和 method
- 标记 maintainSource=SERVICE_SYNC

**Task 6: 同步结果弹窗**
- 显示 addedCount 和 removedCount
- 显示新增接口列表和删除接口列表
- 确认按钮关闭弹窗

---

## Acceptance Criteria
- [ ] 所有 6 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 服务列表正确显示(卡片式)
- [ ] 服务详情表单正确显示
- [ ] 服务注册功能正常
- [ ] 接口同步功能正常
- [ ] 接口资源树正确显示
- [ ] 同步结果正确显示(diff)

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase4-resource-advanced-config.plan.md`
**Confidence Score**: 8/10 — 服务配置逻辑清晰，同步 diff 算法后端处理