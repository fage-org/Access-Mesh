---
doc_type: design
title: 登录页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-09-20   # 2026-09-20 T-FE-048 收口：新增「会话权限热刷新」节（统一能力刷新入口 + 403 触发 + 手动入口），§API 依赖 user-menu 拉取时机与 §令牌生命周期 403 分支同步；此前：T-FE-046 强制改密闭环、T-FE-049 登录提示两态、T-FE-045 登出流程节、2026-09-15 T-FE-015、2026-08-31 T-FE-041
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
2. **提交** → `loginByUsername` → 成功：`initRouter()`（纯静态路由）→ 跳 `getTopMenu()`（里程碑 A 即 `/welcome`；`forceResetPwd=true` 时该跳转被路由守卫阻断改投 `/change-password`，见「强制改密闭环」节）→ **登录提示按菜单状态两态（T-FE-049，`resolveLoginMessages` 唯一出口，非阻断进系统）**：菜单已加载（menus 非空）=「登录成功」success；**半成功**（menus 为空且最近一次 user-menu 拉取失败，含 initRouter 隐式重试仍失败）=一条 warning「登录成功，但菜单与权限加载失败，进入系统后请点击侧栏占位项重试」（「进入系统后」限定：强制改密阻断人群登录后停在 /change-password 全屏页无侧栏，指引动作在改密进入系统后才可达，claude 外评 P3 处置 2026-09-20）；**拉取成功但账号无菜单**（零权限，如建号未配角色）=warning「登录成功，当前账号无可用菜单，请联系管理员分配权限」。`forceResetPwd` 登录页不再提示（T-FE-046 起由阻断流程取代，标记写入 userKey 由守卫消费）；失败（业务 code/网络异常，经 `unwrap` 抛 `RequestError`）：展示后端 message → **无条件刷新验证码**（旧码已被后端消费；刷新 promise 纳入按钮 loading——期间不可重复提交，旧 captchaId/输入即刻失效，新码到达后恢复）。登录失败提示经 message 透传天然区分：**10003 停用**（管理员手工启停，需管理员恢复）与 **10004 临时锁定**（失败计数键剩余 TTL 自动恢复，文案含 30 分钟指引）——T-ADMIN-022 口径，`sys_user.status` 仅 0/1，临时锁定不落库。
3. **验证码点击刷新**：任何时刻点击图片重新拉取（发起即失效旧验证码 + 清空输入）。

验证码有效期 5 分钟、一次性；后端运行时强制校验（无开关）。

## API 依赖（链接后端契约章节）

- `POST /api/access/auth/captcha` → `R<CaptchaResp{captchaId, image}>`（契约来源：后端 `AdminAuthController`/`CaptchaResp`——平台登录端点族未成册，契约总册 §6.4 已登记，以代码为准）
- `POST /api/access/auth/login` → `R<LoginResp{accessToken, refreshToken(null), expiresIn(秒), tokenType, userId, username, tenantId, forceResetPwd}>`（契约来源同上：`LoginReq`/`LoginResp`）；业务失败 HTTP 200 + code≠200
- `POST /api/access/auth/user-menu` → `R<UserMenuData{menus, roles, permissions}>`（拉取时机（T-FE-048 起）：登录（loginByUsername 直调）、会话恢复（initRouter 拉取分支，F5/启动）、403 自动刷新、顶栏手动入口、授予页重试（retryLoadDeps）——登录外四路均收敛到「会话权限热刷新」节的能力刷新入口，登录路径复用 loginByUsername 已拉取结果不重复请求；HTTP 401 会话失效不降级 rethrow，其余异常仅 console.warn 不阻断登录）
- 路径经 Gateway 统一路由（`/api/access/**` 单命名空间，无 StripPrefix——T-ACCESS-042）；开发环境由 vite proxy 单条 `/api` 同路径转发（`VITE_PROXY_TARGET`）

## 组件结构（含可复用组件识别）

- `views/login/index.vue`：页面 + 验证码状态（captchaId/captchaImage/refreshCaptcha）
- `views/login/utils/rule.ts`：表单规则（含 captchaCode required）
- `views/login/utils/messages.ts`：`resolveLoginMessages` 登录成功提示语义唯一出口（T-FE-049 两态；forceResetPwd 提示分支已随 T-FE-046 阻断流程删除）
- `views/change-password/index.vue` + `utils/rules.ts`：强制改密页（T-FE-046，见「强制改密闭环」节）
- `api/auth.ts`：`getCaptcha`/`login`/`logout`（后两者 `unwrap` 解包）/`getUserMenu` + 固定常量
- `store/modules/user.ts`：`loginByUsername`（unwrap → expiresIn 转绝对时间 → setToken → LoginResp 的 userId/forceResetPwd 随登录写入 userKey（T-FE-046，阻断标记与改密请求主体）→ refreshUserMenu）、`refreshUserMenu`（成败维护 `menuLoadFailed`——空侧栏两态区分的事实来源，成功但空 menus=合法形态非失败）、`logOut`（服务端注销优先，见「登出流程」）；**无刷新令牌链路**（后端普通 Sa-Token 会话 refreshToken 为 null）

## 权限接线（hasPerms → 按钮 → 降级）

登录页本身无权限门禁（白名单路由）。登录后角色/权限经 `/api/access/auth/user-menu` 写入 store，供路由 `auths` 过滤与页面按钮 `hasPerms` 使用。`forceResetPwd=true` 登录后由路由守卫阻断至 `/change-password`（T-FE-046，见下节）——T-ADMIN-022「非阻断提示引导联系管理员」口径就此退役（自助通道 `/api/access/user/reset-password` 自身路径已定位，T-PERM-067）。

## 令牌生命周期与 401 窄处理

- `expiresIn`（秒）→ `new Date(Date.now() + expiresIn*1000)` 绝对时间供 `setToken`（cookie + localStorage）。
- 无 refresh-token/自动续期/重试体系（后端无 `/refresh-token`，相关模板代码已删除）。
- 请求拦截器：本地 `expires` 到期 → `logOut()`（真注销，见「登出流程」）清会话回登录页（本次请求无令牌放行，由 Gateway 401 兜底）；白名单 `/api/access/auth/captcha`、`/api/access/auth/login` 不经拦截器令牌逻辑，`/api/access/auth/logout` 亦在白名单但由调用方显式携令牌（见「登出流程」）。
- 响应拦截器：**仅** HTTP 401 → `logOut()`（真注销）清会话回登录页；HTTP 403 → 触发会话权限热刷新（见下节，T-FE-048）；503 等其余状态码由页面层自行处理。

## 会话权限热刷新（T-FE-048，2026-09-19 拍板）

**问题**：管理员给用户 A 新增 `USER:UPDATE` 后 A 的按钮不出现、撤权后按钮残留点击 403，只能 F5/重登——`refreshUserMenu()` 原全仓仅 loginByUsername 与 initRouter 两处调用，且**仅刷 store 不重建侧栏**（permissionStore.wholeMenus 唯一重建点在 initRouter），「按钮权限已新、侧栏旧菜单残留继续点击 403」。**机制必须落在被授权人会话侧**（管理员授权发生在管理员自己的浏览器，页面内刷新帮不了被授权人），403 触发正是「被授权人下一个动作即自愈」的形态（弃轮询/推送——前者延迟=N 分钟且常驻请求量、后者需后端新事件机制属长期项）。

**统一能力刷新入口**（`src/router/utils.ts refreshSessionCapability`，单函数原子更新）：权限串（roles/permissions/menus 经 user store `refreshUserMenu`）+ 侧栏 wholeMenus（复用 initRouter 的 `buildSidebarMenus`/`handleBackendMenus` 接线；menus 空时按 `menuLoadFailed` 渲染两态占位）。四个调用方共用：initRouter、403 自动刷新、顶栏手动入口、授予页 retryLoadDeps（permission-grant.md §10）。语义：

- 成功：侧栏即时重建（撤销的菜单项从侧栏消失；全撤销落「当前账号无可用菜单」占位）；
- 失败：原样抛出且不触碰 wholeMenus——按钮/侧栏维持旧态（后端 fail-closed 兜底）；门禁状态机维持 loaded（failed 仅由首次加载失败产生）——**T-FE-056 落地后路由门禁 path 集/状态机更新挂接于本入口**（同 owner，勿另开通道）；
- 不清理 multiTags 已缓存标签（标签指向的路由由后端 403 兜底）；不经 `handleAsyncRoutes`（其 multiTags 重置仅属登录/F5 的 initRouter 全量路径）。

**403 自动刷新**（`src/utils/http/index.ts` 响应拦截器 403 分支）：窗口去重触发能力刷新——**10s 去重窗口**（2026-09-20 用户拍板；起算于触发时刻，窗口内在途双保险：短时间内多次 403 只刷一次），失败静默维持旧态（仅 console.warn，无 message 弹窗——与手动入口的显式反馈口径区分），**不自动重放原请求**（防循环，用户重新点击即可），排除 user-menu 自身（刷新入口即该请求，其 403 下重发无自愈可能）。403 ≠ 必然权限变更（可能是配错/越权访问）——刷新无害（多一次 user-menu 请求），按钮显隐以最新事实为准。错误本身仍原样 reject 由页面层处理展示。

**顶栏手动入口**（lay-navbar 工具区「刷新权限」图标按钮，经 useNav `refreshPermission`）：直连能力刷新入口（**不走 403 去重通道**——显式动作立即响应，亦不受 10s 窗口限制）；模块级在途标记防并发双发（useNav 多组件实例共享）；成功 message「权限已刷新」且按钮显隐/侧栏菜单即时更新；失败弹错误 message「权限刷新失败，请稍后重试」（2026-09-20 用户拍板：显式动作配显式反馈）。

**回归锁**（`src/utils/http/index.spec.ts` + `src/views/perm/grant/utils/hook.spec.ts`）：①403 触发能力刷新恰好一次（窗口内第二次 403 不再触发）；②403 自动刷新成功后侧栏同步重建（撤销项消失/全撤销占位）——两条旧实现（无触发/仅 initRouter 重建）下必红；③retryLoadDeps 先刷权限串（刷新使权限翻真后重试才发依赖请求）——旧实现下必红；附加锁：窗口过期可再触发 / user-menu 自身 403 不触发 / 刷新失败静默维持旧态 / 401 分支不受扰 / 刷新失败不阻断授予页重试。

## 登出流程（T-FE-045 真注销）

登出入口（顶栏下拉 `useNav.logout`、请求拦截器本地过期分支、响应拦截器 401 分支）统一走 `useUserStore.logOut()`：

1. **服务端注销优先**：持令牌时先 `POST /api/access/auth/logout`（`api/auth.ts logout()`，`R<Void>` 经 unwrap 解包）。注销请求**显式携带 Authorization 头**（store 读当前 token 经 `formatToken` 构造后传入）——该端点已加入 http 请求白名单，拦截器不注入令牌也不做过期判定（防过期分支 `logOut` 递归），令牌仍照常送达服务端：本地 `expires` 已到期路径触发的登出，也能注销可能仍存活的服务端会话（本地到期时刻与 Sa-Token 服务端会话不完全同步）。
2. **本地清理无条件**：服务端注销失败（网络/后端异常）仅 `console.warn` 不弹错——退出意图已明确，本地清理不可被服务端失败绑架（浏览器凭证已清，残留服务端会话按 Sa-Token TTL 自然过期）。清理序：清 Pinia（username/roles/permissions/menus）→ `removeToken`（cookie+localStorage）→ multiTags 重置 → `resetRouter` → 跳 `/login`。
3. **串行防抖**：登出进行中重复触发（连点/拦截器程序化调用）直接短路——同一登出动作只发一次 `POST /logout`；登出完成后重复触发时 `getToken()` 已无令牌，不再发请求，仅幂等清理+跳转。

> T-FE-041 原「前端登出仅清本地、不调接口」口径退役（2026-09-19，T-FE-045）——用户点退出后已复制出去的 Bearer token 在服务端仍有效的问题就此闭合。logoutAll 踢全部端点与多设备会话管理为非目标（另立评估）。

## 强制改密闭环（T-FE-046，2026-09-19 拍板）

**流程**：登录成功（`forceResetPwd=true`）→ 标记随登录写入 userKey → 登录页跳 `getTopMenu()` 被路由守卫阻断改投 `/change-password` → 用户设置新密码（8-32 位，无旧密码字段）→ `POST /api/access/user/reset-password`（`userId=当前登录用户自身`、`newPassword` 必传；自身路径免门禁，契约 §7.7）→ 后端置 `force_reset_pwd=false`（T-PERM-066）+ 前端 `clearForceResetPwdFlag` 同步清标记 → 会话保留直接进入系统（后端改密不注销会话——代码级核实：`resetPassword` 全方法无 StpUtil 踢出/登出调用）。

**阻断口径**：

- 阻断是**导航层 UX 门禁，不是安全边界**——后端仍是最终授权边界（改密页自身经会话认证可达，业务接口由后端 403 兜底，T-PERM-037 口径）；后端无全局 force-reset 请求拦截，前端阻断是唯一闸门。
- 守卫放行清单（`router/index.ts forceResetAllowPaths`）：`/change-password`、`/login` 与公共错误页（`/access-denied`、`/server-error`、`/menu-retry`、`/error/403|404|500`），其余路由 redirect `/change-password`。不含 `/redirect`：真实标签刷新导航是 `/redirect`+fullPath 参数化路径（精确匹配恒不中），裸 `/redirect` 命中 Layout 父记录会把侧栏壳放给阻断人群（双轨评审 P2 处置，2026-09-20）。
- 页面 `/change-password` 注册于 remaining.ts（全屏独立页，无 Layout——阻断人群不应看到侧栏），登录即达无需权限码。

**标记生命周期**（与登录主体绑定、跨标签共享）：标记 = userKey（localStorage）内 `forceResetPwd` 字段——**登录覆盖**（每次登录从 LoginResp 重写，普通登录写 false 清残留）、**登出清除**（removeToken 整体清 userKey）、**改密成功置 false**（`clearForceResetPwdFlag`，其余字段保留）。sessionStorage 每标签独立（新开标签漏失阻断）不合格，故选 localStorage；跨标签经共享存储自然生效（守卫每次导航重读，无需 storage 事件广播——广播仅缩短延迟，不做）。`userId` 同随登录写入 userKey（改密请求主体；改密前旧会话缺 userId 时页面提示重新登录，不做静默兜底）。

**UI 口径**：首期不设旧密码字段（后端旧密码校验未实现，T-PERM-066 明确另立任务，前端收集不校验的字段是假安全）；文案用「设置新密码」；长度 8-32 对齐契约 §7.7 `ResetPasswordReq`。

**Gateway 前置（T-GW-009，2026-09-19 收口）**：`/api/access/user/reset-password` 已纳入 Gateway 会话入口族白名单 + access-service 密钥豁免清单——否则快照判定对无 API:ACCESS 的普通用户 403，阻断落地即把目标人群锁死在改密页。

> T-ADMIN-022「非阻断 warning 引导联系管理员」口径退役（2026-09-19，T-FE-046）——「系统无用户自助改密通道」的立项依据已被 T-PERM-067/066 证伪（自助通道=reset-password 自身路径 + 改密成功置 false），登录页 warning 分支已删。

## mock 与动态路由口径（T-FE-041 决策）

- `mock/login.ts` 由 `VITE_MOCK_LOGIN`（.env.development，默认 **false**）控制注册；开启时注册 `/api/access/auth/captcha`（SVG 占位图）+ `/api/access/auth/login` + `/api/access/auth/user-menu`，响应壳已对齐 R，前端代码零分支（开关经 wrapperEnv 写回 `process.env` 生效，已端到端验证：后端未启动时三端点全走 mock；关闭时请求穿透 vite 代理）。生产构建 mock 由 `VITE_ENABLE_PROD_MOCK=false` 关闭。mock user-menu 自 T-FE-015 起下发最小菜单树（welcome 纯展示，对齐 bootstrap 种子形态）——侧栏唯一数据源已切本接口 menus 树，空数组（拉取成功形态）渲染为「当前账号无可用菜单」占位（T-FE-049 两态——mock 场景不触发失败态）。
- 纯静态路由：`initRouter` 不再请求 `/get-async-routes`（`src/api/routes.ts` 已删除），路由注册由 `router/modules/*.ts` 静态维护；**侧栏菜单已切后端派生（T-FE-015 已接线 2026-08-31）**——`initRouter` 将 `/api/access/auth/user-menu` 的 menus 树直接渲染为侧栏（标题/图标/层级来自 sys_menu bootstrap 种子，可见性 = v3.5 §4.1 ∃op 派生），`meta.showLink` 不再控制侧栏；会话恢复 = 已登录 F5/启动重取 user-menu，失败 fail-closed 空菜单，不持久化、不回退全量静态菜单。**空侧栏占位项两态（T-FE-049，`resolveSidebarFallback` 按 `menuLoadFailed` 区分）**：拉取失败=「菜单加载失败，点击重试」（既有，跳 `/menu-retry` 重试页）；拉取成功但账号无菜单=「当前账号无可用菜单」——重试对该形态无意义，着陆页（同为 `/menu-retry`，页内自适应）引导联系管理员、保留「重新检查」入口（管理员补配后点击即恢复，无需重登）。
