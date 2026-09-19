---
doc_type: design
title: 登录页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-09-19   # 2026-09-19 T-FE-049 收口：登录成功提示按菜单状态两态化（半成功/零权限诚实提示，非阻断）+ menuLoadFailed 空侧栏两态区分；此前：2026-09-19 T-FE-045 登出流程节、2026-09-15 T-FE-015、2026-08-31 T-FE-041
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

1. **页面加载**（onMounted）→ `POST /api/access/auth/captcha` → 展示图片（base64 已含 `data:image/png;base64,` 前缀）、记录 captchaId；失败弹"验证码获取失败"，可点击图片/占位重试。
2. **提交** → `loginByUsername` → 成功：`initRouter()`（纯静态路由）→ 跳 `getTopMenu()`（里程碑 A 即 `/welcome`）→ **登录提示按菜单状态两态（T-FE-049，`resolveLoginMessages` 唯一出口，非阻断进系统）**：菜单已加载（menus 非空）=「登录成功」success；**半成功**（menus 为空且最近一次 user-menu 拉取失败，含 initRouter 隐式重试仍失败）=一条 warning「登录成功，但菜单与权限加载失败，请点击侧栏占位项重试」；**拉取成功但账号无菜单**（零权限，如建号未配角色）=warning「登录成功，当前账号无可用菜单，请联系管理员分配权限」。若 `forceResetPwd=true` 追加非阻断 warning「当前密码为初始密码，请联系管理员重置」（前端暂无自助改密 UI，API 层自助通道为 `/api/access/user/reset-password` 自身路径（T-PERM-067 定位），阻断闭环归 T-FE-046——其分支在 `resolveLoginMessages` 出口上扩展）；失败（业务 code/网络异常，经 `unwrap` 抛 `RequestError`）：展示后端 message → **无条件刷新验证码**（旧码已被后端消费；刷新 promise 纳入按钮 loading——期间不可重复提交，旧 captchaId/输入即刻失效，新码到达后恢复）。登录失败提示经 message 透传天然区分：**10003 停用**（管理员手工启停，需管理员恢复）与 **10004 临时锁定**（失败计数键剩余 TTL 自动恢复，文案含 30 分钟指引）——T-ADMIN-022 口径，`sys_user.status` 仅 0/1，临时锁定不落库。
3. **验证码点击刷新**：任何时刻点击图片重新拉取（发起即失效旧验证码 + 清空输入）。

验证码有效期 5 分钟、一次性；后端运行时强制校验（无开关）。

## API 依赖（链接后端契约章节）

- `POST /api/access/auth/captcha` → `R<CaptchaResp{captchaId, image}>`（契约来源：后端 `AdminAuthController`/`CaptchaResp`——平台登录端点族未成册，契约总册 §6.4 已登记，以代码为准）
- `POST /api/access/auth/login` → `R<LoginResp{accessToken, refreshToken(null), expiresIn(秒), tokenType, userId, username, tenantId, forceResetPwd}>`（契约来源同上：`LoginReq`/`LoginResp`）；业务失败 HTTP 200 + code≠200
- `POST /api/access/auth/user-menu` → `R<UserMenuData{menus, roles, permissions}>`（登录成功后 store 拉取；HTTP 401 会话失效不降级 rethrow，其余异常仅 console.warn 不阻断登录）
- 路径经 Gateway 统一路由（`/api/access/**` 单命名空间，无 StripPrefix——T-ACCESS-042）；开发环境由 vite proxy 单条 `/api` 同路径转发（`VITE_PROXY_TARGET`）

## 组件结构（含可复用组件识别）

- `views/login/index.vue`：页面 + 验证码状态（captchaId/captchaImage/refreshCaptcha）
- `views/login/utils/rule.ts`：表单规则（含 captchaCode required）
- `views/login/utils/messages.ts`：`resolveLoginMessages` 登录成功提示语义唯一出口（T-FE-049 两态+forceResetPwd 追加；T-FE-046 阻断分支在此扩展，勿散落组件内）
- `api/auth.ts`：`getCaptcha`/`login`/`logout`（后两者 `unwrap` 解包）/`getUserMenu` + 固定常量
- `store/modules/user.ts`：`loginByUsername`（unwrap → expiresIn 转绝对时间 → setToken → refreshUserMenu）、`refreshUserMenu`（成败维护 `menuLoadFailed`——空侧栏两态区分的事实来源，成功但空 menus=合法形态非失败）、`logOut`（服务端注销优先，见「登出流程」）；**无刷新令牌链路**（后端普通 Sa-Token 会话 refreshToken 为 null）

## 权限接线（hasPerms → 按钮 → 降级）

登录页本身无权限门禁（白名单路由）。登录后角色/权限经 `/api/access/auth/user-menu` 写入 store，供路由 `auths` 过滤与页面按钮 `hasPerms` 使用。`forceResetPwd` 提示适配已随 T-ADMIN-022 落地（登录成功后非阻断 warning）。

## 令牌生命周期与 401 窄处理

- `expiresIn`（秒）→ `new Date(Date.now() + expiresIn*1000)` 绝对时间供 `setToken`（cookie + localStorage）。
- 无 refresh-token/自动续期/重试体系（后端无 `/refresh-token`，相关模板代码已删除）。
- 请求拦截器：本地 `expires` 到期 → `logOut()`（真注销，见「登出流程」）清会话回登录页（本次请求无令牌放行，由 Gateway 401 兜底）；白名单 `/api/access/auth/captcha`、`/api/access/auth/login` 不经拦截器令牌逻辑，`/api/access/auth/logout` 亦在白名单但由调用方显式携令牌（见「登出流程」）。
- 响应拦截器：**仅** HTTP 401 → `logOut()`（真注销）清会话回登录页；403/503 等由页面自行处理。

## 登出流程（T-FE-045 真注销）

登出入口（顶栏下拉 `useNav.logout`、请求拦截器本地过期分支、响应拦截器 401 分支）统一走 `useUserStore.logOut()`：

1. **服务端注销优先**：持令牌时先 `POST /api/access/auth/logout`（`api/auth.ts logout()`，`R<Void>` 经 unwrap 解包）。注销请求**显式携带 Authorization 头**（store 读当前 token 经 `formatToken` 构造后传入）——该端点已加入 http 请求白名单，拦截器不注入令牌也不做过期判定（防过期分支 `logOut` 递归），令牌仍照常送达服务端：本地 `expires` 已到期路径触发的登出，也能注销可能仍存活的服务端会话（本地到期时刻与 Sa-Token 服务端会话不完全同步）。
2. **本地清理无条件**：服务端注销失败（网络/后端异常）仅 `console.warn` 不弹错——退出意图已明确，本地清理不可被服务端失败绑架（浏览器凭证已清，残留服务端会话按 Sa-Token TTL 自然过期）。清理序：清 Pinia（username/roles/permissions/menus）→ `removeToken`（cookie+localStorage）→ multiTags 重置 → `resetRouter` → 跳 `/login`。
3. **串行防抖**：登出进行中重复触发（连点/拦截器程序化调用）直接短路——同一登出动作只发一次 `POST /logout`；登出完成后重复触发时 `getToken()` 已无令牌，不再发请求，仅幂等清理+跳转。

> T-FE-041 原「前端登出仅清本地、不调接口」口径退役（2026-09-19，T-FE-045）——用户点退出后已复制出去的 Bearer token 在服务端仍有效的问题就此闭合。logoutAll 踢全部端点与多设备会话管理为非目标（另立评估）。

## mock 与动态路由口径（T-FE-041 决策）

- `mock/login.ts` 由 `VITE_MOCK_LOGIN`（.env.development，默认 **false**）控制注册；开启时注册 `/api/access/auth/captcha`（SVG 占位图）+ `/api/access/auth/login` + `/api/access/auth/user-menu`，响应壳已对齐 R，前端代码零分支（开关经 wrapperEnv 写回 `process.env` 生效，已端到端验证：后端未启动时三端点全走 mock；关闭时请求穿透 vite 代理）。生产构建 mock 由 `VITE_ENABLE_PROD_MOCK=false` 关闭。mock user-menu 自 T-FE-015 起下发最小菜单树（welcome 纯展示，对齐 bootstrap 种子形态）——侧栏唯一数据源已切本接口 menus 树，空数组会渲染为失败占位。
- 纯静态路由：`initRouter` 不再请求 `/get-async-routes`（`src/api/routes.ts` 已删除），路由注册由 `router/modules/*.ts` 静态维护；**侧栏菜单已切后端派生（T-FE-015 已接线 2026-08-31）**——`initRouter` 将 `/api/access/auth/user-menu` 的 menus 树直接渲染为侧栏（标题/图标/层级来自 sys_menu bootstrap 种子，可见性 = v3.5 §4.1 ∃op 派生），`meta.showLink` 不再控制侧栏；会话恢复 = 已登录 F5/启动重取 user-menu，失败 fail-closed 空菜单，不持久化、不回退全量静态菜单。**空侧栏占位项两态（T-FE-049，`resolveSidebarFallback` 按 `menuLoadFailed` 区分）**：拉取失败=「菜单加载失败，点击重试」（既有，跳 `/menu-retry` 重试页）；拉取成功但账号无菜单=「当前账号无可用菜单」——重试对该形态无意义，着陆页（同为 `/menu-retry`，页内自适应）引导联系管理员、保留「重新检查」入口（管理员补配后点击即恢复，无需重登）。
