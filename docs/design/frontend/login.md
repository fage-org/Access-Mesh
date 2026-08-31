---
doc_type: design
title: 登录页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-08-31   # 2026-08-31 T-FE-015 收口：menus 后端派生接线完成（侧栏直接渲染 user-menu 树、会话恢复 fail-closed、/menu-retry 重试页），mock 与静态路由口径段同步终态化；此前：2026-08-31 T-PERM-037（归入登记）、2026-08-24
---

# 登录页 前端设计（T-FE-041 真实登录链路）

## 布局结构

pure-admin 模板登录布局不变（背景插画 + 右侧登录框 + 主题切换开关）。表单纵向排列：

1. 账号输入框（可清空）
2. 密码输入框（可清空 + 显隐切换）
3. **验证码输入框（4 位，append 区嵌验证码图片，120×40）**——图片点击即刷新；图片未加载时显示"点击加载"占位
4. 登录按钮（loading/disabled 防重复提交；Enter/NumpadEnter 防抖触发）

无"记住我"勾选 UI（模板 store 保留 isRemembered 机制，未挂页面控件）；无短信登录/OAuth2 入口。

## 字段定义

表单模型 `ruleForm`：`username`（必填）、`password`（仅必填——登录不是设置密码的场景，长度/复杂度约束属改密流程，后端 `LoginReq.password` 仅 `@NotBlank`、`ResetPasswordReq` 才有 8-32 位约束）、`captchaCode`（必填）。
`captchaId` 不进表单，由页面 ref 持有（用户不可见）。

提交体（`LoginFormData` + api 层固定注入）：

| 字段 | 来源 | 说明 |
|---|---|---|
| `tenantId` | api 层常量 `FIXED_TENANT_ID = "1"` | bootstrap 固定租户；多租户选择器 Phase 3 |
| `clientId` | api 层常量 `FIXED_CLIENT_ID = "admin-web"` | oauth2_client 种子客户端（grant_types 含 password） |
| `username` / `password` | 表单 | — |
| `captchaId` / `captchaCode` | 页面 ref + 表单 | 后端一次性消费 |

## 交互流程

1. **页面加载**（onMounted）→ `POST /auth/captcha` → 展示图片（base64 已含 `data:image/png;base64,` 前缀）、记录 captchaId；失败弹"验证码获取失败"，可点击图片/占位重试。
2. **提交** → `loginByUsername` → 成功：`initRouter()`（纯静态路由）→ 跳 `getTopMenu()`（里程碑 A 即 `/welcome`）→ "登录成功"（若 `forceResetPwd=true` 追加非阻断 warning「当前密码为初始密码，请联系管理员重置」——系统暂无自助改密通道，T-ADMIN-022）；失败（业务 code/网络异常，经 `unwrap` 抛 `RequestError`）：展示后端 message → **无条件刷新验证码**（旧码已被后端消费；刷新 promise 纳入按钮 loading——期间不可重复提交，旧 captchaId/输入即刻失效，新码到达后恢复）。登录失败提示经 message 透传天然区分：**10003 停用**（管理员手工启停，需管理员恢复）与 **10004 临时锁定**（失败计数键剩余 TTL 自动恢复，文案含 30 分钟指引）——T-ADMIN-022 口径，`sys_user.status` 仅 0/1，临时锁定不落库。
3. **验证码点击刷新**：任何时刻点击图片重新拉取（发起即失效旧验证码 + 清空输入）。

验证码有效期 5 分钟、一次性；后端运行时强制校验（无开关）。

## API 依赖（链接后端契约章节）

- `POST /auth/captcha` → `PermResult<CaptchaResp{captchaId, image}>`（契约来源：后端 `AdminAuthController`/`CaptchaResp`——`admin-service-api-contract.md` 尚未收录平台登录端点，以代码为准）
- `POST /auth/login` → `PermResult<LoginResp{accessToken, refreshToken(null), expiresIn(秒), tokenType, userId, username, tenantId, forceResetPwd}>`（契约来源同上：`LoginReq`/`LoginResp`）；业务失败 HTTP 200 + code≠200
- `POST /auth/user-menu` → `PermResult<UserMenuData{menus, roles, permissions}>`（登录成功后 store 拉取；HTTP 401 会话失效不降级 rethrow，其余异常仅 console.warn 不阻断登录）
- 路径经 Gateway 外部约定（`/auth/**` StripPrefix=0 直通）；开发环境由 vite proxy 同路径转发（`VITE_PROXY_TARGET`）

## 组件结构（含可复用组件识别）

- `views/login/index.vue`：页面 + 验证码状态（captchaId/captchaImage/refreshCaptcha）
- `views/login/utils/rule.ts`：表单规则（含 captchaCode required）
- `api/auth.ts`：`getCaptcha`/`login`（`unwrap` 解包）/`getUserMenu` + 固定常量
- `store/modules/user.ts`：`loginByUsername`（unwrap → expiresIn 转绝对时间 → setToken → refreshUserMenu）；**无刷新令牌链路**（后端普通 Sa-Token 会话 refreshToken 为 null）

## 权限接线（hasPerms → 按钮 → 降级）

登录页本身无权限门禁（白名单路由）。登录后角色/权限经 `/auth/user-menu` 写入 store，供路由 `auths` 过滤与页面按钮 `hasPerms` 使用。`forceResetPwd` 提示适配已随 T-ADMIN-022 落地（登录成功后非阻断 warning）。

## 令牌生命周期与 401 窄处理

- `expiresIn`（秒）→ `new Date(Date.now() + expiresIn*1000)` 绝对时间供 `setToken`（cookie + localStorage）。
- 无 refresh-token/自动续期/重试体系（后端无 `/refresh-token`，相关模板代码已删除）。
- 请求拦截器：本地 `expires` 到期 → `logOut()` 清会话回登录页（本次请求无令牌放行，由 Gateway 401 兜底）；白名单 `/auth/captcha`、`/auth/login` 不附加令牌。
- 响应拦截器：**仅** HTTP 401 → `logOut()` 清会话回登录页；403/503 等由页面自行处理。

## mock 与动态路由口径（T-FE-041 决策）

- `mock/login.ts` 由 `VITE_MOCK_LOGIN`（.env.development，默认 **false**）控制注册；开启时注册 `/auth/captcha`（SVG 占位图）+ `/auth/login` + `/auth/user-menu`，响应壳已对齐 PermResult，前端代码零分支（开关经 wrapperEnv 写回 `process.env` 生效，已端到端验证：后端未启动时三端点全走 mock；关闭时请求穿透 vite 代理）。生产构建 mock 由 `VITE_ENABLE_PROD_MOCK=false` 关闭。mock user-menu 自 T-FE-015 起下发最小菜单树（welcome 纯展示，对齐 bootstrap 种子形态）——侧栏唯一数据源已切本接口 menus 树，空数组会渲染为失败占位。
- 纯静态路由：`initRouter` 不再请求 `/get-async-routes`（`src/api/routes.ts` 已删除），路由注册由 `router/modules/*.ts` 静态维护；**侧栏菜单已切后端派生（T-FE-015 已接线 2026-08-31）**——`initRouter` 将 `/auth/user-menu` 的 menus 树直接渲染为侧栏（标题/图标/层级来自 sys_menu bootstrap 种子，可见性 = v3.5 §4.1 ∃op 派生），`meta.showLink` 不再控制侧栏；会话恢复 = 已登录 F5/启动重取 user-menu，失败 fail-closed 空菜单 + 侧栏「菜单加载失败，点击重试」占位项（跳 `/menu-retry` 重试页），不持久化、不回退全量静态菜单。
