# 前端权限标识定义

前端使用 `hasPerms` 或 `<Perms>` 组件控制页面元素显隐，权限标识采用 `模块:资源:操作` 格式。

## 用户管理

| 权限标识 | 说明 |
|----------|------|
| `system:user:list` | 查看用户列表 |
| `system:user:add` | 新建用户 |
| `system:user:edit` | 编辑用户 |
| `system:user:delete` | 删除用户 |
| `system:user:assign` | 分配角色 |
| `system:org:list` | 查看组织 |
| `system:org:add` | 新建组织 |
| `system:org:edit` | 编辑组织 |
| `system:org:delete` | 删除组织 |

## 权限管理

| 权限标识 | 说明 |
|----------|------|
| `perm:role:list` | 查看角色列表 |
| `perm:role:add` | 新建角色 |
| `perm:role:edit` | 编辑角色 |
| `perm:role:delete` | 删除角色 |
| `perm:role:grant` | 配置角色权限 |
| `perm:resource:list` | 查看资源列表 |
| `perm:resource:add` | 新建资源 |
| `perm:resource:edit` | 编辑资源 |
| `perm:resource:delete` | 删除资源 |
| `perm:operation:list` | 查看操作权限 |
| `perm:operation:add` | 新建操作 |
| `perm:operation:edit` | 编辑操作 |
| `perm:operation:delete` | 删除操作 |
| `perm:grant:edit` | 权限授权操作 |
| `perm:user-role:assign` | 分配用户角色 |
| `perm:user-role:revoke` | 回收用户角色 |

## 服务集成

| 权限标识 | 说明 |
|----------|------|
| `integration:service:list` | 查看服务列表 |
| `integration:service:add` | 注册服务 |
| `integration:service:sync` | 同步接口 |
| `integration:service:delete` | 删除服务 |
| `integration:mapping:list` | 查看接口映射 |
| `integration:mapping:add` | 新建映射 |
| `integration:mapping:edit` | 编辑映射 |
| `integration:mapping:delete` | 删除映射 |
| `integration:dependency:list` | 查看资源依赖 |
| `integration:dependency:add` | 新建依赖 |
| `integration:dependency:edit` | 编辑依赖 |
| `integration:dependency:delete` | 删除依赖 |

## 审计排查

| 权限标识 | 说明 |
|----------|------|
| `audit:permission-view` | 权限排查 |
| `audit:operation-log` | 查看操作日志 |
| `audit:change-log` | 查看变更日志 |

## 系统配置

| 权限标识 | 说明 |
|----------|------|
| `system:type:list` | 查看类型定义 |
| `system:type:add` | 新建类型 |
| `system:type:edit` | 编辑类型 |
| `system:type:delete` | 删除类型 |
| `system:domain:list` | 查看业务域 |
| `system:domain:add` | 新建业务域 |
| `system:domain:edit` | 编辑业务域 |
| `system:domain:delete` | 删除业务域 |
| `system:config:list` | 查看域配置 |
| `system:config:edit` | 编辑域配置 |
| `system:condition:list` | 查看权限条件 |
| `system:condition:add` | 新建条件 |
| `system:condition:edit` | 编辑条件 |
| `system:condition:delete` | 删除条件 |
| `system:conflict:list` | 查看冲突规则 |
| `system:conflict:add` | 新建冲突规则 |
| `system:conflict:edit` | 编辑冲突规则 |
| `system:conflict:delete` | 删除冲突规则 |
| `system:settings:list` | 查看系统设置 |
| `system:settings:edit` | 编辑系统设置 |
