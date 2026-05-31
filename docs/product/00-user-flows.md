# 核心用户流程

## 新租户初始化流程

```
1. 创建类型定义
   └→ /system/type-definition
      创建 user_type, role_type, resource_type, subject_type 等基本类型

2. 创建业务域
   └→ /system/biz-domain
      创建全局域 + 业务域(如 admin, example)

3. 配置域规则
   └→ /system/domain-config
      为各域配置允许的角色类型、资源类型、操作码、子权限类型

4. 创建操作权限
   └→ /permission/operation
      定义 VIEW, MANAGE, ACCESS, DATA_READ, DATA_EDIT 等操作

5. 注册服务并同步接口
   └→ /integration/service
      注册业务服务，全量同步接口清单

6. 创建资源
   └→ /permission/resource
      创建菜单、按钮、数据范围等资源

7. 创建角色并授权
   └→ /permission/role → /permission/grant
      创建基础角色，配置资源-操作权限

8. 同步用户并分配角色
   └→ /user/list → /permission/user-role
      用户同步到权限中心，分配角色
```

## 日常权限管理流程

### 为新员工配置权限

```
1. 创建用户 (/user/list → 新建)
2. 将用户分配到组织 (/user/org → 添加成员)
3. 为用户分配角色 (/permission/user-role → 批量分配)
4. 验证权限 (/audit/permission-view → 排查用户有效权限)
```

### 为角色新增一个菜单权限

```
1. 进入角色管理 (/permission/role → 选择角色)
2. 点击「配置权限」进入授权页面
3. 在资源树中勾选新菜单
4. 在操作面板中勾选 VIEW
5. 保存 → 权限立即生效
6. 验证 (/audit/permission-view → 排查角色权限)
```

### 排查用户权限问题

```
1. 进入权限排查 (/audit/permission-view)
2. 选择目标用户
3. 在「单权限解释」Tab 中输入具体资源和操作
4. 查看 allowed/reason/sourceRoles/recentChanges
5. 根据结果决定是否需要调整角色权限或用户角色分配
```

## 服务接入流程

```
1. 注册服务 (/integration/service → 注册)
   填写 serviceCode, basePath, name

2. 同步接口 (/integration/service → 同步)
   确认 Diff 预览后执行全量同步

3. 查看接口资源树 (/integration/service → 详情 → 接口列表)
   确认所有接口已自动创建为 API 资源

4. 配置接口映射 (/integration/api-mapping)
   如有需要手动调整映射关系

5. 为角色授权 API 权限 (/permission/grant)
   在资源树中选择 API 类型资源，授予 ACCESS 操作

6. 验证 Gateway 鉴权
   使用测试用户访问接口，确认鉴权结果符合预期
```
