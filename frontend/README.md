# AccessMesh 管理前端

AccessMesh 的管理控制台前端：登录、组织与用户、角色管理、资源与操作定义、权限授予、条件/冲突规则、业务域、系统与服务配置、操作日志、资源依赖等管理页面（均已切换真实接口，无业务 mock）。

## 技术栈

- Vue 3 + TypeScript + Vite
- Element Plus（组件库）
- pinia（状态）+ vue-router（路由）
- pnpm（包管理，`preinstall` 强制——请勿使用 npm/yarn 安装依赖）

## 开发模式

```bash
pnpm install
pnpm dev        # 默认端口 8848（VITE_PORT 可覆盖）
```

- API 经 vite 代理把 `/api` 同路径转发到 Gateway（`VITE_PROXY_TARGET`，默认 `http://localhost:8080`）——见 `vite.config.ts`。
- 本机同起 Nacos 容器（控制台 8848）时用 `VITE_PORT=8890 pnpm dev` 避让端口。
- 登录链路为真实接口（`/api/access/auth/**`）；`VITE_MOCK_LOGIN=true` 仅用于纯前端联调（`.env.development`）。
- 侧栏菜单由登录后 `/api/access/auth/user-menu` 下发的菜单树渲染（后端派生可见性；越权直达由后端 VIEW 403 兜底）。

## 构建

```bash
pnpm build      # 产物 dist/（NODE_ENV=production，vite build）
```

生产形态为 **nginx 同源反代**：axios 全部相对路径 `/api/**`，要求与 API 网关同源部署（仓库根 `docker compose --profile app` 的前端容器即此形态，nginx 配置见本目录 `nginx.conf`：`location /api/ → gateway:8080`）。

## 目录速览

- `src/views/`——管理页面（组织与用户、权限授予、类型定义、条件/冲突规则等）
- `src/api/`——接口封装（全部真实后端契约）
- `src/router/`——路由与静态直达兜底
- `src/components/`——通用组件（`Re*` 前缀为跨页抽取组件）
- `mock/`——仅剩登录 mock 与静态路由 mock（业务 mock 已全部退役）

更多页面级设计见仓库 `docs/design/frontend/*.md`。

## 上游说明（Third-party Notice）

本项目前端派生自 [pure-admin-thin](https://github.com/pure-admin/pure-admin-thin)（vue-pure-admin 精简版，MIT License，© 2020-present pure-admin），并按 AccessMesh 需求大量改造（真实接口对接、页面重设计、mock 退役等）。上游 MIT 许可与版权声明保留于本目录 [LICENSE](LICENSE)。

组件库与构建链（Element Plus、Vite 等）的许可信息见各自包声明。
