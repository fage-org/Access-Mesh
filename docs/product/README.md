# AccessMesh 管理控制台 — 产品说明文档

> 面向前端原型生成工具（Stitch），描述管理控制台的页面结构、交互流程、数据模型和组件规格。
> 设计原则：**用户交互便捷**（减少跳转、上下文保持、批量操作）+ **管理模块内聚**（相关功能集中管理）。

## 文档索引

### 公共文档

| 文档 | 说明 |
|------|------|
| [00-overview.md](00-overview.md) | 系统概述：产品定位、技术栈、全局交互规范、整体布局 |
| [00-shared-components.md](00-shared-components.md) | 公共组件规格：各类选择器、树形组件、JSON 编辑器 |
| [00-user-flows.md](00-user-flows.md) | 核心用户流程：租户初始化、日常权限管理、服务接入 |
| [00-routing.md](00-routing.md) | 路由定义（TypeScript 代码） |
| [00-api-mapping.md](00-api-mapping.md) | API 接口映射表（页面 → 后端 API） |
| [00-permissions.md](00-permissions.md) | 前端权限标识定义（60+ 权限码） |
| [00-theme.md](00-theme.md) | 配色主题、图标规范、响应式断点 |

### 页面文档

| 模块 | 页面 | 文件 |
|------|------|------|
| 👥 用户管理 | 用户管理 | [user/user-management.md](user/user-management.md) |
| 🔐 权限管理 | 角色管理 | [permission/role.md](permission/role.md) |
| 🔐 权限管理 | 资源管理 | [permission/resource.md](permission/resource.md) |
| 🔐 权限管理 | 操作权限 | [permission/operation.md](permission/operation.md) |
| 🔐 权限管理 | 权限授权 | [permission/grant.md](permission/grant.md) |
| 🔐 权限管理 | 用户角色 | [permission/user-role.md](permission/user-role.md) |
| 🌐 服务集成 | 服务管理 | [integration/service.md](integration/service.md) |
| 🌐 服务集成 | 接口映射 | [integration/api-mapping.md](integration/api-mapping.md) |
| 🌐 服务集成 | 资源依赖 | [integration/dependency.md](integration/dependency.md) |
| 📋 审计排查 | 权限排查 | [audit/permission-view.md](audit/permission-view.md) |
| 📋 审计排查 | 操作日志 | [audit/operation-log.md](audit/operation-log.md) |
| 📋 审计排查 | 变更日志 | [audit/change-log.md](audit/change-log.md) |
| ⚙️ 系统配置 | 类型定义 | [system/type-definition.md](system/type-definition.md) |
| ⚙️ 系统配置 | 业务域 | [system/biz-domain.md](system/biz-domain.md) |
| ⚙️ 系统配置 | 域配置 | [system/domain-config.md](system/domain-config.md) |
| ⚙️ 系统配置 | 权限条件 | [system/condition.md](system/condition.md) |
| ⚙️ 系统配置 | 冲突规则 | [system/conflict-rule.md](system/conflict-rule.md) |
| ⚙️ 系统配置 | 系统设置 | [system/settings.md](system/settings.md) |

### 导航结构

```
👥 用户管理 (User)
  └─ 用户管理

🔐 权限管理 (Permission)
  ├─ 角色管理
  ├─ 资源管理
  ├─ 操作权限
  ├─ 权限授权
  └─ 用户角色

🌐 服务集成 (Integration)
  ├─ 服务管理
  ├─ 接口映射
  └─ 资源依赖

📋 审计排查 (Audit)
  ├─ 权限排查
  ├─ 操作日志
  └─ 变更日志

⚙️ 系统配置 (System)
  ├─ 类型定义
  ├─ 业务域
  ├─ 域配置
  ├─ 权限条件
  ├─ 冲突规则
  └─ 系统设置
```

### 页面清单汇总

| # | 路径 | 页面名 | 布局类型 |
|---|------|--------|----------|
| 1 | `/user` | 用户管理 | 树+表格+弹窗 |
| 2 | `/permission/role` | 角色管理 | 树+详情+Tab |
| 3 | `/permission/resource` | 资源管理 | 树+详情+Tab |
| 4 | `/permission/operation` | 操作权限 | 表格 |
| 5 | `/permission/grant` | 权限授权 | 三栏编辑器 |
| 6 | `/permission/user-role` | 用户角色 | 表格+弹窗 |
| 7 | `/integration/service` | 服务管理 | 表格+抽屉 |
| 8 | `/integration/api-mapping` | 接口映射 | 表格 |
| 9 | `/integration/dependency` | 资源依赖 | 表格+图 |
| 10 | `/audit/permission-view` | 权限排查 | 分Tab面板 |
| 11 | `/audit/operation-log` | 操作日志 | 表格 |
| 12 | `/audit/change-log` | 变更日志 | 表格+弹窗 |
| 13 | `/system/type-definition` | 类型定义 | 分组表格 |
| 14 | `/system/biz-domain` | 业务域 | 表格 |
| 15 | `/system/domain-config` | 域配置 | 表格 |
| 16 | `/system/condition` | 权限条件 | 表格+可视化编辑器 |
| 17 | `/system/conflict-rule` | 冲突规则 | 表格+检测弹窗 |
| 18 | `/system/settings` | 系统设置 | 键值列表 |
