# Access Mesh 前端

基于 Vue 3 + TypeScript + Vite + Element Plus + Tailwind CSS 构建的权限管理系统前端。

## 技术栈

| 技术 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5.x | 渐进式 JavaScript 框架 |
| TypeScript | 5.9.x | JavaScript 的类型超集 |
| Vite | 7.x | 下一代前端构建工具 |
| Element Plus | 2.11.x | Vue 3 组件库 |
| Tailwind CSS | 4.x | 原子化 CSS 框架 |
| Pinia | 3.x | Vue 3 状态管理 |
| Vue Router | 4.x | Vue.js 官方路由 |
| Axios | 1.x | HTTP 客户端 |

## 项目结构

```
frontend/
├── build/              # 构建配置
│   ├── plugins.ts      # Vite 插件配置
│   ├── optimize.ts     # 依赖预构建配置
│   └── compress.ts     # 压缩配置
├── mock/               # Mock 数据（开发环境）
├── public/             # 静态资源（不经过构建）
├── src/
│   ├── api/            # API 接口定义
│   │   ├── admin/      # 管理服务 API
│   │   │   ├── auth.ts       # 认证接口
│   │   │   ├── user.ts       # 用户管理
│   │   │   ├── org.ts        # 组织管理
│   │   │   ├── tenant.ts     # 租户管理
│   │   │   └── oauth2.ts     # OAuth2 接口
│   │   ├── system/     # 系统管理 API
│   │   │   ├── dict.ts       # 字典管理
│   │   │   ├── notice.ts     # 通知管理
│   │   │   ├── file.ts       # 文件管理
│   │   │   ├── job.ts        # 定时任务
│   │   │   └── config.ts     # 系统配置
│   │   ├── perm/       # 权限中心 API
│   │   │   ├── role.ts       # 角色管理
│   │   │   ├── resource.ts   # 资源管理
│   │   │   ├── permission.ts # 权限管理
│   │   │   └── audit-log.ts  # 审计日志
│   │   └── routes.ts   # 动态路由 API
│   ├── assets/         # 静态资源（经过构建）
│   ├── components/     # 公共组件
│   ├── constants/      # 常量定义
│   ├── directives/     # 自定义指令
│   │   ├── auth/       # v-auth 指令
│   │   └── perms/      # v-perms 指令
│   ├── layout/         # 布局组件
│   ├── plugins/        # 插件配置
│   ├── router/         # 路由配置
│   │   ├── modules/    # 路由模块
│   │   └── utils.ts    # 路由工具
│   ├── store/          # Pinia 状态管理
│   │   └── modules/
│   │       ├── user.ts       # 用户状态
│   │       ├── tenant.ts     # 租户状态
│   │       ├── oauth2.ts     # OAuth2 状态
│   │       └── permission.ts # 权限状态
│   ├── style/          # 全局样式
│   ├── utils/          # 工具函数
│   │   ├── http/       # HTTP 客户端
│   │   │   ├── index.ts          # 拦截器
│   │   │   ├── tokenRefreshScheduler.ts  # Token 定时刷新
│   │   │   └ refreshLock.ts     # 刷新锁
│   │   ├── auth.ts     # Token 管理
│   │   ├── oauth2.ts   # OAuth2 流程
│   │   ├── pkce.ts     # PKCE 工具
│   │   └ message.ts    # 消息提示
│   │   └ tree.ts       # 树形结构工具
│   ├── views/          # 页面视图
│   │   ├── login/      # 登录页
│   │   ├── system/     # 系统管理
│   │   │   ├── user/         # 用户管理
│   │   │   ├── org/          # 组织管理
│   │   │   ├── dict/         # 字典管理
│   │   │   ├── notice/       # 通知管理
│   │   │   ├── file/         # 文件管理
│   │   │   ├── job/          # 定时任务
│   │   │   └── config/       # 系统配置
│   │   ├── permission/ # 权限中心
│   │   │   ├── role/         # 角色管理
│   │   │   ├── resource/     # 资源管理
│   │   │   ├── permission/   # 权限管理
│   │   │   ├── page/         # 权限视图
│   │   │   └── audit-log/    # 审计日志
│   │   └── welcome/    # 欢迎页
│   ├── App.vue         # 根组件
│   └── main.ts         # 入口文件
├── types/              # 全局类型定义
├── .env.development    # 开发环境变量
├── nginx.conf          # Nginx 配置（生产环境）
├── Dockerfile          # Docker 构建文件
├── vite.config.ts      # Vite 配置
├── package.json        # 项目配置
└ pnpm-lock.yaml        # 锁文件
```

## 功能模块

### 1. 认证与授权

| 功能 | 说明 |
|---|---|
| OAuth2 登录 | 支持 PKCE 授权码流程 |
| 传统登录 | 用户名/密码 + 验证码 |
| 租户切换 | 多租户支持 |
| Token 刷新 | 自动刷新 + 定时调度 |

### 2. 系统管理

| 模块 | 功能 |
|---|---|
| 用户管理 | 用户 CRUD、组织关联、角色分配 |
| 组织管理 | 组织树管理、层级维护 |
| 字典管理 | 字典类型 + 数据维护 |
| 通知管理 | 通知发布、状态管理 |
| 文件管理 | 文件上传、下载、批量删除 |
| 定时任务 | 任务配置、执行日志、手动触发 |
| 系统配置 | 配置项管理 |

### 3. 权限中心

| 模块 | 功能 |
|---|---|
| 角色管理 | 角色类型维护、权限配置 |
| 资源管理 | 资源注册、操作定义 |
| 权限管理 | 授权、撤销、批量操作 |
| 权限视图 | 用户权限概览、资源权限树 |
| 审计日志 | 操作记录查询 |

## 权限控制

### 页面级权限

使用路由 `meta.roles` 或 `meta.auths` 字段控制：

```typescript
// router/modules/system.ts
{
  path: "/system/user",
  meta: {
    title: "用户管理",
    roles: ["admin"]  // 只有 admin 角色可访问
  }
}
```

### 按钮级权限

使用 `v-perms` 指令或 `hasPerms` 函数：

```vue
<!-- 指令方式 -->
<el-button v-perms="'system:user:create'">新增用户</el-button>

<!-- 函数方式 -->
<script setup>
import { hasPerms } from "@/utils/auth";

const canCreate = hasPerms("system:user:create");
</script>
```

## 开发指南

### 环境要求

- Node.js 20.19+ 或 22.13+
- pnpm 9+

### 安装依赖

```bash
pnpm install
```

### 启动开发服务器

```bash
pnpm dev
```

### 构建生产版本

```bash
pnpm build
```

### 类型检查

```bash
pnpm typecheck
```

### Lint 检查

```bash
pnpm lint        # 全量检查
pnpm lint:eslint # ESLint 检查
pnpm lint:prettier # Prettier 格式化
pnpm lint:stylelint # Stylelint 检查
```

### 构建分析

```bash
pnpm report
```

## Docker 部署

### 构建镜像

```bash
docker build -t access-mesh-frontend:latest .
```

### 运行容器

```bash
docker run -d \
  --name frontend \
  -p 80:80 \
  --network access-mesh-network \
  access-mesh-frontend:latest
```

### 环境变量

生产环境需配置后端服务地址，可通过 nginx.conf 中的 proxy_pass 配置调整。

## 配置说明

### 环境变量

| 变量 | 说明 | 示例 |
|---|---|---|
| VITE_API_BASE_URL | API 基础地址 | http://localhost:8080 |
| VITE_API_VERSION | API 版本 | v1 |
| VITE_OAUTH2_CLIENT_ID | OAuth2 客户端 ID | accessmesh-web |
| VITE_OAUTH2_CLIENT_SECRET | OAuth2 客户端密钥 | xxx |

### HTTP 拦截器

- 自动 Token 注入
- Token 过期自动刷新
- 租户 ID Header 注入
- 统一错误处理（按错误码范围分类）

### Token 刷新策略

- 定时刷新：Token 有效期 50% 时触发
- 拦截器刷新：过期时应急刷新
- 刷新锁：防止多请求并发刷新

## 编码规范

详见 `CLAUDE.md` 和 `.claude/rules/frontend-coding-standards.md`。

关键规范：
- 必须使用 pnpm
- 必须使用 `<script setup lang="ts">` + `defineOptions`
- 必须使用内联类型导入 `{ type X }`
- 组件命名使用 `Re` 前缀
- 权限控制使用 `hasPerms` 或 `v-perms`
- 自闭合标签格式

## 许可证

MIT License