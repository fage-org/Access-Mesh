# 前端页面与后端模块对应关系

> 本文档详细列出前端页面与后端 API 的对应关系，用于指导前端开发。
> 
> **设计原则**：功能聚合，减少页面数量，提升用户体验。
> - 用户管理：整合用户 CRUD + 用户组织关系 + 用户角色分配
> - 组织管理：整合组织树 CRUD + 组织树配置 + 组织下用户查看
> - 角色管理：独立页面，角色 CRUD + 分组角色配置
> - 角色权限配置：单独页面，权限配置 + 子权限/范围权限

---

## 一、后端模块总览

| 后端服务 | Controller 数量 | 主要模块 |
|----------|-----------------|----------|
| admin-service | 17 个 | 认证、用户、组织、菜单、角色代理、字典、配置、公告、任务、文件、审计、登录日志、OAuth2客户端、用户组织、组织树配置、同步重试 |
| permission-center | 8 大类 API | 类型定义、业务域、角色、资源、操作、条件、授权关系、视图审计 |

**路径前缀**：
- admin-service: `/admin/*`
- permission-center: `/api/perm/*`

---

## 二、认证与登录模块

### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/auth/captcha` | 获取验证码 |
| `POST /admin/auth/login` | 用户登录 |
| `POST /admin/auth/login/sms` | 短信登录 |
| `POST /admin/auth/logout` | 用户登出 |
| `POST /admin/auth/userinfo` | 获取当前用户信息 |

### 前端页面

| 页面 | 路径 | 功能 | 对应 API |
|------|------|------|----------|
| 登录页 | `/login` | 租户选择 + 验证码 + 账号密码登录 | `/auth/captcha`, `/auth/login` |
| 首页 | `/dashboard` | 系统概览、统计数据 | `/auth/userinfo` |

### 登录页功能点

```
┌─────────────────────────────────────────┐
│  登录页                                  │
│                                         │
│  1. 租户选择（下拉）                     │
│     - 后端接口：查询可访问租户列表        │
│                                         │
│  2. 验证码                               │
│     - 后端接口：/auth/captcha            │
│                                         │
│  3. 账号密码                             │
│     - 后端接口：/auth/login              │
│                                         │
│  4. 登录按钮                             │
│     - 携带 tenantId + username + password│
│     + captcha                           │
└─────────────────────────────────────────┘
```

---

## 三、系统管理模块

### 3.1 用户管理（整合版）

#### 后端 API

**admin-service 用户 API：**

| API | 说明 |
|-----|------|
| `POST /admin/user/create` | 创建用户 |
| `POST /admin/user/update` | 更新用户 |
| `POST /admin/user/delete` | 删除用户（批量） |
| `POST /admin/user/enable` | 启用/停用用户（批量） |
| `POST /admin/user/detail` | 用户详情 |
| `POST /admin/user/page` | 用户分页列表 |
| `POST /admin/user/reset-password` | 重置密码 |
| `POST /admin/user/user-menus` | 获取用户菜单和权限 |

**admin-service 用户组织关系 API：**

| API | 说明 |
|-----|------|
| `POST /admin/user-org/assign` | 分配用户到多个组织 |
| `POST /admin/user-org/remove` | 移除用户组织关系 |
| `POST /admin/user-org/set-primary` | 设置用户主组织 |
| `POST /admin/user-org/list` | 查询用户的组织列表 |

**permission-center 用户角色 API：**

| API | 说明 |
|-----|------|
| `POST /api/perm/user-role/list` | 查询用户角色关系 |
| `POST /api/perm/user-role/assign` | 批量分配角色或分组 |
| `POST /api/perm/user-role/revoke` | 批量回收角色关系 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 用户管理 | `/system/user` | 用户 CRUD + 组织关系 + 角色分配（三合一） |

#### 用户管理页功能点

```
┌─────────────────────────────────────────────────────────────┐
│  用户管理                                                    │
│                                                             │
│  顶部操作栏：                                                │
│  [新增用户] [批量删除] [搜索框：用户名/昵称/手机号]            │
│                                                             │
│  左侧：组织树（可选，用于按组织筛选用户）                      │
│  - 点击组织节点，右侧显示该组织下的用户                        │
│  - 可折叠                                                   │
│                                                             │
│  右侧：用户列表表格                                          │
│  | 用户名 | 昵称 | 主组织 | 其他组织 | 角色 | 状态 | 操作    │
│  |--------|------|----------|----------|------|------|------│
│  | admin  | 管理员| 研发中心  | 产品部    | 管理员| 正常 | 操作│
│                                                             │
│  行操作按钮：                                                │
│  [编辑] [删除] [重置密码] [配置组织] [分配角色]               │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  【新增/编辑用户弹窗】                                        │
│  - 用户名*                                                  │
│  - 昵称                                                     │
│  - 密码（新增时必填）                                        │
│  - 邮箱                                                     │
│  - 手机号                                                   │
│  - 状态（启用/停用）                                         │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  【配置组织弹窗】（点击"配置组织"按钮）                        │
│  用户：张三                                                  │
│                                                             │
│  组织树（checkbox 多选）：                                    │
│  ☑ 研发中心                                                 │
│    ☑ 前端组 [主组织 ★]                                      │
│    ☐ 后端组                                                 │
│  ☐ 产品部                                                   │
│    ☐ 产品组                                                 │
│                                                             │
│  操作：                                                     │
│  - 勾选/取消勾选组织                                         │
│  - 点击某组织旁的"设为主组织"按钮                             │
│                                                             │
│  [保存] [取消]                                               │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  【分配角色弹窗】（点击"分配角色"按钮）                        │
│  用户：张三                                                  │
│  当前组织：研发中心（角色分配需在组织上下文）                  │
│                                                             │
│  角色树（checkbox 多选）：                                    │
│  ☐ 管理员角色组                                             │
│    ☐ 系统管理员                                             │
│    ☐ 业务管理员                                             │
│  ☑ 普通用户角色组                                           │
│    ☑ 开发人员                                               │
│    ☐ 测试人员                                               │
│                                                             │
│  [保存] [取消]                                               │
│                                                             │
│  API 调用：                                                  │
│  - 查询当前角色：/api/perm/user-role/list                    │
│  - 保存分配：/api/perm/user-role/assign                      │
│  - 回收角色：/api/perm/user-role/revoke                      │
└─────────────────────────────────────────────────────────────┘
```

#### 用户-组织-角色关系说明

```
用户可以属于多个组织（多对多）：
- 每个用户有一个"主组织"（primary）
- 用户在不同组织下可以有不同角色
- 角色分配需指定组织上下文

数据结构：
用户 ─┬─ 主组织（1个）
      └─ 其他组织（多个）
      
用户 + 组织 ── 角色列表（多个）
```

---

### 3.2 组织管理（整合版）

#### 后端 API

**admin-service 组织 API：**

| API | 说明 |
|-----|------|
| `POST /admin/org/create` | 创建组织 |
| `POST /admin/org/update` | 更新组织 |
| `POST /admin/org/delete` | 删除组织 |
| `POST /admin/org/detail` | 组织详情 |
| `POST /admin/org/page` | 组织分页列表 |
| `POST /admin/org/tree` | 组织树 |

**admin-service 组织树配置 API：**

| API | 说明 |
|-----|------|
| `POST /admin/org-tree-config/create` | 创建组织树配置 |
| `POST /admin/org-tree-config/update` | 更新组织树配置 |
| `POST /admin/org-tree-config/delete` | 删除组织树配置（批量） |
| `POST /admin/org-tree-config/set-default` | 设置默认组织树配置 |
| `POST /admin/org-tree-config/detail` | 组织树配置详情 |
| `POST /admin/org-tree-config/page` | 组织树配置分页列表 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 组织管理 | `/system/org` | 组织树 CRUD + 组织树配置 + 组织下用户查看（三合一） |

#### 组织管理页功能点

```
┌─────────────────────────────────────────────────────────────┐
│  组织管理                                                    │
│                                                             │
│  顶部 Tab 切换：                                             │
│  [组织树] [树配置]                                           │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  【Tab 1：组织树】                                            │
│                                                             │
│  左侧：组织树                                                │
│  - 树形结构展示                                              │
│  - 支持拖拽调整层级                                          │
│  - 点击节点显示详情                                          │
│                                                             │
│  顶部操作：                                                  │
│  [新增根节点] [展开全部] [折叠全部]                           │
│                                                             │
│  节点操作（右键菜单/悬停按钮）：                               │
│  - 新增子节点                                               │
│  - 编辑节点                                                 │
│  - 删除节点                                                 │
│  - 查看成员（右侧显示该组织下用户）                           │
│                                                             │
│  右侧：组织详情/编辑表单                                      │
│  - 组织名称*                                                │
│  - 组织编码                                                 │
│  - 上级组织                                                 │
│  - 组织类型（下拉选择）                                       │
│  - 负责人（用户选择）                                         │
│  - 状态（启用/停用）                                         │
│  - 排序                                                     │
│                                                             │
│  右侧：组织成员列表（点击"查看成员"后）                        │
│  | 用户名 | 昵称 | 是否主组织 | 角色 | 操作                 │
│  |--------|------|------------|------|------               │
│  | 张三   | 开发  | 是         | 开发人员| 移除              │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  【Tab 2：树配置】                                            │
│                                                             │
│  组织树配置用于定义不同场景下展示的组织树结构：                │
│  - 不同业务场景可能需要展示不同的组织树                       │
│  - 例如：行政组织树、业务组织树、项目组织树                    │
│                                                             │
│  配置列表表格：                                              │
│  | 配置名称 | 配置编码 | 是否默认 | 状态 | 操作              │
│  |----------|----------|----------|------|------            │
│  | 行政组织 | admin_org| 是       | 启用 | 编辑/删除/设为默认│
│  | 业务组织 | biz_org  | 否       | 启用 | 编辑/删除/设为默认│
│                                                             │
│  顶部操作：                                                  │
│  [新增配置]                                                  │
│                                                             │
│  新增/编辑配置弹窗：                                          │
│  - 配置名称*                                                │
│  - 配置编码*                                                │
│  - 描述                                                     │
│  - 根组织（选择组织树的根节点）                               │
│  - 包含的组织类型（多选）                                     │
│  - 排序规则                                                 │
│  - 状态                                                     │
│                                                             │
│  设为默认：                                                  │
│  - 点击"设为默认"按钮                                        │
│  - 调用 /admin/org-tree-config/set-default                  │
└─────────────────────────────────────────────────────────────┘
```

#### 组织树配置说明

```
组织树配置定义不同场景的组织树展示方式：

配置内容：
- 配置名称：如"行政组织树"、"业务组织树"
- 根组织：指定树展示的根节点
- 包含类型：筛选展示的组织类型
- 排序规则：子节点排序方式

使用场景：
- 用户选择组织时，根据场景加载不同配置
- 不同角色看到不同的组织树结构
- 简化用户选择，只展示相关组织
```

---

### 3.3 菜单管理

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/menu/create` | 创建菜单 |
| `POST /admin/menu/update` | 更新菜单 |
| `POST /admin/menu/delete` | 删除菜单 |
| `POST /admin/menu/detail` | 菜单详情 |
| `POST /admin/menu/tree` | 菜单树 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 菜单管理 | `/system/menu` | 菜单树 CRUD、按钮配置、路由配置 |

#### 菜单管理页功能点

```
┌─────────────────────────────────────────┐
│  菜单管理                                │
│                                         │
│  左侧：菜单树                            │
│  - 目录（folder 图标）                   │
│  - 菜单（page 图标）                     │
│  - 按钮（button 图标）                   │
│                                         │
│  顶部操作：                              │
│  [新增目录] [新增菜单] [展开全部]         │
│                                         │
│  节点操作：                              │
│  - 新增子菜单/按钮                       │
│  - 编辑                                  │
│  - 删除                                  │
│                                         │
│  右侧/弹窗：菜单配置表单                  │
│  - 菜单名称                              │
│  - 菜单类型（目录/菜单/按钮）             │
│  - 上级菜单                              │
│  - 路由路径（菜单类型）                   │
│  - 组件路径（菜单类型）                   │
│  - 权限标识（按钮类型）                   │
│  - 图标                                  │
│  - 排序                                  │
│  - 是否缓存                              │
│  - 是否外链                              │
│  - 状态（显示/隐藏）                      │
└─────────────────────────────────────────┘
```

---

### 3.4 字典管理

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/dict/type/create` | 创建字典类型 |
| `POST /admin/dict/type/delete` | 删除字典类型（批量） |
| `POST /admin/dict/type/list` | 字典类型列表 |
| `POST /admin/dict/type/page` | 字典类型分页 |
| `POST /admin/dict/data/create` | 创建字典数据 |
| `POST /admin/dict/data/update` | 更新字典数据 |
| `POST /admin/dict/data/delete` | 删除字典数据 |
| `POST /admin/dict/data/list` | 字典数据列表（按类型） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 字典管理 | `/system/dict` | 字典类型 + 字典数据 CRUD |

#### 字典管理页功能点

```
┌─────────────────────────────────────────┐
│  字典管理                                │
│                                         │
│  上半部分：字典类型表格                   │
│  | 类型名称 | 类型编码 | 状态 | 操作      │
│  |----------|----------|------|----------│
│  | 性别     | sex      | 正常 | 查看/删除 │
│                                         │
│  类型操作：                              │
│  [新增类型]                              │
│                                         │
│  下半部分：字典数据（点击类型后展开）      │
│  | 数据标签 | 数据值 | 排序 | 状态 | 操作 │
│  |----------|--------|------|------|------│
│  | 男       | 1      | 1    | 正常 | 编辑 │
│                                         │
│  数据操作：                              │
│  [新增数据]                              │
└─────────────────────────────────────────┘
```

---

### 3.5 系统配置

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/config/page` | 配置分页列表 |
| `POST /admin/config/detail` | 配置详情 |
| `POST /admin/config/update` | 更新配置 |
| `POST /admin/config/delete` | 删除配置 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 系统配置 | `/system/config` | 系统参数配置 |

---

### 3.6 公告通知

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/notice/create` | 创建公告 |
| `POST /admin/notice/update` | 更新公告 |
| `POST /admin/notice/delete` | 删除公告（批量） |
| `POST /admin/notice/detail` | 公告详情 |
| `POST /admin/notice/page` | 公告分页列表 |
| `POST /admin/notice/publish` | 发布公告 |
| `POST /admin/notice/read` | 标记已读 |
| `POST /admin/notice/my-notices` | 我的公告列表 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 公告管理 | `/system/notice` | 公告 CRUD、发布、已读 |

---

### 3.7 定时任务

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/job/create` | 创建任务 |
| `POST /admin/job/update` | 更新任务 |
| `POST /admin/job/delete` | 删除任务（批量） |
| `POST /admin/job/toggle` | 启用/停用任务 |
| `POST /admin/job/trigger` | 手动触发任务 |
| `POST /admin/job/detail` | 任务详情 |
| `POST /admin/job/page` | 任务分页列表 |
| `POST /admin/job/log/page` | 任务日志分页 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 定时任务 | `/system/job` | 任务 CRUD、启停、执行、日志 |
| 任务日志 | `/system/job-log` | 任务执行日志查看 |

---

### 3.8 文件管理

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/file/upload` | 文件上传 |
| `POST /admin/file/delete` | 文件删除 |
| `POST /admin/file/page` | 文件分页列表 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 文件管理 | `/system/file` | 文件上传、列表、删除 |

---

### 3.9 审计日志

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/audit-log/page` | 操作审计日志分页 |
| `POST /admin/login-log/page` | 登录日志分页（LoginLogController） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 操作日志 | `/log/operation` | 用户操作审计日志 |
| 登录日志 | `/log/login` | 用户登录日志 |

---

## 四、权限中心模块

### 4.1 类型定义

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/type-definition/list` | 类型定义列表 |
| `POST /api/perm/type-definition/detail` | 类型详情 |
| `POST /api/perm/type-definition/create` | 创建类型 |
| `POST /api/perm/type-definition/update` | 更新类型 |
| `POST /api/perm/type-definition/remove` | 删除类型（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 类型定义 | `/perm/type` | 主体类型、资源类型、角色类型管理 |

#### 类型定义页功能点

```
类型定义用于定义系统中的基础类型枚举：
- 主体类型（subject_type）：USER, POSITION, DEPT 等
- 资源类型（resource_type）：MENU, ORG, ROLE, API 等
- 角色类型（role_type）：BASIC_ROLE, GROUP_ROLE 等

页面结构：
┌─────────────────────────────────────────┐
│  类型定义                                │
│                                         │
│  筛选栏：                                │
│  类型分类: [主体类型 ▼]                  │
│                                         │
│  操作栏：                                │
│  [新增]                                  │
│                                         │
│  数据表格：                              │
│  | 类型编码 | 类型名称 | 类型值 | 状态 | 操作│
│  |----------|----------|--------|------|------│
│  | USER     | 用户     | 1      | 启用 | 编辑 │
│                                         │
│  新增/编辑弹窗：                          │
│  - 类型编码、类型名称                    │
│  - 类型值（整数）                        │
│  - 状态                                  │
└─────────────────────────────────────────┘
```

---

### 4.2 业务域

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/biz-domain/list` | 业务域列表 |
| `POST /api/perm/biz-domain/detail` | 业务域详情 |
| `POST /api/perm/biz-domain/create` | 创建业务域 |
| `POST /api/perm/biz-domain/update` | 更新业务域 |
| `POST /api/perm/biz-domain/remove` | 删除业务域（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 业务域管理 | `/perm/domain` | 业务域 CRUD |

---

### 4.3 角色管理（独立页面）

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/abstract-role/list` | 角色列表 |
| `POST /api/perm/abstract-role/tree` | 角色树 |
| `POST /api/perm/abstract-role/detail` | 角色详情 |
| `POST /api/perm/abstract-role/create` | 创建角色 |
| `POST /api/perm/abstract-role/update` | 更新角色 |
| `POST /api/perm/abstract-role/move` | 移动角色树节点 |
| `POST /api/perm/abstract-role/remove` | 删除角色（批量） |
| `POST /api/perm/abstract-role/extra-roles/list` | 分组角色额外角色列表 |
| `POST /api/perm/abstract-role/extra-roles/add` | 分组角色添加基本角色 |
| `POST /api/perm/abstract-role/extra-roles/remove` | 分组角色移除基本角色 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 角色管理 | `/perm/role` | 角色树 CRUD + 分组角色配置 |
| 角色权限配置 | `/perm/role/permission` | 角色的资源权限配置（独立页面，从角色管理跳转） |

#### 角色管理页功能点

```
┌─────────────────────────────────────────────────────────────┐
│  角色管理                                                    │
│                                                             │
│  左侧：角色树                                                │
│  - 分组角色（folder 图标）                                   │
│  - 基本角色（role 图标）                                     │
│                                                             │
│  顶部操作：                                                  │
│  [新增分组] [新增角色] [刷新]                                │
│                                                             │
│  节点操作：                                                  │
│  - 编辑（修改名称、状态）                                    │
│  - 删除                                                     │
│  - 配置权限 → 跳转到角色权限配置页面                          │
│  - 配置子角色（分组角色专属）                                 │
│                                                             │
│  右侧：角色详情面板                                          │
│  ┌─────────────────────────────────────────────────────────│
│  │ 角色信息                                                │
│  │ - 角色名称：管理员                                       │
│  │ - 角色编码：admin                                       │
│  │ - 角色类型：基本角色                                     │
│  │ - 所属业务域：admin                                     │
│  │ - 状态：启用                                            │
│  │                                                         │
│  │ 快捷操作                                                │
│  │ [配置权限] [查看授权用户]                                │
│  └─────────────────────────────────────────────────────────│
│                                                             │
│  分组角色：额外角色配置面板                                   │
│  ┌─────────────────────────────────────────────────────────│
│  │ 当前分组：管理员角色组                                   │
│  │                                                         │
│  │ 已包含的基本角色：                                        │
│  │ - 系统管理员 [移除]                                      │
│  │ - 业务管理员 [移除]                                      │
│  │                                                         │
│  │ [添加基本角色]                                           │
│  │                                                         │
│  │ 弹窗：基本角色选择                                       │
│  │ - 基本角色树（checkbox）                                 │
│  │ - [确认添加]                                            │
│  └─────────────────────────────────────────────────────────│
└─────────────────────────────────────────────────────────────┘
```

#### 角色权限配置页（独立页面）

```
从角色管理点击"配置权限"进入，或直接访问 /perm/role/permission

┌─────────────────────────────────────────────────────────────┐
│  角色权限配置                                                │
│                                                             │
│  当前角色：[管理员角色] （breadcrumb: 角色管理 > 管理员角色）  │
│                                                             │
│  顶部操作：                                                  │
│  [返回角色管理]                                              │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  左侧：资源树选择                                            │
│  ┌─────────────────────────────────────────────────────────│
│  │ 业务域选择: [admin ▼]                                    │
│  │                                                         │
│  │ 资源类型筛选:                                            │
│  │ [菜单] [API] [其他]                                      │
│  │                                                         │
│  │ 资源树（checkbox 多选）：                                 │
│  │ ☑ 系统管理                                              │
│  │   ☑ 用户管理 [VIEW]                                     │
│  │   ☑ 组织管理 [VIEW, MANAGE]                             │
│  │   ☐ 菜单管理                                            │
│  │ ☐ 权限中心                                              │
│  │   ☐ 角色管理                                            │
│  │                                                         │
│  │ 点击节点可展开，勾选即授权                                │
│  └─────────────────────────────────────────────────────────│
│                                                             │
│  右侧：已授权权限列表                                        │
│  ┌─────────────────────────────────────────────────────────│
│  │ 当前已授权：                                             │
│  │                                                         │
│  │ | 资源      | 资源名称 | 操作    | canGrant | 条件 | 操作│
│  │ |-----------|----------|---------|----------|------|------│
│  │ | sys:user  | 用户管理 | VIEW    | false    | -    | 删除│
│  │ | sys:org   | 组织管理 | VIEW    | false    | -    | 配置│
│  │ | sys:org   | 组织管理 | MANAGE  | true     | -    | 配置│
│  │                                                         │
│  │ 操作说明：                                               │
│  │ - 删除：移除该权限                                       │
│  │ - 配置：打开范围权限/子权限配置弹窗                        │
│  │                                                         │
│  │ [保存授权] [批量回收]                                     │
│  └─────────────────────────────────────────────────────────│
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  【范围权限配置弹窗】（点击"配置"按钮）                        │
│  ┌─────────────────────────────────────────────────────────│
│  │ 主权限：组织管理 - MANAGE                                 │
│  │                                                         │
│  │ 范围权限（子权限）：                                      │
│  │ | 资源    | 资源名称 | 操作    | 来源    | 操作          │
│  │ |---------|----------|---------|---------|------         │
│  │ | org:A   | 研发中心 | MANAGE  | 直接授权 | 删除          │
│  │ | org:B   | 产品部   | MANAGE  | 直接授权 | 删除          │
│  │                                                         │
│  │ [添加范围权限]                                           │
│  │                                                         │
│  │ 添加弹窗：                                               │
│  │ - 组织树选择（checkbox）                                 │
│  │ - 操作选择：MANAGE / VIEW                                │
│  │ - [确认添加]                                            │
│  └─────────────────────────────────────────────────────────│
│                                                             │
│  API 调用：                                                  │
│  - 查询已有权限：/api/perm/role-resource-permission/list     │
│  - 三段式保存：/api/perm/role-resource-permission/save        │
│  - 查询子权限：/api/perm/role-resource-permission/children    │
│  - 添加子权限：/api/perm/role-resource-permission/add-child   │
│  - 删除子权限：/api/perm/role-resource-permission/remove-child│
└─────────────────────────────────────────────────────────────┘
```

#### 角色类型说明

```
角色分为两种类型：

1. 基本角色（BASIC_ROLE）
   - 直接授权资源权限
   - 可分配给用户
   - 例如：开发人员、测试人员、管理员

2. 分组角色（GROUP_ROLE）
   - 不直接授权，而是包含多个基本角色
   - 用户分配分组角色后，自动获得包含的基本角色权限
   - 例如：管理员角色组 = 系统管理员 + 业务管理员

优势：
- 简化用户角色分配（一个分组角色 = 多个基本角色）
- 支持角色组合，灵活适配不同岗位需求
```

---

### 4.5 资源管理

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/resource-entity/tree` | 资源树 |
| `POST /api/perm/resource-entity/list` | 资源列表 |
| `POST /api/perm/resource-entity/detail` | 资源详情 |
| `POST /api/perm/resource-entity/create` | 创建资源 |
| `POST /api/perm/resource-entity/batch-create` | 批量创建资源 |
| `POST /api/perm/resource-entity/update` | 更新资源 |
| `POST /api/perm/resource-entity/move` | 移动资源树节点 |
| `POST /api/perm/resource-entity/remove` | 删除资源（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 资源管理 | `/perm/resource` | 资源树 CRUD |

---

### 4.6 操作权限

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/operation-permission/list` | 操作权限列表 |
| `POST /api/perm/operation-permission/detail` | 操作详情 |
| `POST /api/perm/operation-permission/create` | 创建操作 |
| `POST /api/perm/operation-permission/update` | 更新操作 |
| `POST /api/perm/operation-permission/remove` | 删除操作（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 操作权限 | `/perm/operation` | 操作定义 CRUD（VIEW, MANAGE, CREATE 等） |

---

### 4.7 权限条件

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/permission-condition/list` | 条件列表 |
| `POST /api/perm/permission-condition/detail` | 条件详情 |
| `POST /api/perm/permission-condition/create` | 创建条件 |
| `POST /api/perm/permission-condition/update` | 更新条件 |
| `POST /api/perm/permission-condition/remove` | 删除条件（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 权限条件 | `/perm/condition` | 条件定义（时间范围、IP限制等） |

---

### 4.8 服务配置

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/service-config/list` | 接入服务列表 |
| `POST /api/perm/service-config/detail` | 服务详情 |
| `POST /api/perm/service-config/save` | 幂等保存服务 |
| `POST /api/perm/service-config/remove` | 删除服务（批量） |
| `POST /api/perm/service-config/sync` | 全量同步服务接口 |
| `POST /api/perm/service-config/apis` | 服务接口资源树 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 服务配置 | `/perm/service` | 接入服务管理、接口同步 |

---

### 4.9 接口映射

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/resource-api-mapping/list` | 接口映射列表 |
| `POST /api/perm/resource-api-mapping/create` | 创建接口映射 |
| `POST /api/perm/resource-api-mapping/update` | 更新接口映射 |
| `POST /api/perm/resource-api-mapping/remove` | 删除接口映射（批量） |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 接口映射 | `/perm/api-mapping` | API 与资源映射关系 |

---

### 4.10 资源依赖

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/resource-dependency/list` | 依赖列表 |
| `POST /api/perm/resource-dependency/create` | 创建依赖 |
| `POST /api/perm/resource-dependency/update` | 更新依赖 |
| `POST /api/perm/resource-dependency/remove` | 删除依赖（批量） |
| `POST /api/perm/resource-dependency/batch-sync` | 批量同步依赖 |
| `POST /api/perm/resource-dependency/graph` | 查询依赖图 |
| `POST /api/perm/resource-dependency/check` | 检查依赖是否成环 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 资源依赖 | `/perm/dependency` | 资源依赖关系配置 |

---

### 4.11 冲突规则

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/conflict-rule/list` | 冲突规则列表 |
| `POST /api/perm/conflict-rule/detail` | 冲突规则详情 |
| `POST /api/perm/conflict-rule/create` | 创建冲突规则 |
| `POST /api/perm/conflict-rule/update` | 更新冲突规则 |
| `POST /api/perm/conflict-rule/remove` | 删除冲突规则（批量） |
| `POST /api/perm/conflict-rule/detect` | 冲突检测 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 冲突规则 | `/perm/conflict` | 权限冲突规则配置 |

---

### 4.12 域配置

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/domain-config/list` | 域配置列表 |
| `POST /api/perm/domain-config/detail` | 域配置详情 |
| `POST /api/perm/domain-config/save` | 幂等保存域配置 |
| `POST /api/perm/domain-config/remove` | 删除域配置 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 域配置 | `/perm/domain-config` | 业务域配置（子权限类型等） |

---

## 五、系统扩展模块（admin-service 补充）

### 5.1 OAuth2 客户端管理

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/oauth2/client/create` | 创建 OAuth2 客户端 |
| `POST /admin/oauth2/client/update` | 更新 OAuth2 客户端 |
| `POST /admin/oauth2/client/delete` | 删除 OAuth2 客户端（批量） |
| `POST /admin/oauth2/client/detail` | OAuth2 客户端详情 |
| `POST /admin/oauth2/client/page` | OAuth2 客户端分页列表 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| OAuth2 客户端 | `/system/oauth2-client` | OAuth2 应用注册管理 |

#### OAuth2 客户端页功能点

```
┌─────────────────────────────────────────────────────────────┐
│  OAuth2 客户端管理                                            │
│                                                             │
│  顶部操作栏：                                                │
│  [新增客户端] [搜索框：客户端名称/clientId]                    │
│                                                             │
│  数据表格：                                                  │
│  | 客户端名称 | ClientId | 授权类型 | 状态 | 创建时间 | 操作 │
│  |------------|----------|----------|------|----------|------│
│  | 前端应用   | web-app  | code     | 启用 | 2026-05-01| 编辑│
│                                                             │
│  行操作：                                                    │
│  [编辑] [删除] [查看密钥]                                     │
│                                                             │
│  新增/编辑弹窗：                                              │
│  - 客户端名称*                                               │
│  - ClientId*（自动生成或手动输入）                            │
│  - ClientSecret（自动生成，创建后不可修改）                   │
│  - 授权类型（多选：authorization_code, implicit, password）   │
│  - 回调地址（多个，逗号分隔）                                 │
│  - 权限范围（scope 列表）                                    │
│  - Token 有效期                                              │
│  - 状态（启用/停用）                                         │
│                                                             │
│  查看密钥弹窗：                                               │
│  - 显示 ClientSecret（仅创建时可见，后续不可查看）            │
│  - 提示：请妥善保管密钥，无法再次查看                          │
└─────────────────────────────────────────────────────────────┘
```

---

### 5.2 同步重试管理

#### 后端 API（admin-service）

| API | 说明 |
|-----|------|
| `POST /admin/sync-retry/page` | 同步重试记录分页列表 |
| `POST /admin/sync-retry/pending` | 待处理重试记录列表 |
| `POST /admin/sync-retry/mark-success` | 标记同步成功 |
| `POST /admin/sync-retry/mark-failed` | 标记同步失败（带错误信息） |
| `POST /admin/sync-retry/delete` | 删除已处理的重试记录 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 同步重试 | `/system/sync-retry` | 数据同步失败记录管理 |

#### 同步重试页功能点

```
┌─────────────────────────────────────────────────────────────┐
│  同步重试管理                                                │
│                                                             │
│  用于管理用户/组织/菜单同步到权限中心时的失败记录：            │
│  - 同步失败会自动创建重试记录                                │
│  - 后台定时任务自动重试                                      │
│  - 管理员可手动处理或标记                                    │
│                                                             │
│  顶部筛选：                                                  │
│  状态: [全部 ▼] / [待处理] / [成功] / [失败]                  │
│  同步类型: [全部 ▼] / [用户] / [组织] / [菜单]                │
│                                                             │
│  数据表格：                                                  │
│  | 同步类型 | 目标ID | 目标名称 | 状态 | 重试次数 | 错误信息 | 操作│
│  |----------|--------|----------|------|----------|----------|------│
│  | 用户同步 | 1001   | 张三     | 待处理| 3        | 网络超时  | 操作│
│                                                             │
│  行操作：                                                    │
│  [标记成功] [标记失败] [查看详情] [删除]                      │
│                                                             │
│  详情弹窗：                                                  │
│  - 同步类型                                                 │
│  - 目标对象信息                                             │
│  - 当前状态                                                 │
│  - 重试次数                                                 │
│  - 最后重试时间                                             │
│  - 错误信息                                                 │
│  - 同步数据快照（JSON）                                      │
│                                                             │
│  标记失败弹窗：                                               │
│  - 输入失败原因/错误信息                                     │
│  - 确认后不再重试                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 六、视图与审计模块

### 5.1 权限视图

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/permission-view/effective-roles` | 用户有效角色 |
| `POST /api/perm/permission-view/effective-permissions` | 用户/角色有效权限 |
| `POST /api/perm/permission-view/resource-tree` | 用户资源树 |
| `POST /api/perm/permission-view/resource-users` | 拥有资源权限的用户 |
| `POST /api/perm/permission-view/role-permissions` | 角色权限视图 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 权限视图 | `/perm/view` | 查询用户/角色当前有效权限 |

#### 权限视图页功能点

```
┌─────────────────────────────────────────┐
│  权限视图                                │
│                                         │
│  查询类型：                              │
│  [用户权限] [角色权限]                    │
│                                         │
│  用户权限查询：                          │
│  - 用户搜索                              │
│  - 业务域选择                            │
│  - 资源类型筛选                          │
│  - 操作筛选                              │
│                                         │
│  结果表格：                              │
│  | 资源 | 资源名称 | 操作 | 来源角色      │
│  |------|----------|------|-------------│
│  | 菜单A| 用户管理 | VIEW | 管理员角色    │
│                                         │
│  角色权限查询：                          │
│  - 角色选择                              │
│  - 资源类型筛选                          │
│  - 结果表格                              │
└─────────────────────────────────────────┘
```

---

### 5.2 权限排查

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/permission-view/explain` | 解释单个权限（为什么有/没有） |
| `POST /api/perm/permission-view/recent-changes` | 近期影响事件 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 权限排查 | `/perm/explain` | 单权限解释、近期变更 |

#### 权限排查页功能点

```
┌─────────────────────────────────────────┐
│  权限排查                                │
│                                         │
│  查询对象：                              │
│  - 用户搜索                              │
│                                         │
│  权限查询：                              │
│  - 资源类型                              │
│  - 资源编码                              │
│  - 操作                                  │
│                                         │
│  结果：                                  │
│  - 是否有权限                            │
│  - 来源角色列表                          │
│  - 近期可能影响的变更事件                │
│                                         │
│  变更历史：                              │
│  | 事件类型 | 变更类型 | 影响 | 时间      │
│  |----------|----------|------|----------│
│  | 角色权限 | 删除     | 可能 | 2026-05-01│
└─────────────────────────────────────────┘
```

---

### 5.3 操作日志

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/operation-log/list` | 操作日志列表 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 权限操作日志 | `/log/perm-operation` | 权限中心操作审计 |

---

### 5.4 权限变更日志

#### 后端 API（permission-center）

| API | 说明 |
|-----|------|
| `POST /api/perm/permission-change-log/list` | 权限变更日志 |

#### 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 权限变更日志 | `/log/perm-change` | 权限变更历史 |

---

## 七、页面总览表

### 7.1 按模块分类

| 模块 | 页面数量 | 页面列表 |
|------|----------|----------|
| 认证 | 2 | 登录、首页 |
| 系统管理 | 10 | 用户（含组织关系+角色分配）、组织（含树配置）、菜单、字典、配置、公告、任务、文件、任务日志、OAuth2客户端 |
| 系统扩展 | 1 | 同步重试 |
| 权限中心 | 12 | 类型、业务域、角色、角色权限配置、资源、操作、条件、服务、接口映射、依赖、冲突、域配置 |
| 视图审计 | 4 | 权限视图、权限排查、权限日志、变更日志 |
| 系统日志 | 2 | 操作日志、登录日志 |

**总计：约 31 个页面**

**页面整合说明**：
- 用户管理：整合用户 CRUD + 用户组织关系 + 用户角色分配（3合一）
- 组织管理：整合组织树 CRUD + 组织树配置 + 组织成员查看（3合一）
- 角色权限配置：从角色管理独立出的专门页面

---

### 7.2 完整页面清单

| 序号 | 页面名称 | 路径 | 后端服务 | 整合功能 | 优先级 |
|------|----------|------|----------|----------|--------|
| 1 | 登录 | `/login` | admin | 租户选择 | P0 |
| 2 | 首页 | `/dashboard` | admin | - | P0 |
| 3 | 用户管理 | `/system/user` | admin + perm | 用户组织关系 + 用户角色分配 | P0 |
| 4 | 组织管理 | `/system/org` | admin + perm | 组织树配置 + 组织成员 | P0 |
| 5 | 菜单管理 | `/system/menu` | admin + perm | - | P0 |
| 6 | 字典管理 | `/system/dict` | admin | - | P1 |
| 7 | 系统配置 | `/system/config` | admin | - | P1 |
| 8 | 公告管理 | `/system/notice` | admin | - | P1 |
| 9 | 定时任务 | `/system/job` | admin | - | P1 |
| 10 | 任务日志 | `/system/job-log` | admin | - | P1 |
| 11 | 文件管理 | `/system/file` | admin | - | P1 |
| 12 | OAuth2 客户端 | `/system/oauth2-client` | admin | - | P1 |
| 13 | 同步重试 | `/system/sync-retry` | admin | - | P1 |
| 14 | 类型定义 | `/perm/type` | perm | - | P1 |
| 15 | 业务域 | `/perm/domain` | perm | - | P0 |
| 16 | 角色管理 | `/perm/role` | perm | 分组角色配置 | P0 |
| 17 | 角色权限配置 | `/perm/role/permission` | perm | 范围权限配置 | P0 |
| 18 | 资源管理 | `/perm/resource` | perm | - | P1 |
| 19 | 操作权限 | `/perm/operation` | perm | - | P1 |
| 20 | 权限条件 | `/perm/condition` | perm | - | P1 |
| 21 | 服务配置 | `/perm/service` | perm | - | P1 |
| 22 | 接口映射 | `/perm/api-mapping` | perm | - | P1 |
| 23 | 资源依赖 | `/perm/dependency` | perm | - | P1 |
| 24 | 冲突规则 | `/perm/conflict` | perm | - | P1 |
| 25 | 域配置 | `/perm/domain-config` | perm | - | P1 |
| 26 | 权限视图 | `/perm/view` | perm | - | P0 |
| 27 | 权限排查 | `/perm/explain` | perm | - | P1 |
| 28 | 操作日志 | `/log/operation` | admin | - | P0 |
| 29 | 登录日志 | `/log/login` | admin | - | P0 |
| 30 | 权限操作日志 | `/log/perm-operation` | perm | - | P1 |
| 31 | 权限变更日志 | `/log/perm-change` | perm | - | P1 |

---

### 7.3 功能整合对照表

| 整合后页面 | 原功能模块 | API 来源 |
|------------|------------|----------|
| 用户管理 `/system/user` | 用户 CRUD（UserController） | admin-service |
| | 用户组织关系（UserOrgController） | admin-service |
| | 用户角色分配（UserRole API） | permission-center |
| 组织管理 `/system/org` | 组织 CRUD（OrgController） | admin-service |
| | 组织树配置（OrgTreeConfigController） | admin-service |
| | 组织成员查看 | admin-service（通过组织ID查询用户） |
| 角色管理 `/perm/role` | 角色树 CRUD | permission-center |
| | 分组角色配置 | permission-center |
| 角色权限配置 `/perm/role/permission` | 角色资源权限配置 | permission-center |
| | 子权限/范围权限配置 | permission-center |

---

## 八、菜单树结构建议

```
系统管理
├── 用户管理（含组织关系、角色分配）
├── 组织管理（含树配置、成员查看）
├── 菜单管理
├── 字典管理
├── 系统配置
├── 公告管理
├── 定时任务
│   └── 任务日志
├── 文件管理
└── OAuth2 客户端

系统运维
└── 同步重试

权限中心
├── 基础配置
│   ├── 类型定义
│   ├── 业务域
│   ├── 操作权限
│   └── 权限条件
├── 角色管理
│   ├── 角色列表
│   └── 角色权限配置
├── 资源管理
│   ├── 资源列表
│   ├── 资源依赖
│   └── 域配置
├── 服务配置
│   ├── 接入服务
│   └── 接口映射
├── 高级配置
│   └── 冲突规则
└── 权限视图
    ├── 权限查询
    └── 权限排查

日志审计
├── 操作日志
├── 登录日志
├── 权限操作日志
└── 权限变更日志
```

---

## 九、开发优先级建议

### P0 - 必须优先完成

| 页面 | 原因 |
|------|------|
| 登录 | 系统入口 |
| 首页 | 登录后落地页 |
| 用户管理 | 核心业务对象 |
| 组织管理 | 核心业务对象 |
| 菜单管理 | 动态菜单来源 |
| 业务域 | 权限中心基础 |
| 角色管理 | 权限配置核心 |
| 用户角色 | 权限分配入口 |
| 权限视图 | 权限排查工具 |
| 操作日志 | 审计要求 |

### P1 - 后续迭代完成

其他辅助管理页面和高级配置页面。