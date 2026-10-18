---
doc_type: design
title: 登录页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-10-18
---

# 登录页 前端设计（T-FE-041 真实登录链路）

## 布局结构

pure-admin 模板登录布局不变（背景插画 + 右侧登录框 + 主题切换开关）。表单纵向排列：

1. 账号输入框（可清空）
2. 密码输入框（可清空 + 显隐切换）
3. **验证码输入框（4 位，append 区嵌验证码图片，120×40）**——图片点击即刷新；图片未加载时显示"点击加载"占位
4. 登录按钮（loading/disabled 防重复提交；Enter/NumpadEnter 防抖触发）

无"记住我"勾选 UI（模板 store 保留 isRemembered 机制，未挂页面控件）；无短信登录或外部 OAuth2 登录按钮；第三方应用授权从独立 `/oauth2/authorize` 页发起，必要时复用本登录页后返回确认页。

## 字段定义

表单模型 `ruleForm`：`username`（必填）、`password`（仅必填——登录不是设置密码的场景，长度/复杂度约束属改密流程，后端 `LoginReq.password` 仅 `@NotBlank`、`ResetPasswordReq` 才有 8-32 位约束）、`captchaCode`（必填）。
`captchaId` 不进表单，由页面 ref 持有（用户不可见）。

提交体（`LoginFormData`）：

| 字段 | 来源 | 说明 |
|---|---|---|
| `tenantCode` | 用户输入 | 不可变租户编码，严格小写字母开头，允许数字与连字符，最长 64 |
| `clientId` | 前端不发送 | 后端可选审计来源字段，普通登录不依赖 OAuth2 客户端 |
| `username` / `password` | 表单 | — |
| `captchaId` / `captchaCode` | 页面 ref + 表单 | 后端一次性消费 |

## 交互流程

1. **页面加载**（onMounted）→ `POST /api/access/auth/captcha` → 展示图片（base64 已含 `data:image/png;base64,` 前缀）、记录 captchaId；失败弹"验证码获取失败"，可点击图片/占位重试。
2. **提交** → `loginByUsername` → 成功：`initRouter()`（纯静态路由）→ 跳 `getTopMenu()`（里程碑 A 即 `/welcome`；`forceResetPwd=true` 时该跳转被路由守卫阻断改投 `/change-password`，见「强制改密闭环」节）→ **登录提示按菜单状态两态（T-FE-049，`resolveLoginMessages` 唯一出口，非阻断进系统）**：菜单已加载（menus 非空）=「登录成功」success；**半成功**（menus 为空且最近一次 user-menu 拉取失败，含 initRouter 隐式重试仍失败）=一条 warning「登录成功，但菜单与权限加载失败，进入系统后请点击侧栏占位项重试」（「进入系统后」限定：强制改密阻断人群登录后停在 /change-password 全屏页无侧栏，指引动作在改密进入系统后才可达，claude 外评 P3 处置 2026-09-20）；**拉取成功但账号无菜单**（零权限，如建号未配角色）=warning「登录成功，当前账号无可用菜单，请联系管理员分配权限」。`forceResetPwd` 登录页不再提示（T-FE-046 起由阻断流程取代，标记写入 userKey 由守卫消费）；失败（业务 code/网络异常，经 `unwrap` 抛 `RequestError`）：展示后端 message → **无条件刷新验证码**（旧码已被后端消费；刷新 promise 纳入按钮 loading——期间不可重复提交，旧 captchaId/输入即刻失效，新码到达后恢复）。登录失败提示经 message 透传天然区分：**10003 停用**（管理员手工启停，需管理员恢复）与 **10004 临时锁定**（失败计数键剩余 TTL 自动恢复，文案含 30 分钟指引）——T-ADMIN-022 口径，`sys_user.status` 仅 0/1，临时锁定不落库。
3. **验证码点击刷新**：任何时刻点击图片重新拉取（发起即失效旧验证码 + 清空输入）。

验证码有效期 5 分钟、一次性；后端运行时强制校验（无开关）。

<a id="captcha-accessibility-status"></a>
**验证码替代通道（2026-10-17 用户确认 L1）**：继续暂缓音频、行为式或其他替代验证方案（Q-068）。当前租户登录只有图片验证码，无法识别图片的用户缺少替代验证方式；刷新图片不能消除这项限制。不增加验证码跳过入口，不恢复短信登录。将来启动时先确定方案、登录入口覆盖范围与防自动化边界，再实施并验收可访问性。

## API 依赖（链接后端契约章节）

- `POST /api/access/auth/captcha` → `R<CaptchaResp{captchaId, image}>`（契约来源：后端 `AdminAuthController`/`CaptchaResp`——租户登录端点族已在契约总册 §6.4 登记）
- `POST /api/access/auth/browser-login` → `R<BrowserLoginResp>`（仅 sessionId/用户元数据，认证值只经 HttpOnly Cookie）；请求复用 LoginReq，详见[网页会话契约](../access-service-api-contract.md#browser-session-contract)。原 Bearer 登录入口继续供工具使用。
- `POST /api/access/auth/user-menu` → `R<UserMenuData{menus, roles, permissions}>`（拉取时机（T-FE-048 起）：登录（loginByUsername 直调）、会话恢复（initRouter 拉取分支，F5/启动）、403 自动刷新、顶栏手动入口、授予页重试（retryLoadDeps）、menu-retry 重试（reloadSessionMenus，T-FE-056 起）——登录外各路均收敛到「会话权限热刷新」节的能力刷新入口，登录路径复用 loginByUsername 已拉取结果不重复请求；HTTP 401 会话失效不降级 rethrow，其余异常仅 console.warn 不阻断登录）
- 路径经 Gateway 统一路由（`/api/access/**` 单命名空间，无 StripPrefix——T-ACCESS-042）；开发环境由 vite proxy 单条 `/api` 同路径转发（`VITE_PROXY_TARGET`）

## 组件结构（含可复用组件识别）

- `views/login/index.vue`：页面 + 验证码状态（captchaId/captchaImage/refreshCaptcha）
- `views/login/utils/rule.ts`：表单规则（含 captchaCode required）
- `views/login/utils/messages.ts`：`resolveLoginMessages` 登录成功提示语义唯一出口（T-FE-049 两态；forceResetPwd 提示分支已随 T-FE-046 阻断流程删除）
- `views/change-password/index.vue` + `utils/rules.ts`：强制改密页（T-FE-046，见「强制改密闭环」节）
- `api/auth.ts`：`getCaptcha`/`login`/`logout`（后两者 `unwrap` 解包）/`getUserMenu`
- `store/modules/user.ts`：登录保存会话元数据并加载菜单；refreshUserMenu 维护 menuLoadFailed，并以 sessionId 校核响应归属；logOut 创建原会话的注销请求后立即清理本地状态。无刷新令牌链路。
- `utils/session-expired.ts`：会话已过期统一提示（`notifySessionExpiredOnce` 10s 去重窗口）与 `SessionExpiredError` 信号（initRouter 会话终结分支抛出，T-FE-054；独立小模块：去重状态放 http 会与既有边成环、放 router/utils 属职责错位）
- `router/gate.ts`：路由级 UX 门禁判定纯函数（`isPublicRoute` 公共白名单 / `collectMenuPaths` menus 树 path 集 / `isRouteAllowed` 三源判定，T-FE-056——见「路由级 UX 门禁」节；独立小模块同 session-expired 先例：守卫消费、可单测）

## 权限接线（hasPerms → 按钮 → 降级）

登录页本身无权限门禁（白名单路由）。登录后角色/权限经 `/api/access/auth/user-menu` 写入 store，供路由 `auths` 过滤与页面按钮 `hasPerms` 使用。`forceResetPwd=true` 登录后由路由守卫阻断至 `/change-password`（T-FE-046，见下节）——T-ADMIN-022「非阻断提示引导联系管理员」口径就此退役（自助通道 `/api/access/user/reset-password` 自身路径已定位，T-PERM-067）。

## 会话生命周期与错误处理

- expiresIn 转为绝对时间，`setTenantSession` 保存非认证的 sessionId 与用户元数据。原生令牌仅由服务器签入 HttpOnly Cookie；前端不读 Cookie 认证值，不将其放入 Authorization。
- `isSessionTerminated` 统一判断活动元数据缺失、标记缺失或本地 expires 已到期。请求短路时提示“会话已过期，请重新登录”（10 秒去重），立即 logOut 清理并抛 SessionExpiredError；不发出已知无效的业务请求。登录、验证码与注销进入各自窄分支，注销显式使用请求创建时的原标记。
- 401 只终结对应的当前会话：先检查请求代际，再比较 X-AM-Session 与当前 sessionId；主动退出后迟到 401 不重复弹过期提示，旧会话响应不得注销新身份。403 维持原能力刷新去重规则，503 等错误由页面处理。
- HTTP 409 + code=90002 表示 Cookie 与页面上下文不一致；只在请求仍属于当前页会话时清除对应活动元数据并阻断旧界面，确认重新载入后进入登录页。此分支不调用注销新 Cookie、不重放写请求、不触发权限刷新。

JSON 类型的 Blob 错误响应同样经过公共 HTTP 层的会话处置；异步读取响应体后重新校核请求所属会话，旧响应不得清除或阻断新身份。CSV 导出不另设会话例外（T-FE-074）。mock 登录的菜单查询使用同一 `X-AM-Session` 头传递测试身份（T-FE-075）。

<a id="cross-tab-session-status"></a>
## 跨标签会话同步交付边界

**当前实现（2026-10-17 用户选择 M2/N1，Q-068）**：增强同一浏览器内租户管理台标签页的会话变更检测，处理其他标签页退出、换账号或换租户后旧页面状态滞留的问题；不得让旧页面继续以新共享令牌提交原上下文操作。T-ACCESS-109 将认证承载改为 HttpOnly Cookie，继续保持 M2/N1 的页面清理与身份隔离保证，两种身份域不绑定。

原生认证值由 HttpOnly Cookie 保存，页面只持有 sessionId 和可读元数据。`tenant-session.ts` 固定当前页标记与本地代次，监听 storage/clear、focus、pageshow 和 visibilitychange。请求创建时记录标记，发送及响应回写前复核；服务端再检查该标记与浏览器实际携带的 Cookie 是否绑定。通知延迟不允许旧页面借用新 Cookie 操作。当前标签页登录/退出更新本地基线；跨标签失效只清本页状态，不能注销或删除共享新会话。已发起的退出请求只携原标记，迟到失败仅提示“服务器尚未确认退出”，不终结新会话。检测到会话变化后立即清理旧页面和弹窗，丢弃普通未保存输入，不自动提交；已成功保存的授权草稿保持原账号/租户隔离，不转交新身份。显示阻断提示，由用户点击重新载入：无有效租户会话进入登录页，有新会话进入当前账号首页（既有强制改密/菜单门禁仍适用），不恢复旧编辑页面。Hash 路由重新载入须真正重建页面运行时，不能只更新 hash。可读元数据本地到期时继续走统一会话过期处置；Cookie 失效则由服务端校核拒绝。已经发出的写请求不能据此承诺撤回，结果未知仍按原有核验与授权草稿规则处理；不自动重放请求。任务见 [T-FE-071](../../archive/2026-10-17/tasks/T-FE-071.md)。

<a id="session-permissions"></a>

## 会话权限热刷新（T-FE-048，2026-09-19 拍板）

**问题**：管理员给用户 A 新增 `USER:UPDATE` 后 A 的按钮不出现、撤权后按钮残留点击 403，只能 F5/重登——`refreshUserMenu()` 原全仓仅 loginByUsername 与 initRouter 两处调用，且**仅刷 store 不重建侧栏**（permissionStore.wholeMenus 唯一重建点在 initRouter），「按钮权限已新、侧栏旧菜单残留继续点击 403」。**机制必须落在被授权人会话侧**（管理员授权发生在管理员自己的浏览器，页面内刷新帮不了被授权人），403 触发正是「被授权人下一个动作即自愈」的形态（弃轮询/推送——前者延迟=N 分钟且常驻请求量、后者需后端新事件机制属长期项）。

**统一能力刷新入口**（`src/router/utils.ts refreshSessionCapability`，单函数原子更新）：权限串（roles/permissions/menus 经 user store `refreshUserMenu`）+ 侧栏 wholeMenus（复用 initRouter 的 `buildSidebarMenus`/`handleBackendMenus` 接线；menus 空时按 `menuLoadFailed` 渲染两态占位）。调用方共用：initRouter、403 自动刷新、顶栏手动入口、授予页 retryLoadDeps、menu-retry 重试编排（T-FE-056 起，经 reloadSessionMenus）——复评 P3-3 同批补记第五方。语义：

- 成功：侧栏即时重建（撤销的菜单项从侧栏消失；全撤销落「当前账号无可用菜单」占位）；
- 失败：原样抛出且不触碰 wholeMenus——按钮/侧栏维持旧态（后端 fail-closed 兜底）；门禁状态机维持 loaded（failed 仅由首次加载失败产生）——T-FE-056 起门禁消费面落地：path 集为 menus 的**响应式派生**（每次导航现算，本入口回写 menus 即门禁随会话权限即时收敛，无快照失联——T-FE-055 capability 派生化同款定案）、状态机迁移在本入口所经的 `refreshUserMenu`（见下节）；
- **single-flight（Q-016 收口，T-FE-054）**：入口内共享在途 Promise——同会话（sessionId 标记相同）并发调用只发一次 user-menu 请求，403 自动/手动/授予页重试多通道并发不再各发各的（旧响应晚到覆盖新权限串的同会话竞态随之消除）；完成后在途标记复位，下次调用重新发起；
- **跨会话代际守卫**：成功结果只在 `sessionAlive`（当前令牌存在且仍等于请求指纹）时回写 Pinia、localStorage userKey 与侧栏；令牌已消失时也丢弃成功结果，防止复活旧会话。失败只在 `sessionReplaced`（另一活会话）时丢弃，会话终结型 401 仍上抛，不能让手动刷新假成功。正常 `setUserKey` 恒带 expires，残缺 userKey 的回写入口已封闭，暂不为不可达的 NaN 形态增加防御分支；新增持久化写者时复核。[来源](../../archive/2026-09-26/decision-registry-history.md)（原第 33 行）。
- 不清理 multiTags 已缓存标签（标签指向的路由由后端 403 兜底）；不经 `handleAsyncRoutes`（其 multiTags 重置仅属登录/F5 的 initRouter 全量路径）。

**403 自动刷新**（`src/utils/http/index.ts` 响应拦截器 403 分支）：窗口去重触发能力刷新——**10s 去重窗口**（2026-09-20 用户拍板；起算于触发时刻，窗口内在途双保险：短时间内多次 403 只刷一次），失败静默维持旧态（仅 console.warn，无 message 弹窗——与手动入口的显式反馈口径区分），**不自动重放原请求**（防循环，用户重新点击即可），排除 user-menu 自身（刷新入口即该请求，其 403 下重发无自愈可能）。403 ≠ 必然权限变更（可能是配错/越权访问）——刷新无害（多一次 user-menu 请求），按钮显隐以最新事实为准。错误本身仍原样 reject 由页面层处理展示。去重窗口跨登出/登录不清空：接受新会话首次 403 自愈最多延后 10s，避免 HTTP 层反向挂载登出钩子；手动刷新不受此窗口限制。会话/HTTP 耦合方式变化时复核。[来源](../../archive/2026-09-26/decision-registry-before.md)（原第 127 行）。

**顶栏手动入口**（lay-navbar 工具区「刷新权限」图标按钮，经 useNav `refreshPermission`）：直连能力刷新入口（**不走 403 去重通道**——显式动作立即响应，亦不受 10s 窗口限制）；模块级在途 ref（T-FE-054 起「发请求去重」已由入口内 single-flight 承担，useNav 标记职责收窄为防重复进入/重复 message 的 UI 层防抖）；成功 message「权限已刷新」且按钮显隐/侧栏菜单即时更新；失败弹错误 message「权限刷新失败，请稍后重试」（2026-09-20 用户拍板：显式动作配显式反馈）。**已知布局边界（2026-09-20 外评登记，修法定向抽公共组件、暂不实施）**：按钮现仅挂 vertical 布局工具区——mix/horizontal 布局的顶栏工具区由 `NavMix.vue`/`NavHorizontal.vue` 两份手写复制体渲染、暂无此入口；该两布局下靠 403 自动通道（与布局无关）+ F5 兜底，未来收敛为三处共用小组件时消除。

**回归锁**（`src/utils/http/index.spec.ts` + `src/views/perm/grant/utils/hook.spec.ts`）：①403 触发能力刷新恰好一次（窗口内第二次 403 不再触发）；②403 自动刷新成功后侧栏同步重建（撤销项消失/全撤销占位）——两条旧实现（无触发/仅 initRouter 重建）下必红；③retryLoadDeps 先刷权限串（刷新使权限翻真后重试才发依赖请求）——旧实现下必红；附加锁：窗口过期可再触发 / user-menu 自身 403 不触发 / 刷新失败静默维持旧态 / 401 分支不受扰 / 刷新失败不阻断授予页重试。

<a id="route-gate"></a>

## 路由级 UX 门禁（T-FE-056，2026-09-19 拍板③；拦截落点 2026-09-20 用户拍板全屏 /access-denied）

**口径演进**：T-PERM-037（2026-08-31）当时维持「菜单可见、路由可达、后端 403 兜底」的理由是「meta.auths 为前端静态声明可绕过，与后端派生方案重复」——T-FE-015 后菜单已是后端按权限派生（menus 树），以 menus 派生路由门禁不再有静态可绕过问题，本机制落地后「路由可达、后端 403 兜底」口径退役为「menus 门禁拦 403 + 后端 403 双层兜底」。

模板 `meta.roles` 白名单不启用，菜单权限由后端 menus 派生；`filterNoPermissionTree` 尚有结构处理副作用，不能仅因没有 roles 声明就删除整条调用链。强制改密完成后撤销旧会话并返回租户登录页；重新登录后由菜单决定落点。[来源](../../archive/2026-09-26/decision-registry-history.md)（原第 32、34、35 行）。

**门禁集合**（`src/router/gate.ts`，三源之并）：

- **menus 树 path 集**（含 children 递归）——与侧栏可见性同源不分叉（后端菜单种子 path 与前端路由 path 同形）；每次导航**现算派生**，menus 更新即集合更新（能力刷新入口回写后门禁随会话权限即时收敛，无快照失联形态）；
- **公共路由白名单**：remaining.ts 全部节点 path 自动纳入（递归含 children——`/redirect/:path(.*)` 参数形态转前缀匹配，漏配会拦死标签刷新链路 lay-tag onFresh 的 `router.replace("/redirect"+fullPath)`）+ 公共错误页显式登记（`/error/403|404|500`，error.ts 模块路由不在 remaining.ts——防门禁拦截落点与守卫 VITE_HIDE_HOME 重定向 `/error/404` 自环）；
- **显式动作路由映射**：showLink:false 不进菜单的业务路由按权限串判定（`/perm/grant` → `ROLE:VIEW`，`hasPerms` 单源，与三处入口按钮门禁同源〔T-FE-055〕——纯 menus 白名单会封死授权页）；新增动作路由在 gate.ts 映射表登记。

**守卫状态机**（`router/index.ts` beforeEach 分派 × user store `menuGateStatus`；迁移唯一入口 `refreshUserMenu`）：

- `uninitialized`/`loading`（会话内首次拉取在途）：**等待初始化完成后再判定**（不同步放行）——防冷启动深链绕过（旧实现 initRouter 完成后仅 to.name 为空才重导航，静态路由有名即漏判）；initRouter 内能力刷新 single-flight，并发导航共享同一次拉取；
- `loaded`（至少成功一次；零权限账号 menus 空集亦 loaded，门禁集合=仅公共白名单+显式映射）：按门禁集合判定，集合外路由 `next({path:"/access-denied"})`——**全屏 403 落点**（remaining.ts 公共页，无 Layout 侧栏壳，与 T-FE-046 阻断着陆页同款先例；页面含「返回首页」按钮，零权限账号 menus 不含 /welcome 时该按钮亦被拦，属零权限边缘形态非缺陷）；
- `failed`（**仅由首次加载失败产生**）：fail-open 放行——门禁是 UX 层不是安全层，门禁失效的最坏结果=回到现状（路由全可达+后端 403 兜底），不产生新锁死；已 loaded 会话的后续刷新失败**维持 loaded**（`menuLoadFailed=true` 但 menus 保留旧值，守卫用旧 path 集继续判定——门禁不因刷新抖动静默失效）；`logOut` 重置 `uninitialized`。

**与相邻机制的边界**：公共路由不判门禁不等待（冷启动仍后台 initRouter 建 wholeMenus——阻断人群同样建：改密页虽无侧栏渲染，改密成功跳转 getTopMenu 同步读 wholeMenus，不建则 F5 改密页改密成功解引用 undefined 抛 TypeError，外评 P2 处置 2026-09-20 拍板恢复改前行为）；externalLink 不触门禁（openLink 新开标签，模板原状）；强制改密阻断（T-FE-046）优先于门禁判定；multiTags 残留标签点击被拦（权限回收后的旧标签）落 403 全屏页，标签清理仍属非目标（T-FE-048 边界维持）；冷启动手输未知路径（pathMatch 注册前）被拦 403 而非 404——不可达路由统一按无权限语义拦下，不泄露路由存在性；浏览器回退到已失效菜单路由（授权回收+热刷新收缩后）时，守卫 redirect 经 vue-router popstate 语义向历史栈追加 /access-denied 条目——被拦条目之前的页面无法连续回退到达（出路=403 页「返回首页」或浏览器历史菜单），纯导航体验无数据/安全后果——已知边界（外评 P3，2026-09-20 拍板登记；replace:true 可消除但会使点击进入同样丢来源页 push 语义，不采）。

**回归锁**（`src/router/index.spec.ts` 守卫行为 + `src/router/gate.spec.ts` 纯函数 + `src/store/modules/user.spec.ts` 状态机）：锁① loaded 态无权限导航拦 403（旧实现放行，红跑实证）；锁② 冷启动深链同步不放行、初始化后被拦（旧实现立即放行，红跑实证）；锁③ `/redirect/:path` 白名单前缀放行（漏配实现红）；锁④ `/perm/grant` 显式映射放行/反向无 `ROLE:VIEW` 拦截（纯 menus 白名单实现红）；锁⑤ failed fail-open 放行（安全锁，现状保持）；状态机行为锁（首载 loading→loaded / 首载失败 failed / 刷新失败维持 loaded / 刷新在途不回退 loading / logOut 重置——旧实现无状态机字段红跑实证）。红跑实证（红数与全量绿数以当轮 vitest 报告为准）。

登录提示与令牌在毫秒级失效交错时仍可能短暂不同，维持现有提示行为；令牌终结统一由请求层处理，不给 menu-retry 等单入口另造判空分支，也不把提示边界扩为鉴权豁免。[来源](../../archive/2026-09-26/decision-registry-before.md)（原第 122 行）。

## 登出流程（保留 U2，取消 W1 与 X1/X2）

主动退出及现有会话终结编排继续共用 logOut。持有活动元数据时，先创建 `/api/access/auth/browser-logout` 请求，固定原 sessionId 到 X-AM-Session；随后立即清 Pinia、对应本地活动元数据、标签状态与全局编辑弹窗，重置路由并进入登录页，不等待服务端确认。无活动元数据则只做幂等本地清理，不发请求。

服务端只注销 Cookie 与原标记匹配的会话，不接受标记单独授权。响应不写 Set-Cookie，迟到响应不会清除新 Cookie；请求若已自动携带新账号 Cookie，则按不匹配拒绝，不转而注销新账号。失败向用户提示原会话“服务器尚未确认退出”，允许立即重新登录；不保留补注销队列，不在网络恢复或换账号后补发，旧服务端会话按原过期规则结束。成功注销后的残留 Cookie 值无效，下次登录覆盖或浏览器按期限清理；本地活动元数据已清除时，刷新不从 Cookie 自动恢复旧身份。

原 OAuth2 returnTo 续接规则保持：缺省继承当前页面 query，只接受本地 `/oauth2/authorize` 目标。普通页面无 returnTo 不改变跳转；改密成功、401、本地过期及初始化会话终结共用该规则。请求短路、initRouter、menu-retry 继续通过 terminateLocalSession 提示过期、调用 logOut 并抛 SessionExpiredError，主动退出不借终结异常控制流。

## 强制改密闭环

租户登录返回 `forceResetPwd=true` 时，前端导航到全屏 `/change-password`；后端同时限制该会话只可访问必要的 userinfo/user-menu/logout 和自身 reset-password 入口，不允许直接调用业务 API 或签发 OAuth2 委托。用户设置 8–32 位、含字母和数字且与当前密码不同的新密码后，后端清除标记并撤销旧会话，前端立即清本地租户会话并返回登录页；若从 OAuth2 授权流程进入，沿用仅本地 `/oauth2/authorize` 的白名单校验保留 returnTo，重新登录后回到授权确认页。普通自助改密行为保持原契约。新密码另受 UTF-8 字节上限约束：不超过 72 字节（约 24 个汉字，T-PERM-110；超限后端拒 10013，改密页 placeholder 同步提示）——BCrypt 只参与前 72 字节，全角字符过多会使尾部差异静默失效。

强制标记存于登录主体对应的 localStorage 元数据；每次登录重写，登出清除，不再通过只清标记来继续使用旧令牌。缺少 userId 时改密页提示重新登录，不猜测主体。租户自助改密接口仍不收旧密码字段；独立平台改密接口则校验 oldPassword。

`/api/access/user/reset-password` 保持 Gateway 会话入口族豁免，其最终安全边界由后端会话认证、租户即时门禁、强制改密自助限制及非自身实例级权限校验共同承担。

## 独立平台运营入口

平台 `/platform/login`、`/platform/change-password` 与 `/platform/*` 运营布局在同一前端工程中独立注册，不进入租户布局的路由压平流程。平台账号、租户和平台审计页面只调用独立平台 API，平台没有客户用户、组织和授权管理入口。

<a id="platform-cross-tab-session"></a>
**平台跨标签同步（2026-10-17 用户选择 O1，Q-082 已收敛）**：通过 `session-boundary.ts` 共用工厂与 `tenant-session.ts` / `platform-session.ts` 两个独立实例实现，两域独立记录当前页身份与失效状态，互不注销、互不删除凭据。平台身份变化时清理平台旧页面、普通未保存输入和一次性密码弹窗；未复制的一次性密码不另存草稿，后续需经重置流程恢复交付，已创建的账号不删除。显示阻断提示，用户确认后完整重新载入；无有效平台会话进入平台登录页，有会话进入平台租户列表，既有强制改密守卫仍适用。请求身份固定及迟到响应保护与租户同源；T-ACCESS-109 同步改用独立平台 HttpOnly Cookie。任务见 [T-FE-072](../../archive/2026-10-17/tasks/T-FE-072.md)。

平台可读元数据使用 `platform-browser-session-v3` 与 `authRealm=platform` 分支；租户使用 `tenant-browser-session-v3`，`tenant-tabs-v3` 仅为浏览器标签生命周期标记，均不保存原生令牌。平台 401/退出只清平台身份，不刷新租户菜单或注销租户；两个身份可以同时存在。升级按 V1 删除旧版认证存储并分别重新登录。Redis 重启后的租户进程代次门禁和平台数据库凭据代次规则保持。

开通或凭据重置的明文只在当前弹窗内一次展示，不持久化到浏览器；关闭弹窗即清除。响应中断时提示先查已创建对象，再用受审计重置入口恢复交付。租户列表分别展示登记状态和访问状态；门禁未就绪不表述为已恢复。

## mock 与动态路由口径（T-FE-041 决策）

侧栏菜单的斜杠图标键走离线注册，`offlineIcon.ts` 覆盖 `BootstrapGraphDefinition` 菜单种子集合。`ep/coins`、`ep/history` 保留既有键，分别注册 Element Plus 的 `coin`、`clock` 图形；后端种子与静态路由键不变（T-FE-062，2026-10-04 确认）。

- `mock/login.ts` 由 `VITE_MOCK_LOGIN`（.env.development，默认 **false**）控制注册；开启时注册 `/api/access/auth/captcha`（SVG 占位图）+ `/api/access/auth/browser-login` + `/api/access/auth/user-menu`，响应壳已对齐 R，前端代码零分支（开关经 wrapperEnv 写回 `process.env` 生效，已端到端验证：后端未启动时三端点全走 mock；关闭时请求穿透 vite 代理）。生产构建 mock 由 `VITE_ENABLE_PROD_MOCK=false` 关闭。mock user-menu 自 T-FE-015 起下发最小菜单树（welcome 纯展示，对齐 bootstrap 种子形态）——侧栏唯一数据源已切本接口 menus 树，空数组（拉取成功形态）渲染为「当前账号无可用菜单」占位（T-FE-049 两态——mock 场景不触发失败态）。
- 纯静态路由：`initRouter` 不再请求 `/get-async-routes`（`src/api/routes.ts` 已删除），路由注册由 `router/modules/*.ts` 静态维护；**侧栏菜单已切后端派生（T-FE-015 已接线 2026-08-31）**——`initRouter` 将 `/api/access/auth/user-menu` 的 menus 树直接渲染为侧栏（标题/图标/层级来自 sys_menu bootstrap 种子，可见性 = v3.5 §4.1 ∃op 派生），`meta.showLink` 不再控制侧栏；会话恢复 = 已登录 F5/启动重取 user-menu，失败 fail-closed 空菜单，不持久化、不回退全量静态菜单。**会话已终结分支（Q-020 收口，T-FE-054；外评 P2 收口后判据单源）**：`initRouter` 开头经 `isSessionTerminated()`（无活动元数据或本地过期，与请求拦截器短路同源）判会话已终结时统一提示「会话已过期」+ `logOut` 跳登录 + 抛 `SessionExpiredError`（调用方 catch 后跳过按陈旧菜单状态的业务提示）——/menu-retry 重试不再零请求误报「仍无可用菜单，请联系管理员」（包括活动元数据不完整形态）；本地过期后的 F5 会话恢复同样命中本分支（守卫 `initRouter().then` 已补 catch 留痕）；登录路径刚 setToken 恒不触达。**空侧栏占位项两态（T-FE-049，`resolveSidebarFallback` 按 `menuLoadFailed` 区分）**：拉取失败=「菜单加载失败，点击重试」（既有，跳 `/menu-retry` 重试页）；拉取成功但账号无菜单=「当前账号无可用菜单」——重试对该形态无意义，着陆页（同为 `/menu-retry`，页内自适应）引导联系管理员、保留「重新检查」入口（管理员补配后点击即恢复，无需重登）。

OAuth2 授权续接 query 为 returnTo，仅允许本地 /oauth2/authorize 路径。平台强制改密门禁仍优先，改密成功后再返回确认页；菜单加载失败不把未确认的授权请求自动签码。returnTo 的续接规则单源在 `logOut`（T-FE-068 拍板 A）：改密成功分支显式传参、401 分支/本地过期短路/initRouter 终结的无参调用缺省继承当前页 query 的 returnTo，均经同一 `oauthReturnTarget` 白名单校验——改密首请求响应丢失、重试收 401 等形态重登后不丢原授权确认地址。授权确认页对 query 变化（如同一页面切换 client_id）立即作废旧预览与旧请求并按新 query 重新核验：签码只认当前已核验请求（`views/oauth-consent/model.ts` 生命周期模型，迟到响应按代际丢弃——T-PERM-110 同页切换不再给旧应用签 code）。

登录响应中的 userId/tenantId 随本地会话元数据保存，供授权草稿隔离。登录采用 tenantCode，草稿仍以服务端解析出的内部 tenantId 与 userId 分区；缺 tenantId 的旧会话不能构造草稿归属，不回落租户 1。


## HttpOnly 承载与升级

按[浏览器会话设计](../browser-session-authentication.md)与[网页会话契约](../access-service-api-contract.md#browser-session-contract)实施。新网页登录不接收明文原生令牌，原 Bearer 登录供工具保留。V1 要求加载新版后分别重新登录，不迁移旧会话。U2 保留立即重新登录，W1 与 X1/X2 已由用户取消；本任务不建设离线补偿或独立注销凭据。

<a id="legacy-sso-template-status"></a>
## 未启用 SSO 模板处置

未挂载的 URL username/roles/accessToken 登录示例已随 T-ACCESS-109 删除；不恢复脚本写入原生令牌的旧函数，不影响现有登录或标准 OAuth2 授权。T-ACCESS-119 按 2026-10-18 确认范围删除未使用的 isRemembered/loginDay 状态及设置动作；multipleTabsKey 仍作为路由守卫消费的会话标记，使用浏览器会话 Cookie，不提供“记住我”期限选项。认证凭据仍由 HttpOnly Cookie 承载，不能由此标记取得登录身份。

### 保留的 iframe 模板刷新

iframe 缓存模板仍未由现役动态路由启用。保留模板的刷新动作通过 URL.searchParams 设置 t 查询参数，保留已有查询参数及 hash 路由；重复刷新替换 t，不累积参数。此修正不新增动态路由或嵌入页面入口（T-ACCESS-119）。
