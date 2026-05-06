# Plan: Frontend Phase 4 - Resource and Advanced Configuration (Strategic Overview)

> **详细实施指南见子计划**: Phase 4.1 (资源管理) + Phase 4.2 (服务配置)

## Summary
实现资源管理、服务配置、接口同步等高级配置页面。

## Problem → Solution
- **当前**: frontend 缺少高级配置页面
- **目标**: 完整的高级配置模块（资源 CRUD + 服务接入 + 接口同步）

## Metadata
- **Complexity**: Medium
- **Estimated Files**: 15 files
- **Subplans**: Phase 4.1 (资源) + Phase 4.2 (服务)

---

## Strategic Architecture

### Advanced Configuration
```
Phase 4.1: 资源管理页面
├─ 左侧资源树 + 右侧详情表单
├─ 资源类型筛选（MENU/API/BUTTON/DATA）
├─ 资源 CRUD
├─ 资源依赖配置（源资源授权触发目标资源补全）
└─ 6 个任务，6 个文件

Phase 4.2: 服务配置与接口同步
├─ 服务卡片列表 + 详情与接口树
├─ 服务注册（服务编码 + 名称 + 基础路径）
├─ 接口全量同步（FULL 模式）
├─ Diff 算法处理新增/删除
├─ 接口资源树展示
└─ 6 个任务，6 个文件
```

### Core Concepts

**资源依赖**:
```
源资源授权
→ 触发目标资源补全
→ 自动补全权限
```

**服务接入流程**:
```
1. 注册服务（service-config/save）
2. 全量同步接口（service-config/sync）
3. 自动生成 API 资源
4. 标记 maintainSource=SERVICE_SYNC
```

---

## NOT Building (Scope Boundaries)

Phase 4 **不实现**（后续 Phase 实现）：
- 操作权限管理完整功能 → 已在 Phase 3.3 实现
- 权限条件完整配置 → Phase 4 只做基础配置
- 冲突规则管理 → Phase 4 只做基础 CRUD
- 接口映射管理完整功能 → Phase 4 只做基础 CRUD
- 域配置详细参数 → Phase 4 只做基础管理

---

## Related Subplans

| Subplan | File | Tasks | Description |
|---------|------|-------|-------------|
| Phase 4.1 | `subplans/phase-4.1-resource-management-page.plan.md` | 6 | **资源管理**: 树展示、CRUD、依赖配置 |
| Phase 4.2 | `subplans/phase-4.2-service-config-api-sync.plan.md` | 6 | **服务配置**: 注册、全量同步、diff |

**实施顺序**: Phase 4.1 → Phase 4.2

**实施建议**:
1. Phase 4.1 建立资源管理基础（资源树 + 依赖配置）
2. Phase 4.2 建立服务接入流程（注册 + 同步）
3. Phase 4.2 的接口同步是关键功能，diff 算法由后端处理

---

**Generated**: 2026-05-06
**Plan Status**: Strategic Overview - See Subplans for Implementation Details