# Plan: Frontend Phase 2 - User Organization Menu Pages (Strategic Overview)

> **详细实施指南见子计划**: Phase 2.1 (用户管理) + Phase 2.2 (组织管理) + Phase 2.3 (菜单管理)

## Summary
实现用户管理、组织管理、菜单管理三个核心系统管理页面。

## Problem → Solution
- **当前**: frontend 缺少基础管理页面
- **目标**: 完整的系统管理模块（用户/组织/菜单 CRUD）

## Metadata
- **Complexity**: Large
- **Estimated Files**: 18 files
- **Subplans**: Phase 2.1 (用户) + Phase 2.2 (组织) + Phase 2.3 (菜单)

---

## Strategic Architecture

### Three-Page Approach
```
Phase 2.1: 用户管理页面
├─ 左侧组织树筛选 + 右侧用户列表
├─ 用户 CRUD（创建/编辑/删除/启用停用）
├─ 用户组织关系配置
├─ 用户角色分配预留
└─ 9 个任务，6 个文件

Phase 2.2: 组织管理页面
├─ 左侧组织树 + 右侧详情表单
├─ 组织节点 CRUD
├─ 组织成员查看
├─ 组织树配置预留
└─ 5 个任务，6 个文件

Phase 2.3: 菜单管理页面
├─ 左侧菜单树 + 右侧详情表单
├─ 菜单类型区分（目录/菜单/按钮）
├─ 路由配置
└─ 3 个任务，4 个文件
```

### Interaction Changes
| Touchpoint | Implementation | Phase |
|---|---|---|
| 用户管理 | 表格 + 组织树筛选 + CRUD 弹窗 | 2.1 |
| 组织管理 | 左侧树 + 右侧详情 + Tab 切换 | 2.2 |
| 菜单管理 | 左侧树 + 右侧表单 + 类型区分 | 2.3 |
| 表格工具栏 | RePureTableBar 集成 | 所有 |
| 树形组件 | ElTree + 自定义节点 | 所有 |

---

## NOT Building (Scope Boundaries)

Phase 2 **不实现**（后续 Phase 实现）：
- 用户角色分配完整功能 → Phase 3
- 组织树配置完整功能 → 后续优化
- 菜单权限按钮配置 → Phase 2.3 基础 CRUD
- 数据导入导出 → 后续 Phase
- 树形拖拽排序 → 后续优化

---

## Related Subplans

| Subplan | File | Tasks | Description |
|---------|------|-------|-------------|
| Phase 2.1 | `subplans/phase-2.1-user-management-page.plan.md` | 9 | **用户管理**: 表格、筛选、CRUD、组织配置 |
| Phase 2.2 | `subplans/phase-2.2-organization-management-page.plan.md` | 5 | **组织管理**: 树展示、CRUD、成员查看 |
| Phase 2.3 | `subplans/phase-2.3-menu-management-page.plan.md` | 3 | **菜单管理**: 树展示、类型区分、路由配置 |

**实施顺序**: Phase 2.1 → Phase 2.2 → Phase 2.3

**实施建议**:
1. Phase 2.1 建立用户管理基础框架（表格 + 树筛选）
2. Phase 2.2 建立组织管理（树展示模式）
3. Phase 2.3 建立菜单管理（类型区分逻辑）
4. 三个子计划共用 API 模式和组件模式

---

**Generated**: 2026-05-06
**Plan Status**: Strategic Overview - See Subplans for Implementation Details