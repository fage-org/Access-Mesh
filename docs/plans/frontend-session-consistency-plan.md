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
| [T-FE-045](../tasks/T-FE-045.md) | 登出真注销——前端接 `/api/access/auth/logout` | ⚙️ proposed |
| [T-FE-046](../tasks/T-FE-046.md) | 强制改密闭环——自助改密页 + forceResetPwd 阻断 | ⚙️ proposed |
| T-FE-047 | 用户删除二次确认与错误反馈 | ⚙️ proposed |
| [T-FE-048](../tasks/T-FE-048.md) | 会话权限热刷新——403 触发自动刷新 + 手动入口 | ⚙️ proposed |
| T-FE-049 | 登录半成功语义修正（诚实提示） | ⚙️ proposed |

### P2 批（一致性清理）

| ID | 标题 | 状态 |
|---|---|---|
| T-FE-050 | `findParentOrgName` 递归 bug 修复（仅 user/index.vue；PositionTab 系正确实现非同款 bug） | ⚙️ proposed |
| [T-FE-051](../tasks/T-FE-051.md) | 列表加载统一错误处理 + 请求代际（composable 化） | ⚙️ proposed |
| T-FE-052 | 跨层级拖拽 + 角色禁用加二次确认（parentId 变化拖拽确认；同级排序直接生效） | ⚙️ proposed |
| T-FE-053 | 路由守卫 `next()` 后补 return（卫生修） | ⚙️ proposed |
| T-FE-054 | token 过期拦截器短路 + 过期提示 | ⚙️ proposed |
| T-FE-055 | 授予入口按钮按 MANAGE/VIEW 分文案 | ⚙️ proposed |
| [T-FE-056](../tasks/T-FE-056.md) | menus 派生路由级 UX 门禁 | ⚙️ proposed |

关键依赖：T-FE-046 depends_on T-FE-049（先定登录提示语义再做阻断分支，避免 message 位置返工）与 T-GW-009（白名单前置）；T-FE-056 depends_on T-FE-053（同文件路由守卫，先机械修 return 再加门禁逻辑）。同文件协调（建议串行）：router/index.ts beforeEach 由 046（阻断）/048（门禁 path 集更新方）/056（状态机）三卡触达，建议顺序 053→048（统一能力刷新入口）→056（状态机挂接）→046（阻断）；http 拦截器由 048（403 响应分支）/054（请求 expired 分支）触达；user/index.vue 由 047/050/051 三卡触达，建议串行提交避免冲突。

## 归档条件

- 全部任务 done/cancelled；
- 四项定案对应行为全部落地且带回归锁（旧实现下失败）；
- 全量回归 `mvn test -T 1C`（E2E 必跑）绿 + 前端 vitest 全绿；
- 定案与口径变更已登记 decision-registry、设计回写完成（login.md / permission-grant.md / role-manage.md / system-config.md / 各页路由可达性注记与权限表效应格）。

## 当前进度

- 2026-09-19 立项：外评 15 项逐条核实完成、四项拍板、12 任务两批登记。
- 2026-09-19 codex sol 外评（P1×1+P2×3+P3×3，逐条代码级核实：P1 全成立、P2×2 成立、P2×1 部分成立触发定案④改判、P3×2 成立含主代理自误报撤回、P3×1 部分采纳）处置完成：T-FE-046/048/051/056 卡改写、T-FE-052 范围改跨层级拖拽+禁用确认、registry/看板/本计划同步；任务均未开工（proposed）。
- 2026-09-19 claude 外评（P1×1+P2×2+P3×4，逐条代码级核实：P1 成立=Gateway 白名单缺 reset-password 会锁死阻断人群→定案⑤拍板 Gateway 白名单纳入+T-GW-009 立项；P2×2 成立=/redirect 白名单漏项、048/056 刷新失败状态语义互斥→状态迁移表钉死；P3×4 成立或部分成立=045 过期路径注销无头、051 现状描述失实（十页均已有 catch）、056 清扫面漏 system-config 与效应格、任务卡过程叙事残留→全部处置；外评原文计数勘误 15→14 项）处置完成：五卡修正+registry/看板/本计划同步。
