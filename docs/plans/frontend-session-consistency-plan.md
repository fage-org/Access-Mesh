---
doc_type: plan
title: 前端会话生命周期与交互一致性修复（2026-09 外评处置）
status: proposed
domain: frontend
design_refs:
  - docs/design/frontend/login.md
  - docs/design/frontend/permission-grant.md
  - docs/design/frontend/role-manage.md
  - docs/design/frontend/biz-domain.md
  - docs/design/frontend/operation-log.md
  - docs/design/frontend/type-definition.md
  - docs/design/frontend/system-config.md
tasks:
  - T-FE-045
  - T-FE-046
  - T-FE-047
  - T-FE-048
  - T-FE-049
  - T-FE-050
  - T-FE-051
  - T-FE-052
  - T-FE-053
  - T-FE-054
  - T-FE-055
  - T-FE-056
  - T-GW-009
acceptance: "P1 批（T-FE-045~049）全 done：登出真注销、强制改密阻断闭环、用户删除确认与错误反馈、会话权限热刷新、登录半成功语义修正；P2 批（T-FE-050~056）全 done：父组织名递归 bug、列表错误处理与请求代际、角色禁用确认、守卫 return-next 卫生、token 过期短路、授予入口文案语义、menus 派生路由门禁；新增行为全部带「旧实现下失败」回归锁（既有行为确认为特征/安全锁，见 T-FE-051/056 锁分类口径）；全量回归（含容器组与 E2E）绿"
last_updated: 2026-09-20
---

## 目标

处置 2026-09-19 外部 AI 评审（基线 commit `5495d2ffd`，距今 6 提交且前端零改动，结论全部针对现码）对前端会话生命周期与产品交互的 14 项结论。主代理逐条代码级核实：**12 项成立、3 项降级**（守卫双 `next` 当前触发面为零、token 过期放行系 T-FE-041 明文口径、路由可达性系 T-PERM-037 定案口径）；评审**半误报 1**（「拖拽改权限继承」运行时方向错误，但管理面实例委托影响真实）；评审**遗漏 1**（T-PERM-067/066 已备自助改密闭环前提）。主代理自身误报 1 处经 codex sol 外评指出后撤回（PositionTab 的 findParentOrgName 系返回 null 的正确实现，非同款 bug）。

四项用户拍板（2026-09-19，AskUserQuestion 四问；第四项经 codex sol 外评修正事实基础后改判，定案见 decision-registry 同日行）：

1. **强制改密 = 自助改密页 + 阻断**：`forceResetPwd=true` 登录后只放行改密页，改密成功（后端自身置 false）后进系统；阻断标记跨标签共享持久化（登录主体绑定）。
2. **会话权限热刷新 = 403 触发自动刷新一次 + 顶栏手动入口**：统一「会话能力刷新入口」（权限串+侧栏+门禁 path 集原子更新）；不自动重放请求防循环；授予页重试先刷权限串。
3. **路由级 UX 门禁 = 后端 menus 派生 + 显式动作路由映射**：门禁集合 = menus 树 path ∪ 非菜单业务路由显式映射（/perm/grant）；守卫状态机，仅明确 failed 放行 fail-open（T-PERM-037 口径演进）。
4. **高影响操作确认 = 跨层级拖拽 + 角色禁用都加确认**（初判「仅禁用」，codex sol 外评证伪「实例级委托当前无人用」（数据状态≠机制约束，授权页现役可配 INSTANCE 委托）后用户改判）；同级排序拖拽直接生效。

## 非目标

- 服务端权限版本推送（SSE/WebSocket/snapshotVersion 事件）——评审建议长期项，本批不做。
- 外评提议的「20~30 个真实用户场景 E2E 审计清单」建设——另行立项，不在本计划。
- 前端设计文档全面翻新——仅回写本批触达面（login.md / permission-grant.md / role-manage.md / 各页「路由可达性」注记）。
- 登出踢全部端点（logoutAll）、多设备会话管理——超出「当前会话真注销」范围。
- 改密旧密码校验——后端 T-PERM-066 明确另立任务，前端 UI 首期不设旧密码字段。

## 准入条件

- 服务层能力全部就绪（已核实）：`POST /api/access/auth/logout` 存在（AdminAuthController，StpUtil.logout）；`POST /api/access/user/reset-password` 自身路径豁免定位自助改密通道（T-PERM-067，契约 §7.7），自身改密成功置 `forceResetPwd=false`（T-PERM-066 定案）。
- **Gateway 层就绪依赖 T-GW-009**：`/api/access/user/reset-password` 不在 Gateway 白名单内（application.yml 会话入口族精确清单），快照判定对无 API:ACCESS 的普通用户 403——阻断（T-FE-046）落地前必须先完成白名单纳入（用户拍板 2026-09-19，定案⑤）。
- 前端真实链路已切换（T-FE-041 起），无 mock 依赖阻塞。
- 看板无在办前端任务，T-FE 编号 045~056 本批分配。

## 任务清单（引用 [tasks/README 看板](../tasks/README.md)，此处只列 ID+标题+状态快照）

### P1 批（阻断真实试用）

| ID | 标题 | 状态 |
|---|---|---|
| [T-FE-045](../tasks/T-FE-045.md) | 登出真注销——前端接 `/api/access/auth/logout` | ✅ done |
| [T-FE-046](../tasks/T-FE-046.md) | 强制改密闭环——自助改密页 + forceResetPwd 阻断 | ✅ done |
| T-FE-047 | 用户删除二次确认与错误反馈 | ✅ done |
| [T-FE-048](../tasks/T-FE-048.md) | 会话权限热刷新——403 触发自动刷新 + 手动入口 | ✅ done |
| T-FE-049 | 登录半成功语义修正（诚实提示） | ✅ done |

### P2 批（一致性清理）

| ID | 标题 | 状态 |
|---|---|---|
| [T-FE-050](../tasks/T-FE-050.md) | `findParentOrgName` 递归 bug 修复（仅 user/index.vue；PositionTab 系正确实现非同款 bug） | ✅ done |
| [T-FE-051](../tasks/T-FE-051.md) | 列表加载统一错误处理 + 请求代际（composable 化） | ✅ done |
| T-FE-052 | 跨层级拖拽 + 角色禁用加二次确认（parentId 变化拖拽确认；同级排序直接生效） | ✅ done |
| T-FE-053 | 路由守卫 `next()` 后补 return + 删 meta.roles 死分支（卫生修，2026-09-19 拍板扩范围） | ✅ done |
| T-FE-054 | token 过期拦截器短路 + 过期提示 | ✅ done |
| T-FE-055 | 授予入口按钮按 MANAGE/VIEW 分文案 | ✅ done |
| [T-FE-056](../tasks/T-FE-056.md) | menus 派生路由级 UX 门禁 | ✅ done |

关键依赖：T-FE-046 depends_on T-FE-049（先定登录提示语义再做阻断分支，避免 message 位置返工）与 T-GW-009（白名单前置）；T-FE-056 depends_on T-FE-053（同文件路由守卫，053 已完成删 meta.roles 死分支+全 next 站点补 return+守卫 spec 架子，本任务再加门禁逻辑）。同文件协调（建议串行）：router/index.ts beforeEach 由 046（阻断）/048（门禁 path 集更新方）/056（状态机）三卡触达，建议顺序 053→048（统一能力刷新入口）→056（状态机挂接）→046（阻断）；http 拦截器由 048（403 响应分支）/054（请求 expired 分支）触达；user/index.vue 由 047/050/051 三卡触达，建议串行提交避免冲突。

前置执行状态：**T-GW-009 ✅ 2026-09-19 收口**——`/api/access/user/reset-password` 纳入 Gateway 白名单（application.yml 会话入口族精确清单 + GatewayProperties 默认值同步）+ **access-service `SecurityWebMvcConfig` 密钥豁免清单同源同步**（双轨评审代码轨 P0：漏同步时 Gateway 对白名单路径仍无条件注入内部密钥且不注入租户/用户头，`RequestContextInterceptor` 内部凭证分支先于会话分支命中纯服务子分支 → 400「缺 X-Tenant-Id」遮蔽、端点到不了服务层——白名单只落半边任务目标不成立）。回归锁两处均旧实现实证红：GatewayApplicationConfigTest 配置锁（撤条目复跑断言失败）/ SecurityMatrixIT 链路锁（持密无租户头 400≠401，撤豁免复跑失败）。gateway 模块 106 项 + SecurityMatrixIT 13 项全绿。gateway.md 两处清单同步为 T-ACCESS-042 精确形态（顺带修陈旧整族简写三处）+ 契约 §7.7「Gateway 放行」注记与 §6.4 白名单族补记。遗留：①dev Nacos 未启动，远端 gateway.yml 是否覆盖 `gateway.whitelist.paths` 未核（栈启动后经 Nacos 控制台/配置 API 复核——若远端定义该键须同步补条目）；②存量清扫面登记 Q-015（architecture.md/access-service-architecture.md 整族句，claude 外评后扩至含 oauth2 半句与 SignatureEnrichFilter 注释）。T-FE-046 依赖解锁。claude 外评处置完毕（2026-09-19：P0-P2=0、P3×2 全采纳——javadoc 去枚举化直修 + 固定图 API 行用户拍板「保行+标注失效」 + 锁补强×2 + Q-015 扩面；SecurityMatrixIT 14/14 + GatewayApplicationConfigTest 7/7 复跑绿；定案与明细见 registry 同日处置行）。

## 归档条件

- 全部任务 done/cancelled；
- 四项定案对应行为全部落地且带回归锁（旧实现下失败）；
- 全量回归 `mvn test -T 1C`（E2E 必跑）绿 + 前端 vitest 全绿；
- 定案与口径变更已登记 decision-registry、设计回写完成（login.md / permission-grant.md / role-manage.md / system-config.md / 各页路由可达性注记与权限表效应格）。

## 当前进度

- 2026-09-19 立项：外评 15 项逐条核实完成、四项拍板、12 任务两批登记。
- 2026-09-19 codex sol 外评（P1×1+P2×3+P3×3，逐条代码级核实：P1 全成立、P2×2 成立、P2×1 部分成立触发定案④改判、P3×2 成立含主代理自误报撤回、P3×1 部分采纳）处置完成：T-FE-046/048/051/056 卡改写、T-FE-052 范围改跨层级拖拽+禁用确认、registry/看板/本计划同步；任务均未开工（proposed）。
- 2026-09-19 claude 外评（P1×1+P2×2+P3×4，逐条代码级核实：P1 成立=Gateway 白名单缺 reset-password 会锁死阻断人群→定案⑤拍板 Gateway 白名单纳入+T-GW-009 立项；P2×2 成立=/redirect 白名单漏项、048/056 刷新失败状态语义互斥→状态迁移表钉死；P3×4 成立或部分成立=045 过期路径注销无头、051 现状描述失实（十页均已有 catch）、056 清扫面漏 system-config 与效应格、任务卡过程叙事残留→全部处置；外评原文计数勘误 15→14 项）处置完成：五卡修正+registry/看板/本计划同步。
- 2026-09-19 T-GW-009 收口（白名单+密钥豁免同源同步+双轨评审 P0 处置+两处回归锁旧实现实证红，详见「关键依赖」节前置执行状态）；T-FE-046 前置就绪，P1/P2 前端任务均未开工。
- 2026-09-19 T-FE-045 收口（P1 批首个前端任务）：`api/auth.ts logout(authorization)` 显式携 Authorization 头（api 层纯透传）+ http 请求白名单纳入 logout（防过期分支 logOut 递归与注销空转）+ `logOut()` 服务端注销优先/本地清理无条件/串行防抖（进行中短路、完成后零请求）；回归锁×7 红跑实证（旧实现下全红、既有 7 用例全绿），login.md「登出流程」节回写、T-FE-041「前端登出不调接口」口径退役注记。claude 外评处置完毕（2026-09-19：P0-P1=0、P2×1 成立→用户拍板维持现状不修（accessToken 入 localStorage 扩大令牌存续面反噬）、P3×1 成立→登记 Q-016 不修；存量观察×5 维持现状；定案与明细见 registry 同日处置行）。
- 2026-09-19 T-FE-047 收口（P1 批第二个）：`user/utils/hook.ts handleDelete` 照角色页 hook 先例加两段 try/catch——ElMessageBox.confirm（取消零请求）+ 删除失败 `message(error.message || "删除失败")`（后端业务错如 CANNOT_DELETE_SELF 经 RequestError.message 透出）；确认文案含真实后果（无法登录+组织/岗位关联一并移除，对齐后端级联语义）；MemberTab 两入口经 hook 自动全覆盖零组件改动。与 T-FE-051 分工：loadTable/handleCreate/handleUpdate 的 catch+message 收口归 051。回归锁×3 红跑实证（stash 源码 3 用例全红）；vitest 242/242 + typecheck/lint/build 全绿。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P2=0「未发现生产级缺陷」、P3×1 spec mock 泄漏直接修（beforeEach 逐 mock 重设默认实现）、存量×2→登记 Q-017（index.vue 死解构+双请求，关联 050/051）+ T-FE-051 扩行（启停 catch 透后端 message）；定案与明细见 registry 同日处置行）。
- 2026-09-19 T-FE-050 收口（P2 批首个）：user/index.vue `findParentOrgName` 递归修复——「未知」truthy 哨兵混入递归返回值，首个子树查找失败即被当成功值上抛、后续兄弟子树永不遍历（选中后位根子树组织时信息卡「上级部门：未知」，openOrgForm 编辑态同源）；未找到改返 null（PositionTab 同款纯查找形态），提取 utils/orgTree.ts（SFC 内函数不可测），两调用点 `?? "未知"` 保展示语义。红跑 4 红 1 绿实证（旧算法逐字迁移作等价被测体）；vitest 247/247 + typecheck/lint/build 全绿。Q-017 收敛载体拍板 T-FE-051、本卡不顺带（定案见 registry 同日行）。双轨评审处置完毕（文档轨 P0=registry 表格断裂直接修；代码轨 P3×3=规范 §2 措辞对齐工具链（`no-import-type-side-effects` 实证，用户拍板直接修）、Q-018 登记 update 空名、T-FE-051 卡回填 Q-017 锚点；定案见 registry 同日行）。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P3 全零；存量×2→Q-018 加关联注记+Q-019 登记；定案见 registry 同日处置行）。
- 2026-09-19 T-FE-051 收口（P2 批第二个）：`src/utils/list-load.ts` 两层 composable（useListLoad=latest-wins 代际+失败提示保留旧数据+error 标记+onLoaded 副作用仅最新触发；usePagedList=叠 pagination，total 仅最新成功回写）+ `_envelope#toErrorMessage` 统一错误文案（非 2xx 优先后端 body message，替换 axios 默认串；service-interface 局部 getErrorMessage 副本退役）。16 加载面顺扫/收敛：user 试点（loadTable catch+代际、handleCreate/handleUpdate catch、handleToggleStatus 移入 hook 透后端文案——两项 AskUserQuestion 拍板）、八页代际缺口、reqSeq/configReqSeq/treeRequestSeq 三先例收敛（grant matrixToken 矩阵面形态不同不收敛）；失败语义统一保留旧数据（permission-change-log/resource-dependency 两页原清空，行为变更记录在卡）；Q-017 就地化收敛（index.vue 删 useUserManage 实例，点组织单请求）。红跑实证 user 7红/config 2红/pcl 1红（old-fail）+特征锁绿；vitest 279/279 + typecheck/lint/build 全绿。user/index.vue 串行链（047→050→051）全部收口。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P2=0、P3×3 全采纳直接修——Q-017 收敛行死链/测试计数四处统一/onLoaded 回调异常不再冒充加载失败（红跑 1红实证）；存量×5 处置（promise 语义顺带修/两条登记/两条既有登记覆盖）；定案与明细见 registry 同日处置行）。
- 2026-09-19 T-FE-052 收口（P2 批第三个）：定案④落地——role hook 禁用先确认（钉死短语「全部持有者立即失去该角色权限」，启用安全方向不确认）+ 拖拽仅 parentId 变化先确认（文案含原/新上级名与父链镜像后果；取消重拉树恢复，同父排序直接生效）；ReOrgTreePanel 拖拽事件 node-drag-end→node-drop（事实性修正：element-plus 实证被拒落点仍误发 node-move）+ 确认判定提取 confirmMove.ts。回归锁 hook.spec 旧实现 4红/4绿实证 + confirmMove.spec；vitest 290/290（+11）+ typecheck/lint/build 全绿。双轨评审两轨 P0-P2=0、P3×5 全采纳；role-manage.md §4.1 回写。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P2=0、P3×1 编辑表单无确认第二入口两面用户拍板——角色表单状态编辑态只读+组织表单换父提交前确认（confirmMove 扩展移至顶层）；存量×3 维持/注记；vitest 291/291；定案与明细见 registry 同日处置行）。
- 2026-09-19 T-FE-049 收口（P1 批第三个）：登录半成功诚实提示——两项 AskUserQuestion 拍板（非阻断+诚实提示 / 空菜单与加载失败一并区分，定案见 registry 同日行）。`resolveLoginMessages`（views/login/utils/messages.ts，登录成功提示语义唯一出口，T-FE-046 阻断分支扩展点）：菜单已加载=success；半成功（含 initRouter 隐式重试仍失败）=warning 如实告知+指引占位项；拉取成功但账号无菜单（零权限）=warning 引导联系管理员；forceResetPwd 追加保留。store `menuLoadFailed`（refreshUserMenu 成败维护、logOut 重置）作空侧栏两态事实来源；`resolveSidebarFallback` 占位项两态（失败可重试 vs「当前账号无可用菜单」）；/menu-retry 着陆页按状态自适应文案/按钮（重试后「仍失败」error vs「仍无菜单」warning）。红跑 9 红实证：user.spec 3 红+router/utils.spec 4 红（stash 源码）+ messages.spec 2 红（旧语义等价被测体，既有行为 2 用例绿证不变）；vitest 303/303（+12 含 claude 外评补锁）+ typecheck/lint/build 全绿；login.md 交互流程/组件结构/占位项口径回写。双轨评审处置完毕（P0-P1=0：P2×1=registry 定案行补登记；P3×3 直接修——menu-retry 换行 pre-line/login.md mock 节残留句/remaining.ts 注释两态；存疑×2——menu-retry 会话过期重试失真登记 Q-020〔用户口径 token 失效统一处理不单点修，关联 T-FE-054/056〕、病态交错提示维持现状；定案与明细见 registry 同日处置行）。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P1=0、P2×1=零菜单占位项携带未注册 name 致渲染消失/标签点击失效——删 name 对齐 buildSidebarMenus 不变量+spec 补锁；P3×2=mock 注释残留句两态化+meta.title 中性「菜单不可用」；存量×2=占位项两态图标 2 枚顺带注册+侧栏种子 8 枚未注册登记 Q-021、Q-020 已覆盖不另报；定案与明细见 registry 同日处置行）。**T-FE-046 双前置（T-FE-049+T-GW-009）全部就绪，P1 批剩余 046/048。**
- 2026-09-19 T-FE-053 收口（P2 批第四个）：两项 AskUserQuestion 拍板（定案见 registry 同日行）——①修法=②+③组合：删守卫 meta.roles 死分支（触发面为零：全仓零路由声明 meta.roles、VITE_HIDE_HOME=false；本仓路由权限=后端 menus 派生，模板前端白名单永不启用）+全部 next() 站点统一「每 next 必 return」；utils.ts filterNoPermissionTree 维持现状（活跃链路+结构副作用）；②新建 router/index.spec.ts 守卫首次可测化（mock createRouter 捕获守卫回调+8 模块断环，node 环境零 DOM 零新 devDep；架子 T-FE-056 复用）。红跑实证旧实现 1 红 5 绿（红锁「声明 meta.roles 不再拦 403」got 2 times=双 next 本体）；vitest 309/309（+6）+typecheck/lint/build 全 0。双轨评审：代码轨 P0-P3 全零；文档轨 P3×1（056 卡/计划措辞滞后本批回写）；存疑两处事实性最小修正直接修（index.ts 头注时态+spec mock 分歧注释）、两处维持现状。连带 perms.ts 注释清扫；live 文档 6 处 filterNoPermissionTree 表述经核仍准确。claude 外评处置完毕（2026-09-19 模型 deepseek-flash[1m]：P0-P2=0、P3×1=守卫头注「next 恰一次」全称表述失真直修（三元站点无 return/externalLink 0 次 next 例外限定）+存量×4——单注册断言补锁、asyncRoutes/isOneOfArray 导出/403 路由维持；专项 1-5 全过含 vue-router 4 源码级核证；vitest 310/310；定案与明细见 registry 同日处置行）。**P2 批剩余 054/055/056；T-FE-056 前置就绪（守卫基线=无 roles 分支+每 next 必 return+可测架子）。**
- 2026-09-20 T-FE-046 收口（P1 批第四个）：强制改密闭环——登录写 userId/forceResetPwd 入 userKey（登录覆盖清残留）+守卫 8 路径放行清单阻断（redirect /change-password）+`/change-password` 全屏页（8-32 位双字段无旧密码，复用 resetUserPassword 自身路径）+改密成功 `clearForceResetPwdFlag` 清标记会话保留进系统（后端改密不注销会话——代码级核实）+登录页 forceResetPwd warning 分支删除（T-ADMIN-022 口径退役，login.md 新增「强制改密闭环」节）。红跑 6 红实证（5 处对 HEAD 旧实现+redirect 族锁对修前清单回填红）；vitest 324/324（+14）+typecheck/lint/build 全 0。双轨评审处置：代码轨 P2×1=放行清单 /redirect 死项（真实刷新路径参数化精确匹配恒不中+裸路径放给 Layout 壳——删+补锁）+P3×2（user.spec Once 队列被 state 初始化吞噬修正；success toast 观感转拍板）；文档轨 P2×1=契约 §7.7 与后端注释「登录页 warning 据此闭环」滞后顺改+P3×2（「已实证」降格代码级核实；/redirect 同轨）。两项 AskUserQuestion 拍板（定案见 registry 2026-09-20 行）：阻断登录 success toast 维持现状；端到端验收（acceptance 第 5 条，经 Gateway 真实用户全链路）登记计划归档时统一跑（与 T-GW-009 Nacos 遗留同批——归档条件本含全量 E2E）。claude 外评处置完毕（2026-09-20 模型 deepseek-flash[1m]：P0-P2=0、P3×2 全采纳直修——①纯空白密码穿透（required 只判空串+min/max 按长度计，8 空格过后端 isBlank 随机替换且置 false，用户被锁死）rules 加 whitespace 规则+spec 锁删规则红跑实证；②登录落点三处文案失真（MemberTab×2「系统将提示联系管理员修改密码」退役口径改「需先设置新密码」+半成功「进入系统后请点击侧栏占位项」限定——阻断人群着陆页无侧栏）；存量×6 处置（sso 休眠/改密页无登出入口注记入卡/文案直透已登记面/后端不注销会话维持/superseded 册豁免/URL 锁不扩大）；专项四条全过含标记写读全枚举与守卫逐分支不变量；vitest 325/325；定案与明细见 registry 同日处置行）。**P1 批剩余 T-FE-048；router/index.ts 守卫 053→046 两卡触达完毕，048（门禁 path 集更新方）/056（状态机）按建议序接力。**
- 2026-09-20 T-FE-048 收口（P1 批收官：045~049 五卡全 done；P2 批余 T-FE-054/055/056 三卡）：统一能力刷新入口 `refreshSessionCapability`（router/utils.ts：getToken 守卫+refreshUserMenu+rebuildSidebarFromUserMenus；失败不触碰 wholeMenus 维持旧态、不清理 multiTags、不经 handleAsyncRoutes；T-FE-056 门禁 path 集/状态机挂接点预留同 owner）四调用方共用（initRouter 拉取分支/403 自动/顶栏手动/retryLoadDeps）；http 拦截器 403 分支 10s 窗口去重（起算触发时刻+在途双保险，排除 user-menu 自身，失败静默，不重放原请求）；顶栏「刷新权限」直连入口（在途 ref 防双发，成功/失败双 message）；授予页重试先刷权限串+retryInFlight 防重入。红跑 8 红/9 绿实证（三把验收锁全红，特征锁不误伤）；vitest 335/335+typecheck/lint/build 全 0。两项 AskUserQuestion 拍板（10s 窗口/手动失败弹错）+评审 P3×5 处置（两项接受现状、Q-016 扩行登记、重试按钮补 loading 采纳、一项接受），定案见 registry 2026-09-20 行；login.md 新增「会话权限热刷新」节+permission-grant.md §10 重试语义句。**router/index.ts 建议 053→048→056→046 序中 046 已插队完成（T-FE-049 依赖解锁），048 未触达守卫本体（门禁 path 集更新方挂接点在能力刷新入口内预留）；T-FE-056 落地状态机时按 registry 2026-09-19 定案③+T-FE-056 状态迁移表消费该挂接点。**claude+codex 双通道外评处置完毕（2026-09-20：claude deepseek-flash[1m] P0-P2=0/P3×1、codex luna max P2×2，逐条亲核成立——codex P2-1 同会话跨通道并发刷新无 single-flight（Q-016 扩两变体家族）与 P2-2 手动入口 mix/horizontal 布局缺口（修法定向抽公共组件）均用户拍板登记不修、零代码改动；login.md 补布局边界注记；定案见 registry 同日处置行）。
- 2026-09-20 T-FE-054 收口（P2 批第五个；**P2 批剩余 T-FE-055/056 两卡，计划进入收官段**）：token 过期拦截器短路+过期提示——四项 AskUserQuestion 拍板（定案见 registry 同日行）：①**Q-016 收口**=注销 fire-and-forget（T-FE-045「服务端注销优先」从「等注销完成」演进为「发出即清理」；黑洞挂 ≤10s 与新登录凭据竞态消除）+ refreshSessionCapability 入口 single-flight（指纹=accessToken，同会话并发只发一次 user-menu）+ 代际守卫（refreshUserMenu 回写〔Pinia+userKey〕与侧栏重建双拦截，旧会话响应〔成功/失败〕不污染新会话）；②**Q-020 收口**=initRouter 无凭证分支（提示+logOut+抛 SessionExpiredError，menu-retry retry catch 不再按陈旧菜单状态失真提示——判定挂会话恢复统一入口非页面单点判 token，2026-09-19「统一处理」口径落地）；③本地过期短路与 401 **双分支统一提示**「会话已过期，请重新登录」（10s 去重窗口共用；独立小模块 utils/session-expired.ts——放 http 成环、放 router/utils 职责错位）；④页面层照弹（短路 reject Error 同文案经 toErrorMessage 透出，与 401 现状一致）。红跑 10 红/28 绿（修正一轮：fire-and-forget 主锁补红跑态 try/finally 收尾，防 logoutInFlight 残留污染后续用例）；vitest 348/348+typecheck/lint/build 全 0。双轨评审处置（代码轨 P0-P2=0/P3×3+文档轨 P0-P2=0/P3×5，逐条亲核全成立直接修——logoutInFlight 死码注释如实化/代际锁补 userKey setItem 断言/成环论证措辞补全/「与 401 一致」括注限定照弹形态/menu-retry catch 注释限定/spec 计数去计数化/http spec 头注扩段；T-FE-045 历史载体表述按「历史行不改写」协议不动）；守卫 initRouter().then 无 catch 的跨标签理论窗口登记 T-FE-056 顺手处置（详见任务卡遗留节）。Q-016（含 048 两变体扩行）/Q-020 随卡收敛（pending-problems 已收敛表）；login.md 七面回写（令牌生命周期短路口径/401 统一提示/热刷新节 single-flight+代际两条/useNav 分层说明/登出流程 fire-and-forget+退役注记/组件结构 session-expired/mock 节会话终结分支）。claude 外评处置完毕（2026-09-20 模型 deepseek-flash[1m]：P0-P1=0、P2×1+P3×3 逐条亲核全成立直修——P2 判据单源 isSessionTerminated〔「cookie 清+userKey 残留」形态 Q-020 条目点名〕+守卫 then 连带补 catch（原登记 T-FE-056 遗留提前消化）/代际守卫仅「另一活会话」才拦（会话终结型 401 不吞）/短路改 SessionExpiredError+classifySaveError 按 name 识别/401 令牌仍在才提示；红跑 5 红/73 绿；vitest 352/352 全绿；定案见 registry 同日处置行）。codex sol 复评处置完毕（2026-09-20 模型 gpt-5.6-sol xhigh：P0-P1=0、P2×2 直修〔均 claude 处置轮次生缝隙〕——终结后成功响应不回写〔sessionAlive/sessionReplaced 拆分〕+无凭证请求同短路〔拦截器无条件单源判据〕；**过度设计检查可裁剪项=0**〔用户叮嘱，8 机制逐个判定必要且最小〕；NaN fail-closed 建议不采纳〔残缺形态源头已消除，不可达防御〕；红跑 2 红/30 绿+vitest 354/354 全绿；定案见 registry 同日处置行）。
- 2026-09-20 T-FE-055 收口（P2 批第六个；**P2 批剩余 T-FE-056 一张卡，本计划进入最后一张卡**）：授予入口按钮按 MANAGE/VIEW 分文案——三项拍板（registry 同日行，无卡形态）：①三入口（角色页详情卡/组织信息卡/岗位行操作）文案按是否另持 ROLE:MANAGE 显示「权限授予」/「查看权限」（`grant-entry.ts` 单源、判定串与授予页 capability 同源），门禁维持 ROLE:VIEW 放行，canGrant/canGrantPerm 同批换绑 PERMISSION_GRANT_PERMS.ROLE_VIEW（值等价、跨页 ROLE_MANAGE_PERMS import 删除）；②GrantMatrixPanel「授权」按钮 view 态由 v-if 隐藏改禁用态渲染+tooltip「需要 ROLE:MANAGE 权限」（对齐 §10 表「按钮禁用 + tooltip」原文），footer「保存全部」与 MatrixCell「+」维持 v-if 现状；③extension-guide.md:34 refs 外同类文案位随批顺带修。红跑=grant-entry.spec 模块缺失红起+两态锁绿（SFC 接线属 T-FE-050/052 无组件测试既定边界）；vitest 356/356（+2）+typecheck/lint/build 全绿。双轨评审（代码轨 P0-P2=0/P3×1、文档轨 P0-P2=0/P3×4 重叠一项）逐条亲核全属实直接修：两册 last_reviewed 补记、permission-grant.md §2 能力标签理据句（隐藏→禁用）/§1.1 入口表组织行/§13.1 入口行与纯函数行同步；过度设计可裁剪项=0（两轨一致）。设计回写 permission-grant.md（§1.1/§2/§10/§13.1）+ role-manage.md（§1/§4.3）。claude+codex sol 双通道外评处置完毕（2026-09-20，定案见 registry 同日处置行）：codex P2「capability 不随权限热刷新重算」用户拍板根因修——capability 改 canManage 响应式派生（grant-store 删快照字段/setCapability，openGrantDialog/footer/Panel 消费面全改读派生值；未选主体/升权虚假 tooltip/降权可操作三形态全消；红跑 stash 快照实现 2 红→修复 12/12、全量 358/358）；文案位漏扫四处直修（quickstart 按 extension-guide 先例类推）；看板行承载拍板维持惯例；存量注释×2 顺手修；过度设计可裁剪项=0（两通道一致）。claude 复评处置完毕（2026-09-20 复评轮：上轮五项处置逐项核实闭合；P3×3 全处置——红跑锁重写为三态翻转锁〔判别力红跑实证〕/死解构直修/降权弹窗暂态拍板登记边界句 + 存量类推 resource-dependency 门控 computed 化；vitest 357/357；定案见 registry 同日复评处置行）。
- 2026-09-20 T-FE-056 收口（P2 批收官，**13 张卡全部 done——计划任务面完成，剩归档批次**）：menus 派生路由级 UX 门禁——gate.ts 三源判定（menus path 集响应式派生 ∪ remaining.ts 全量白名单〔参数路由 /redirect/:path(.*) 前缀化〕∪ /perm/grant 显式映射 ROLE:VIEW）+user store menuGateStatus 状态机（迁移唯一入口 refreshUserMenu；failed 仅首载失败产生、已 loaded 刷新失败维持 loaded 用旧 path 集、logOut 重置）+守卫等待语义（uninitialized/loading 等 initRouter 完成后判定，防冷启动深链绕过）+拦截落点全屏 /access-denied（用户拍板）。红跑 10 红实证（stash 实现）+vitest 383/383+typecheck/lint/build 全 0；十册「路由可达性」清扫（任务卡 design_refs 补四册+排除清单两册，覆盖判据闭合）+代码注释四处；双轨评审两轨 P0-P2=0、P3×7 全直修（menu-retry 预清锁死形态用户拍板本卡直接修——menu-retry-reload.ts 失败回滚，主锁红跑实证）、过度设计可裁剪项=0；两项拍板与三项实现口径见 registry 2026-09-20 T-FE-056 行。**归档批次待办（此前拍板登记）**：全量回归 mvn test -T 1C（E2E 必跑）+ T-FE-046 端到端验收（经 Gateway 真实用户全链路）+ T-GW-009 Nacos 远端覆盖键复核。
