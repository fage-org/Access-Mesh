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
last_updated: 2026-09-19
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
| [T-FE-046](../tasks/T-FE-046.md) | 强制改密闭环——自助改密页 + forceResetPwd 阻断 | ⚙️ proposed |
| T-FE-047 | 用户删除二次确认与错误反馈 | ✅ done |
| [T-FE-048](../tasks/T-FE-048.md) | 会话权限热刷新——403 触发自动刷新 + 手动入口 | ⚙️ proposed |
| T-FE-049 | 登录半成功语义修正（诚实提示） | ⚙️ proposed |

### P2 批（一致性清理）

| ID | 标题 | 状态 |
|---|---|---|
| [T-FE-050](../tasks/T-FE-050.md) | `findParentOrgName` 递归 bug 修复（仅 user/index.vue；PositionTab 系正确实现非同款 bug） | ✅ done |
| [T-FE-051](../tasks/T-FE-051.md) | 列表加载统一错误处理 + 请求代际（composable 化） | ✅ done |
| T-FE-052 | 跨层级拖拽 + 角色禁用加二次确认（parentId 变化拖拽确认；同级排序直接生效） | ✅ done |
| T-FE-053 | 路由守卫 `next()` 后补 return（卫生修） | ⚙️ proposed |
| T-FE-054 | token 过期拦截器短路 + 过期提示 | ⚙️ proposed |
| T-FE-055 | 授予入口按钮按 MANAGE/VIEW 分文案 | ⚙️ proposed |
| [T-FE-056](../tasks/T-FE-056.md) | menus 派生路由级 UX 门禁 | ⚙️ proposed |

关键依赖：T-FE-046 depends_on T-FE-049（先定登录提示语义再做阻断分支，避免 message 位置返工）与 T-GW-009（白名单前置）；T-FE-056 depends_on T-FE-053（同文件路由守卫，先机械修 return 再加门禁逻辑）。同文件协调（建议串行）：router/index.ts beforeEach 由 046（阻断）/048（门禁 path 集更新方）/056（状态机）三卡触达，建议顺序 053→048（统一能力刷新入口）→056（状态机挂接）→046（阻断）；http 拦截器由 048（403 响应分支）/054（请求 expired 分支）触达；user/index.vue 由 047/050/051 三卡触达，建议串行提交避免冲突。

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
