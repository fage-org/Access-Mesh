# API 接口映射表

> 前端页面与后端 API 的对应关系。所有接口统一前缀 `/api/perm/`，统一 POST + JSON Body。

| 页面 | 操作 | API 路径 |
|------|------|----------|
| 用户列表 | 查询 | 通过 admin-service 代理 |
| 用户列表 | 同步到权限中心 | `abstract-user/sync` |
| 组织管理 | 查询/操作 | 通过 admin-service 代理 |
| 角色管理 | 列表/树 | `abstract-role/list`, `abstract-role/tree` |
| 角色管理 | 详情 | `abstract-role/detail` |
| 角色管理 | CRUD | `abstract-role/create`, `update`, `remove`, `move` |
| 角色管理 | 分组角色 | `abstract-role/extra-roles/list`, `add`, `remove` |
| 资源管理 | 列表/树 | `resource-entity/list`, `resource-entity/tree` |
| 资源管理 | 详情 | `resource-entity/detail` |
| 资源管理 | CRUD | `resource-entity/create`, `batch-create`, `update`, `move`, `remove` |
| 操作权限 | 全部 | `operation-permission/list`, `detail`, `create`, `update`, `remove` |
| 权限授权 | 保存 | `role-resource-permission/save` |
| 权限授权 | 查询 | `role-resource-permission/list` |
| 权限授权 | 回收 | `role-resource-permission/revoke` |
| 权限授权 | 子权限 | `role-resource-permission/children`, `add-child`, `remove-child` |
| 用户角色 | 查询 | `user-role/list` |
| 用户角色 | 分配 | `user-role/assign`, `user-role/batch-assign` |
| 用户角色 | 回收 | `user-role/revoke` |
| 服务管理 | 全部 | `service-config/list`, `detail`, `save`, `remove`, `sync`, `apis` |
| 接口映射 | 全部 | `resource-api-mapping/list`, `create`, `update`, `remove` |
| 资源依赖 | 全部 | `resource-dependency/list`, `create`, `update`, `remove`, `batch-sync`, `graph`, `check` |
| 权限排查 | 有效权限 | `permission-view/effective-permissions` |
| 权限排查 | 有效角色 | `permission-view/effective-roles` |
| 权限排查 | 单权限解释 | `permission-view/explain` |
| 权限排查 | 近期变更 | `permission-view/recent-changes` |
| 权限排查 | 角色权限 | `permission-view/role-permissions` |
| 权限排查 | 资源用户 | `permission-view/resource-users` |
| 权限排查 | 资源树 | `permission-view/resource-tree` |
| 操作日志 | 查询 | `operation-log/list` |
| 变更日志 | 查询 | `permission-change-log/list` |
| 类型定义 | 全部 | `type-definition/list`, `detail`, `create`, `update`, `remove` |
| 业务域 | 全部 | `biz-domain/list`, `detail`, `create`, `update`, `remove` |
| 域配置 | 全部 | `domain-config/list`, `detail`, `save`, `remove` |
| 权限条件 | 全部 | `permission-condition/list`, `detail`, `create`, `update`, `remove` |
| 冲突规则 | 全部 | `conflict-rule/list`, `detail`, `create`, `update`, `remove`, `detect` |
| 系统设置 | 全部 | `system-config/list`, `detail`, `save` |
