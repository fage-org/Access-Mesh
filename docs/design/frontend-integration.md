# 前端集成计划

> 本文档定义 AccessMesh 前端管理系统的集成方案与实施计划。

## 1. 方案决策

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 集成方式 | Monorepo 子目录 `frontend/` | 单仓库管理，前后端 API 同步方便 |
| 基础脚手架 | pure-admin-thin | Vue 3 + Element Plus + Vite 精简版 |
| 认证模式 | Sa-Token OAuth2 授权码模式 | 生产级安全认证 |
| 菜单加载 | 后端返回完整路由配置 | 权限控制精确，动态菜单 |
| 多租户 | 支持，从 Header `X-Tenant-Id` 读取 | SaaS 管理员可切换租户 |
| 国际化 | 暂不实现 | 后续扩展 |

## 2. 技术栈

| 层面 | 技术 |
|------|------|
| 框架 | Vue 3.4+ |
| UI 组件 | Element Plus |
| 构建 | Vite 5 |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| HTTP | Axios |
| 样式 | TailwindCSS + SCSS |
| 图标 | @iconify/vue |

## 3. API 对接规范

### 3.1 请求协议

- 所有接口 `POST + application/json`
- 统一响应体：`{ code, message, data, requestId, traceId }`
- 分页结构：`{ items, total, pageNum, pageSize, hasNext }`

### 3.2 Header 规范

| Header | 来源 | 说明 |
|--------|------|------|
| `Authorization` | 登录后存储 | `Bearer <token>` |
| `X-Tenant-Id` | Token 解析 / 租户切换器 | 当前租户 ID |
| `X-Request-Id` | 前端生成 | 可选，用于追踪 |
| `X-Api-Version` | 固定 | `2026-04-26` |

### 3.3 错误处理

| code 范围 | 来源 | 处理 |
|-----------|------|------|
| 200 | 成功 | 正常处理 |
| 10001-19999 | admin-service | 业务错误提示 |
| 20001-29999 | permission-center | 业务错误提示 |
| 90001-99999 | 全局 | 系统错误提示 |

## 4. 页面规划

### 4.1 基础管理页面

| 页面 | 路径 | 功能 | API 来源 |
|------|------|------|----------|
| 登录 | `/login` | Sa-Token OAuth2 认证 | admin-service |
| 首页 | `/dashboard` | 系统概览 | - |
| 用户管理 | `/system/user` | 用户 CRUD、角色分配 | admin-service + perm |
| 组织管理 | `/system/org` | 组织树管理 | admin-service + perm |
| 菜单管理 | `/system/menu` | 菜单树、按钮配置 | admin-service + perm |

### 4.2 权限中心页面

| 页面 | 路径 | 功能 | API |
|------|------|------|-----|
| 类型定义 | `/perm/type-def` | 主体/资源/角色类型管理 | `/api/perm/type-definition/*` |
| 业务域 | `/perm/domain` | 业务域 CRUD | `/api/perm/biz-domain/*` |
| 角色管理 | `/perm/role` | 角色树、权限配置 | `/api/perm/abstract-role/*` |
| 资源管理 | `/perm/resource` | 资源树、依赖配置 | `/api/perm/resource-entity/*` |
| 操作权限 | `/perm/operation` | 操作定义 | `/api/perm/operation-permission/*` |
| 条件管理 | `/perm/condition` | 权限条件 | `/api/perm/permission-condition/*` |
| 用户角色 | `/perm/user-role` | 用户角色分配视图 | `/api/perm/user-role/*` |

### 4.3 高级配置页面

| 页面 | 路径 | 功能 | API |
|------|------|------|-----|
| 服务配置 | `/perm/service` | 接入服务、接口同步 | `/api/perm/service-config/*` |
| 接口映射 | `/perm/api-mapping` | API 资源映射 | `/api/perm/resource-api-mapping/*` |
| 域配置 | `/perm/domain-config` | 城配置管理 | `/api/perm/domain-config/*` |
| 冲突规则 | `/perm/conflict` | 权限冲突规则 | `/api/perm/conflict-rule/*` |
| 资源依赖 | `/perm/dependency` | 资源依赖配置 | `/api/perm/resource-dependency/*` |

### 4.4 视图与审计页面

| 页面 | 路径 | 功能 | API |
|------|------|------|-----|
| 权限视图 | `/perm/view` | 用户/角色有效权限 | `/api/perm/permission-view/*` |
| 权限排查 | `/perm/explain` | 单权限解释 | `/api/perm/permission-view/explain` |
| 变更历史 | `/perm/changes` | 近期变更事件 | `/api/perm/permission-view/recent-changes` |
| 操作日志 | `/log/operation` | 操作审计 | `/api/perm/operation-log/*` |
| 权限日志 | `/log/permission` | 权限变更历史 | `/api/perm/permission-change-log/*` |

### 4.5 辅助管理页面

| 页面 | 路径 | 功能 | API 来源 |
|------|------|------|----------|
| 字典管理 | `/system/dict` | 数据字典 | admin-service |
| 通知管理 | `/system/notice` | 系统通知 | admin-service |
| 文件管理 | `/system/file` | 文件上传 | admin-service |
| 任务调度 | `/system/job` | 定时任务 | admin-service |
| 系统设置 | `/system/config` | 系统配置 | admin-service + perm |

## 5. 按钮级权限设计

### 5.1 权限码格式

```
{模块}:{资源}:{操作}
```

示例：
- `sys:user:create` — 用户创建
- `sys:user:update` — 用户编辑
- `sys:user:delete` — 用户删除
- `perm:role:grant` — 角色授权

### 5.2 权限指令

```vue
<!-- 单权限 -->
<button v-perm="['sys:user:create']">新增用户</button>

<!-- 多权限（任一满足） -->
<button v-perm="['sys:user:update', 'sys:user:delete']">操作</button>
```

### 5.3 权限获取流程

1. 登录成功后调用 `/api/perm/auth/query-resources`
2. 参数：`resourceTypeCodes=["BUTTON"]`, `operationCodes=["VIEW"]`
3. 返回用户可见按钮权限码列表
4. 存储到 Pinia store，供 `v-perm` 指令比对

## 6. 动态路由流程

### 6.1 路由获取

```typescript
// 登录成功后
const menuResp = await api.post('/api/perm/auth/query-resources', {
  subjectTypeCode: 'USER',
  subjectExternalId: userStore.externalId,
  resourceTypeCodes: ['MENU'],
  operationCodes: ['VIEW'],
  treeMode: true
});
```

### 6.2 路由转换

后端返回的 `resourceCode` 格式：`menu:{routePath}`

前端转换逻辑：
1. 解析 `resourceCode` 获取路由路径
2. 从 `resource_entity.meta` 获取组件路径、图标等信息
3. 动态注册 Vue Router 路由

### 6.3 路由守卫

```typescript
router.beforeEach(async (to, from, next) => {
  const token = getToken();
  if (!token && to.path !== '/login') {
    next('/login');
    return;
  }

  // 动态路由已加载
  if (router.getRoutes().length > BASE_ROUTES.length) {
    next();
    return;
  }

  // 加载动态路由
  await loadDynamicRoutes();
  next(to.fullPath);
});
```

## 7. 多租户支持

### 7.1 租户信息获取

- 登录时从 Token 解析租户列表（用户可访问的租户）
- 当前租户存储在 Pinia + localStorage

### 7.2 租户切换器

布局 Header 右侧添加租户下拉选择：

```vue
<el-select v-model="currentTenantId" @change="onTenantChange">
  <el-option
    v-for="tenant in tenantList"
    :key="tenant.id"
    :label="tenant.name"
    :value="tenant.id"
  />
</el-select>
```

### 7.3 切换后处理

1. 更新 Header `X-Tenant-Id`
2. 清空当前路由和权限缓存
3. 重新加载动态路由和按钮权限

## 8. 实施阶段

### Phase 1: 项目集成与认证

**目标**: pure-admin-thin 改造并跑通登录流程

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 1.1 | 克隆 pure-admin-thin 到 `frontend/` | P0 |
| 1.2 | 清理 mock 数据，建立 API 请求层 | P0 |
| 1.3 | 改造登录模块对接 Sa-Token OAuth2 | P0 |
| 1.4 | 实现 Token 持久化与自动刷新 | P0 |
| 1.5 | 改造路由守卫集成权限校验 | P0 |
| 1.6 | 基础布局调整（Logo、主题色） | P1 |

**验收**: 登录成功后跳转首页，Token 自动刷新

---

### Phase 2: 用户组织菜单

**目标**: 完成用户、组织、菜单管理页面

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 2.1 | 用户管理列表页 + 搜索 | P0 |
| 2.2 | 用户创建/编辑表单 | P0 |
| 2.3 | 用户状态切换、删除 | P0 |
| 2.4 | 组织管理树形组件 | P0 |
| 2.5 | 组织节点增删改、拖拽 | P1 |
| 2.6 | 菜单管理树形组件 | P0 |
| 2.7 | 菜单配置表单 | P0 |
| 2.8 | 菜单按钮配置与同步 | P0 |

**验收**: 用户、组织、菜单 CRUD 全流程可用

---

### Phase 3: 权限中心核心页面

**目标**: 完成角色管理、用户角色分配、角色权限配置

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 3.1 | 业务域管理页面 | P0 |
| 3.2 | 类型定义管理页面 | P1 |
| 3.3 | 角色管理树形组件 | P0 |
| 3.4 | 角色创建/编辑表单 | P0 |
| 3.5 | 用户角色分配页面 | P0 |
| 3.6 | 角色权限配置页面（三段式） | P0 |
| 3.7 | 子权限/范围权限配置 | P1 |

**验收**: 用户可被分配角色，角色可配置资源权限

---

### Phase 4: 资源与高级配置

**目标**: 完成资源管理、条件、依赖、冲突规则页面

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 4.1 | 资源管理树形组件 | P0 |
| 4.2 | 资源依赖配置页面 | P1 |
| 4.3 | 操作权限管理页面 | P1 |
| 4.4 | 权限条件管理页面 | P1 |
| 4.5 | 冲突规则管理页面 | P1 |
| 4.6 | 域配置管理页面 | P1 |
| 4.7 | 服务配置与接口同步 | P1 |

**验收**: 权限中心所有配置项可通过前端管理

---

### Phase 5: 视图与审计页面

**目标**: 完成权限视图、排查、日志页面

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 5.1 | 用户有效权限视图 | P0 |
| 5.2 | 角色权限视图 | P0 |
| 5.3 | 单权限排查页面 | P1 |
| 5.4 | 近期变更事件页面 | P1 |
| 5.5 | 操作日志列表页 | P0 |
| 5.6 | 权限变更日志页 | P0 |

**验收**: 管理员可查询权限事实、排查变更历史

---

### Phase 6: 辅助管理功能

**目标**: 完成字典、通知、文件、任务调度页面

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 6.1 | 字典管理 CRUD | P1 |
| 6.2 | 系统通知管理 | P1 |
| 6.3 | 文件上传管理 | P1 |
| 6.4 | 定时任务调度 | P1 |
| 6.5 | 系统配置管理 | P1 |

**验收**: 辅助管理功能完整可用

---

### Phase 7: 集成测试与优化

**目标**: 全流程验证、性能优化、文档完善

| 任务 | 内容 | 优先级 |
|------|------|--------|
| 7.1 | 全流程权限校验测试 | P0 |
| 7.2 | 前端性能优化 | P1 |
| 7.3 | API 错误处理统一化 | P0 |
| 7.4 | 前端文档编写 | P1 |
| 7.5 | Docker 构建配置 | P1 |

**验收**: 生产级可用前端系统

## 9. 文件结构规划

```
frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── api/                  # API 请求层
│   │   ├── admin/            # admin-service 接口
│   │   │   ├── user.ts
│   │   │   ├── org.ts
│   │   │   ├── menu.ts
│   │   │   └── auth.ts
│   │   ├── perm/             # permission-center 接口
│   │   │   ├── type.ts
│   │   │   ├── domain.ts
│   │   │   ├── role.ts
│   │   │   ├── resource.ts
│   │   │   ├── permission.ts
│   │   │   ├── userRole.ts
│   │   │   ├── service.ts
│   │   │   └── view.ts
│   │   ├── request.ts        # Axios 封装
│   │   └── types.ts          # API 类型定义
│   ├── components/           # 通用组件
│   │   ├── Table/
│   │   ├── Tree/
│   │   ├── Form/
│   │   └── Permission/
│   ├── directives/           # Vue 指令
│   │   └── permission.ts     # v-perm 指令
│   ├── hooks/                # 组合式函数
│   │   ├── useTable.ts
│   │   ├── useTree.ts
│   │   └── usePermission.ts
│   ├── layouts/              # 布局组件
│   │   ├── default/
│   │   └── blank/
│   ├── router/               # 路由配置
│   │   ├── index.ts
│   │   ├── static.ts         # 静态路由
│   │   └── dynamic.ts        # 动态路由加载
│   ├── stores/               # Pinia 状态
│   │   ├── user.ts           # 用户信息
│   │   ├── tenant.ts         # 租户信息
│   │   ├── permission.ts     # 权限缓存
│   │   └── menu.ts           # 菜单状态
│   ├── styles/               # 样式文件
│   │   ├── index.scss
│   │   ├── variables.scss
│   │   └── element-override.scss
│   ├── utils/                # 工具函数
│   │   ├── auth.ts           # Token 管理
│   │   ├── storage.ts        # 本地存储
│   │   └── format.ts         # 格式化
│   ├── views/                # 页面组件
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── system/           # 基础管理
│   │   │   ├── user/
│   │   │   ├── org/
│   │   │   ├── menu/
│   │   │   ├── dict/
│   │   │   ├── notice/
│   │   │   ├── file/
│   │   │   ├── job/
│   │   │   └── config/
│   │   ├── perm/             # 权限中心
│   │   │   ├── type-def/
│   │   │   ├── domain/
│   │   │   ├── role/
│   │   │   ├── resource/
│   │   │   ├── operation/
│   │   │   ├── condition/
│   │   │   ├── user-role/
│   │   │   ├── service/
│   │   │   ├── api-mapping/
│   │   │   ├── domain-config/
│   │   │   ├── conflict/
│   │   │   ├── dependency/
│   │   │   ├── view/
│   │   │   └── explain/
│   │   └── log/              # 日志审计
│   │   │   ├── operation/
│   │   │   └ permission/
│   ├── App.vue
│   └── main.ts
├── .env                      # 环境变量
├── .env.development
├── .env.production
├── vite.config.ts
├── tsconfig.json
├── package.json
└── README.md
```

## 10. 环境变量

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_API_VERSION=2026-04-26

# .env.production
VITE_API_BASE_URL=https://api.accessmesh.com
VITE_API_VERSION=2026-04-26
```

## 12. 详细设计决策

### 12.1 登录与 Token 管理

| 决策项 | 选择 | 说明 |
|--------|------|------|
| Token 存储 | localStorage | 持久化，跨标签页共享 |
| Token 刷新 | 定时 + 拦截结合 | Token 有效期一半时定时刷新，401 时拦截刷新重试 |
| 登录路由 | `/login` | 标准登录页路径 |

### 12.2 租户管理

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 租户信息来源 | 登录响应返回 | 登录时后端返回用户租户列表 |
| 租户切换时机 | **登录前切换** | 登录页选择租户后进入该租户系统 |
| 跨租户用户 | 不支持 | 一个用户只属于一个租户，不考虑跨租户场景 |

**租户选择流程**：
```
登录页 → 显示租户列表（后端接口查询） → 选择租户 → 输入账号密码 → 认证 → 进入系统
```

### 12.3 权限控制

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 权限指令 | v-perm + hasPerm 函数 | 模板用指令，JS 逻辑用函数 |
| 权限码格式 | `{模块}:{页面}:{操作}` | 如 `sys:user:create` |
| 权限码存储 | 前端硬编码（当前） | 后续可改为菜单配置关联 |

**权限码集中管理**：
```typescript
// src/constants/permission.ts
export const PERM_CODES = {
  // 系统管理
  SYS_USER_CREATE: 'sys:user:create',
  SYS_USER_UPDATE: 'sys:user:update',
  SYS_USER_DELETE: 'sys:user:delete',
  SYS_USER_EXPORT: 'sys:user:export',

  // 权限中心
  PERM_ROLE_CREATE: 'perm:role:create',
  PERM_ROLE_UPDATE: 'perm:role:update',
  PERM_ROLE_DELETE: 'perm:role:delete',
  PERM_ROLE_GRANT: 'perm:role:grant',
  PERM_ROLE_VIEW: 'perm:role:view',

  // ... 其他模块
};
```

使用方式：
```vue
<script setup>
import { PERM_CODES } from '@/constants/permission';
</script>

<template>
  <button v-perm="[PERM_CODES.SYS_USER_CREATE]">新增用户</button>
</template>
```

### 12.4 动态路由

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 菜单结构 | 符合预期 | 支持树形结构，meta 存储路由信息 |
| 组件映射 | 组件名映射表 | 后端返回组件名，前端查找映射表 |
| 路由缓存 | 可配置 keepAlive | 菜单 meta.keepAlive 控制是否缓存 |
| 外链菜单 | 支持 | meta.external + meta.externalUrl |

**组件名映射表**：
```typescript
// src/router/componentMap.ts
export const COMPONENT_MAP: Record<string, Component> = {
  'Layout': () => import('@/layouts/default/index.vue'),
  'SystemUser': () => import('@/views/system/user/index.vue'),
  'SystemOrg': () => import('@/views/system/org/index.vue'),
  'SystemMenu': () => import('@/views/system/menu/index.vue'),
  'PermRole': () => import('@/views/perm/role/index.vue'),
  'PermResource': () => import('@/views/perm/resource/index.vue'),
  // ... 其他组件
};
```

**iframe 嵌入支持**：
```typescript
// 菜单配置支持 iframe 类型
{
  "resourceTypeCode": "MENU",
  "resourceCode": "menu:external:report",
  "resourceName": "外部报表系统",
  "meta": {
    "iframe": true,
    "iframeUrl": "https://report.example.com",
    "icon": "ep:link"
  }
}
```

### 12.5 布局与主题

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 布局模式 | 可配置切换 | 保留 pure-admin-thin 的布局切换功能 |
| 主题色 | Element Plus 默认 | 蓝色 #409EFF |
| Logo | 文字 "AccessMesh" | 暂无图片 Logo |

### 12.6 API 请求层

| 决策项 | 选择 | 说明 |
|--------|------|------|
| Axios 封装 | 改造复用 pure-admin-thin | 基于现有封装改造对接后端规范 |
| 分页/表单封装 | 参考 pure 和后端规范 | 实施时根据具体情况调整 |

---

## 13. 登录页租户选择设计

### 13.1 登录流程

```
┌─────────────────────────────────────────────────────┐
│                    登录页                            │
│  ┌───────────────────────────────────────────────┐  │
│  │  租户选择                                      │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │  选择租户: [租户A ▼]                      │  │  │
│  │  │  （下拉列表，后端接口查询可访问租户）      │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  账号密码                                      │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │  用户名: [________________]              │  │  │
│  │  │  密码:   [________________]              │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │         [ 登 录 ]                        │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 13.2 租户查询接口

```typescript
// GET /admin/api/tenant/list
// 或 POST /admin/api/tenant/query

interface TenantQueryResp {
  items: Array<{
    id: number;
    name: string;
    code: string;
  }>;
}
```

### 13.3 登录请求携带租户

```typescript
// POST /admin/api/auth/login
interface LoginReq {
  username: string;
  password: string;
  tenantId: number;  // 登录前选择的租户
}
```

---

## 14. iframe 嵌入设计

### 14.1 iframe 菜单类型

```json
{
  "resourceTypeCode": "MENU",
  "resourceCode": "menu:iframe:report",
  "resourceName": "外部报表",
  "meta": {
    "iframe": true,
    "iframeUrl": "https://report.example.com/embedded",
    "icon": "ep:link",
    "order": 10
  }
}
```

### 14.2 iframe 页面组件

```vue
<!-- src/views/iframe/index.vue -->
<template>
  <div class="iframe-container">
    <iframe
      :src="iframeUrl"
      frameborder="0"
      class="iframe-content"
      @load="onLoad"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{
  iframeUrl: string;
}>();

const loading = ref(true);

const onLoad = () => {
  loading.value = false;
};
</script>

<style scoped>
.iframe-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.iframe-content {
  width: 100%;
  height: 100%;
}
</style>
```

### 14.3 iframe 路由注册

```typescript
// 动态路由转换时
if (meta.iframe) {
  route.component = () => import('@/views/iframe/index.vue');
  route.props = { iframeUrl: meta.iframeUrl };
}
```

---

## 15. 权限码常量定义（完整版）

```typescript
// src/constants/permission.ts

/**
 * 权限码常量定义
 * 集中管理，便于后续变更和复用
 */
export const PERM_CODES = {
  // ========== 系统管理 ==========
  SYS_USER_CREATE: 'sys:user:create',
  SYS_USER_UPDATE: 'sys:user:update',
  SYS_USER_DELETE: 'sys:user:delete',
  SYS_USER_VIEW: 'sys:user:view',
  SYS_USER_EXPORT: 'sys:user:export',

  SYS_ORG_CREATE: 'sys:org:create',
  SYS_ORG_UPDATE: 'sys:org:update',
  SYS_ORG_DELETE: 'sys:org:delete',
  SYS_ORG_VIEW: 'sys:org:view',

  SYS_MENU_CREATE: 'sys:menu:create',
  SYS_MENU_UPDATE: 'sys:menu:update',
  SYS_MENU_DELETE: 'sys:menu:delete',
  SYS_MENU_VIEW: 'sys:menu:view',

  SYS_DICT_CREATE: 'sys:dict:create',
  SYS_DICT_UPDATE: 'sys:dict:update',
  SYS_DICT_DELETE: 'sys:dict:delete',
  SYS_DICT_VIEW: 'sys:dict:view',

  SYS_CONFIG_CREATE: 'sys:config:create',
  SYS_CONFIG_UPDATE: 'sys:config:update',
  SYS_CONFIG_DELETE: 'sys:config:delete',
  SYS_CONFIG_VIEW: 'sys:config:view',

  // ========== 权限中心 ==========
  PERM_TYPE_CREATE: 'perm:type:create',
  PERM_TYPE_UPDATE: 'perm:type:update',
  PERM_TYPE_DELETE: 'perm:type:delete',
  PERM_TYPE_VIEW: 'perm:type:view',

  PERM_DOMAIN_CREATE: 'perm:domain:create',
  PERM_DOMAIN_UPDATE: 'perm:domain:update',
  PERM_DOMAIN_DELETE: 'perm:domain:delete',
  PERM_DOMAIN_VIEW: 'perm:domain:view',

  PERM_ROLE_CREATE: 'perm:role:create',
  PERM_ROLE_UPDATE: 'perm:role:update',
  PERM_ROLE_DELETE: 'perm:role:delete',
  PERM_ROLE_VIEW: 'perm:role:view',
  PERM_ROLE_GRANT: 'perm:role:grant',
  PERM_ROLE_ASSIGN: 'perm:role:assign',

  PERM_RESOURCE_CREATE: 'perm:resource:create',
  PERM_RESOURCE_UPDATE: 'perm:resource:update',
  PERM_RESOURCE_DELETE: 'perm:resource:delete',
  PERM_RESOURCE_VIEW: 'perm:resource:view',

  PERM_OPERATION_CREATE: 'perm:operation:create',
  PERM_OPERATION_UPDATE: 'perm:operation:update',
  PERM_OPERATION_DELETE: 'perm:operation:delete',
  PERM_OPERATION_VIEW: 'perm:operation:view',

  PERM_CONDITION_CREATE: 'perm:condition:create',
  PERM_CONDITION_UPDATE: 'perm:condition:update',
  PERM_CONDITION_DELETE: 'perm:condition:delete',
  PERM_CONDITION_VIEW: 'perm:condition:view',

  PERM_USER_ROLE_ASSIGN: 'perm:user-role:assign',
  PERM_USER_ROLE_REVOKE: 'perm:user-role:revoke',
  PERM_USER_ROLE_VIEW: 'perm:user-role:view',

  PERM_SERVICE_CREATE: 'perm:service:create',
  PERM_SERVICE_UPDATE: 'perm:service:update',
  PERM_SERVICE_DELETE: 'perm:service:delete',
  PERM_SERVICE_VIEW: 'perm:service:view',
  PERM_SERVICE_SYNC: 'perm:service:sync',

  PERM_API_MAPPING_CREATE: 'perm:api-mapping:create',
  PERM_API_MAPPING_UPDATE: 'perm:api-mapping:update',
  PERM_API_MAPPING_DELETE: 'perm:api-mapping:delete',
  PERM_API_MAPPING_VIEW: 'perm:api-mapping:view',

  PERM_DEPENDENCY_CREATE: 'perm:dependency:create',
  PERM_DEPENDENCY_UPDATE: 'perm:dependency:update',
  PERM_DEPENDENCY_DELETE: 'perm:dependency:delete',
  PERM_DEPENDENCY_VIEW: 'perm:dependency:view',

  PERM_CONFLICT_CREATE: 'perm:conflict:create',
  PERM_CONFLICT_UPDATE: 'perm:conflict:update',
  PERM_CONFLICT_DELETE: 'perm:conflict:delete',
  PERM_CONFLICT_VIEW: 'perm:conflict:view',

  // ========== 视图与审计 ==========
  LOG_OPERATION_VIEW: 'log:operation:view',
  LOG_PERMISSION_VIEW: 'log:permission:view',
  PERM_VIEW_QUERY: 'perm:view:query',
  PERM_EXPLAIN: 'perm:explain:view',

  // ========== 辅助功能 ==========
  SYS_NOTICE_CREATE: 'sys:notice:create',
  SYS_NOTICE_UPDATE: 'sys:notice:update',
  SYS_NOTICE_DELETE: 'sys:notice:delete',
  SYS_NOTICE_VIEW: 'sys:notice:view',

  SYS_FILE_UPLOAD: 'sys:file:upload',
  SYS_FILE_DELETE: 'sys:file:delete',
  SYS_FILE_VIEW: 'sys:file:view',

  SYS_JOB_CREATE: 'sys:job:create',
  SYS_JOB_UPDATE: 'sys:job:update',
  SYS_JOB_DELETE: 'sys:job:delete',
  SYS_JOB_VIEW: 'sys:job:view',
  SYS_JOB_EXECUTE: 'sys:job:execute',
};

/**
 * 权限码分组（便于按模块批量检查）
 */
export const PERM_GROUPS = {
  SYS_USER: [
    PERM_CODES.SYS_USER_CREATE,
    PERM_CODES.SYS_USER_UPDATE,
    PERM_CODES.SYS_USER_DELETE,
    PERM_CODES.SYS_USER_VIEW,
  ],
  SYS_ORG: [
    PERM_CODES.SYS_ORG_CREATE,
    PERM_CODES.SYS_ORG_UPDATE,
    PERM_CODES.SYS_ORG_DELETE,
    PERM_CODES.SYS_ORG_VIEW,
  ],
  PERM_ROLE: [
    PERM_CODES.PERM_ROLE_CREATE,
    PERM_CODES.PERM_ROLE_UPDATE,
    PERM_CODES.PERM_ROLE_DELETE,
    PERM_CODES.PERM_ROLE_VIEW,
    PERM_CODES.PERM_ROLE_GRANT,
  ],
  // ... 其他分组
};
```

---

## 16. 组件名映射表（完整版）

```typescript
// src/router/componentMap.ts
import type { Component } from 'vue';

/**
 * 组件名映射表
 * 后端返回组件名，前端通过此表查找实际组件
 */
export const COMPONENT_MAP: Record<string, () => Promise<Component>> = {
  // 布局
  'Layout': () => import('@/layouts/default/index.vue'),
  'BlankLayout': () => import('@/layouts/blank/index.vue'),

  // 系统管理
  'SystemUser': () => import('@/views/system/user/index.vue'),
  'SystemOrg': () => import('@/views/system/org/index.vue'),
  'SystemMenu': () => import('@/views/system/menu/index.vue'),
  'SystemDict': () => import('@/views/system/dict/index.vue'),
  'SystemConfig': () => import('@/views/system/config/index.vue'),
  'SystemNotice': () => import('@/views/system/notice/index.vue'),
  'SystemFile': () => import('@/views/system/file/index.vue'),
  'SystemJob': () => import('@/views/system/job/index.vue'),

  // 权限中心
  'PermType': () => import('@/views/perm/type-def/index.vue'),
  'PermDomain': () => import('@/views/perm/domain/index.vue'),
  'PermRole': () => import('@/views/perm/role/index.vue'),
  'PermResource': () => import('@/views/perm/resource/index.vue'),
  'PermOperation': () => import('@/views/perm/operation/index.vue'),
  'PermCondition': () => import('@/views/perm/condition/index.vue'),
  'PermUserRole': () => import('@/views/perm/user-role/index.vue'),
  'PermService': () => import('@/views/perm/service/index.vue'),
  'PermApiMapping': () => import('@/views/perm/api-mapping/index.vue'),
  'PermDomainConfig': () => import('@/views/perm/domain-config/index.vue'),
  'PermConflict': () => import('@/views/perm/conflict/index.vue'),
  'PermDependency': () => import('@/views/perm/dependency/index.vue'),
  'PermView': () => import('@/views/perm/view/index.vue'),
  'PermExplain': () => import('@/views/perm/explain/index.vue'),
  'PermChanges': () => import('@/views/perm/changes/index.vue'),

  // 日志审计
  'LogOperation': () => import('@/views/log/operation/index.vue'),
  'LogPermission': () => import('@/views/log/permission/index.vue'),

  // 首页
  'Dashboard': () => import('@/views/dashboard/index.vue'),

  // iframe 容器
  'IframePage': () => import('@/views/iframe/index.vue'),
};
```