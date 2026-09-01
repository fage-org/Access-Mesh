---
doc_type: plan
title: 前端 Phase 3 — 前后端联调
status: proposed
domain: frontend
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/frontend/permission-grant.md
tasks:
  - T-FE-015
  - T-FE-016
  - T-FE-017
  - T-FE-018
  - T-FE-037
  - T-FE-019
  - T-FE-020
  - T-FE-021
  - T-FE-022
acceptance: "9 个有效联调任务 mock -> 真实接口替换完成（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿；T-FE-018 角色联调依赖 T-FE-036 + T-FE-038 + T-FE-039 + **T-FE-040** + T-PERM-040 + T-PERM-041 + T-PERM-034 + T-PERM-022/028/029/031（2026-08-05 评审：T-FE-038 mock 先行，T-FE-018 汇合单类型矩阵/图标模型/条件转授链路；2026-08-08 补记录级聚焦编辑 T-FE-040），T-ADMIN-021 不阻塞首期；T-FE-037 组织联调二期依赖 T-FE-018 + T-ADMIN-021；统一提交主通道 = apply-grant-plan，无 CAS/幂等表/clientRequestId），核心流程联调通过，异常场景提示正确，页面间跳转/状态保持正确。"
last_updated: 2026-09-01
---

# 前端 Phase 3 — 前后端联调

> 状态：proposed
> 来源：`docs/archive/2026-08-27/improvement-plan.md` §4 Phase 3 拆分（roadmap 已归档，拆分产物即本 plan）
> 准入：Phase 1 收尾 + Phase 2 接口改造完成

## 目标

将 Phase 1 的 mock 页面切换到真实后端接口，端到端联调通过。

## 任务清单

9 个有效联调任务（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿），每个有效任务对应一组已实现页面的 mock->真实接口替换：

| ID | 联调范围 | depends_on |
|---|---|---|
| T-FE-015 ✅ | 组织与用户（已实现 2.1；含 user-menu menus 轨道前端接线——登录链路切真实接口后菜单可见性切 v3.5 §4.1 ∃op 派生，T-PERM-037 收口归入 2026-08-31；接线含会话恢复——F5/启动重取 user-menu、失败 fail-closed 空菜单+可重试、不持久化不回退全量静态菜单；已收口 2026-08-31，终态见任务卡完成记录） | T-PERM-037 |
| T-FE-016 ✅ | 角色管理（2.2；api 层 T-FE-041 已真实路径，本任务收口 Gateway +4 端点注册/编辑 extra 回显 detail 回填 + extraClear 清空协议（3 项设计定案，另含开发库重建）/mock 退役删除/业务门禁零新增（ROLE 全档在图）；已收口 2026-09-01，终态见任务卡完成记录） | T-FE-002, T-PERM-022 |
| T-FE-017 | 资源/操作定义（3.1） | T-FE-008, T-PERM-028 |
| T-FE-018 | 权限授予（4.1）- 角色联调（首期） | T-FE-036, T-FE-038, T-FE-039, **T-FE-040**, T-PERM-040, T-PERM-041, T-PERM-034, T-PERM-022, T-PERM-028, T-PERM-029, T-PERM-031 |
| T-FE-037 | 权限授予（4.1）- 组织联调（二期） | T-FE-018, T-ADMIN-021 |
| T-FE-019 | 权限查询/校验（4.2） | T-FE-013, T-PERM-033 |
| T-FE-020 | 条件/冲突规则（3.2/3.3） | T-FE-009, T-FE-010, T-PERM-029, T-PERM-030 |
| T-FE-021 | 业务域配置（5.1） | T-FE-006, T-PERM-026 |
| T-FE-022 | 系统/服务配置与日志（6.x/5.2/7.x；2026-08-28 扩入 7.x 日志两页——原计划无 7.x 承接） | T-FE-003, T-FE-004, T-FE-007, T-FE-005, T-FE-012, T-PERM-023, T-PERM-024, T-PERM-025, T-PERM-027, T-PERM-032 |

## 联调完成标准

- 每页核心交互流程正常
- 异常场景（权限不足、数据不存在、参数校验失败）提示正确
- 页面间跳转和状态保持正确
- 每页消费端点在 bootstrap Gateway 管理 API 清单注册（未映射路径 fail-closed 403——T-FE-015 联调暴露的系统性缺口，逐页按 T-FE-015 模式扩展 `BootstrapGraphDefinition.apiRoutes()` 并同步 PgIT 计数断言）

## 归档条件

- 9 个有效联调任务全部 done（T-FE-018 角色联调首期 + T-FE-037 组织联调二期，首期/二期拆分定稿）
- menus 接线（T-FE-015）完成后统一补一轮全页导航/F5/直达 URL 冒烟验收（归档门禁式，T-FE-016~022 不串行依赖 015——2026-08-31 设计定案）
- 联调发现的接口问题回写 api-contract.md

## 当前进度

- 2026-06-29：建立本 plan + 拆分 8 个联调任务。全部 proposed，待 Phase 1/2 收尾。
- 2026-07-12：权限授予 UX 设计变更，T-FE-018 重连依赖 T-FE-027；联调以 `design/frontend/permission-grant.md` §16 为准。
- 2026-07-12：T-FE-018 关键路径增加 UX 重构链 T-FE-025 → T-FE-026 → T-FE-027，且仍依赖 T-PERM-034，是 Phase 3 最晚启动项；该延后用于避免旧矩阵联调后再次返工。
- 2026-07-26：权限授予页 v1+v2 整体删除重做，T-FE-018 标 cancelled；设计文档已归档至 `docs/archive/2026-07-26/`，待新页面设计完成后重新立项联调。
- 2026-08-01：v3 设计定稿（`permission-grant.md`）后 **T-FE-018 恢复待排期**（依赖 T-FE-036 + T-PERM-034 完成后启动），本 plan 有效联调任务由 7 个恢复为 8 个；frontmatter acceptance/归档条件同步更新。
- 2026-08-05：**评审方案 B 落地**——T-FE-018 依赖补 T-FE-038 + T-PERM-040（单类型矩阵链路汇合点）；T-FE-038 mock 先行、T-PERM-040 非前置；联调验收补多类型矩阵场景（类型切换/操作列隔离/list 类型过滤/20008 含嵌套反例）。
- 2026-08-05（评审）：**T-FE-018 再补 T-FE-039 + T-PERM-041 依赖**（汇合图标正交模型与条件转授 20041 链路）；联调验收补图标映射真实数据验证与条件+canGrant 场景（前端阻止 + 20041 兜底）。
- 2026-08-31：**T-PERM-037 收口将 user-menu menus 轨道前端接线归入 T-FE-015**（设计定案）：菜单可见性 v3.5 ∃op 派生方案后端已实现（`/auth/user-menu` 双轨下发过滤后 menus 树），前端 T-FE-041 纯静态路由模式未接线（menus 存 user store 备用，`initRouter` 传空数组）；T-FE-015 作为首个联调任务在登录链路切真实 user-menu 时完成菜单栏接线（从本地静态路由/mock 角色矩阵切后端派生 menus 树）；不立独立任务、不改 `filterNoPermissionTree`（三处页设文档 🔧 登记同步收口）。同时 T-PERM-037 done 解锁 T-FE-015~022 联调依赖（T-FE-037 二期另待 T-ADMIN-021）。
- 2026-08-31：**menus 接线三项设计定案**：①会话恢复 = 每次已登录启动/F5 重取 `/auth/user-menu`，失败 fail-closed 空菜单+可重试，不持久化 menus、不回退全量静态菜单（现状 menus 仅登录时拉取进内存 store、F5 丢失，接线若只改侧栏读 store 会刷后菜单变空——恢复策略消除该缺口）；②T-FE-015 完成后统一补全页导航/F5/直达 URL 冒烟验收作为本 plan 归档门禁（T-FE-016~022 不串行依赖 015）；③T-FE-015 启动时补建任务卡（menus 接线属跨页基础设施且承载两项设计定案，详细范围入卡）。另：admin 契约四处「当前差距」陈旧措辞对齐实现（T-PERM-037 收口补遗，见该任务卡完成记录第 7 条）。
- 2026-08-31：**T-FE-015 收口（Phase 3 首个联调任务 done，验收五场景全过——3 个浏览器实测 + 场景 4 API 层门禁验证 + 场景 5 代码层保证）**：组织与用户页 22 端点 mock→真实（含 member-candidates 新增接入与 perm 域角色写接口切换）；menus 后端树直接渲染接线（会话恢复 fail-closed + /menu-retry 重试闭环实测）；bootstrap 固定图扩容（业务门禁 +17、菜单种子 15 行、Gateway 端点 +20、默认组织树种子——后两项为联调暴露的系统性缺口经设计定案补入）；联调修复 7 个潜伏缺陷（后端 5：org sort NULL 500/effectiveBits 缓存反序列化/三处 MyBatis-Flex XML 分页；前端 2：RouterLink name 冲突致菜单渲染失败/getTopMenu 叶子树假设）。Gateway 端点注册缺口与默认树死锁两个系统性问题的解法为后续 T-FE-016~022 各页复用模式（每页联调须按 T-FE-015 模式扩 bootstrap Gateway 端点注册，见下方联调完成标准第 4 条）。menus 接线完成即归档条件中的全页导航/F5/直达 URL 冒烟门禁自此解锁（归档前统一执行）。终态见任务卡完成记录。
- 2026-09-01：**T-FE-016 收口（Phase 3 第二个联调任务 done）**：角色管理页 Gateway bootstrap 清单 +4 端点（update/remove/move/detail；tree/create 先在册，list 属冲突规则页 T-FE-020 不注册）；编辑弹窗 extra 回显接线（设计定案：树节点契约无 extra，按业务键拉 detail 回填，失败/未命中/无 externalId 三态回落节点数据——role-manage.md §8 既定路径）+ extra 清空协议 extraClear（冒烟暴露「清空无协议通道」，对齐 T-PERM-028 资源域标准方式，全仓单一清空协议）；mock/role-manage.ts 退役删除 + hook mock 专属 ROOT 跳过分支清除；业务门禁零新增（ROLE:MANAGE inherit_mask 含 VIEW，全档已在固定图）；联调未发现前后端缺陷（7 DTO 逐一比对零漂移）；PgIT 计数 33/32/76→37/36/80；开发库按 runbook 重建（固定图定义变更）后浏览器冒烟六场景全过。
- 2026-09-01：**T-FE-016 双轨子代理评审收口（代码轨 0P1+1P2+3P3、文档轨 0P1+2P2+6P3，逐条核实全属实全处置；2 存疑经用户决策）**：测试锁强采 DB 级——UserRoleWriteProjectionPgIT updateRole 场景扩展 extraClear 设值→清空→DB `extra IS NULL` 断言（旧实现普通 update(entity) 忽略 null 列下必红，资源域 PgIT 同款先例）；bootstrap-admin 自我锁面维持现状登记（写保护仅拒本地投影角色为 T-PERM-022 既定语义）；数字订正 883→885（首轮端口干扰轮 DualInstance 仅计 1 test）；已知差距登记三条（extra 非 JSON 500 无校验直达 PG / SDK Feign getRole 仍 IdReq 与控制器业务键漂移系 T-PERM-022 遗漏 / bootstrap-admin 可删）；卫生批：AccessBootstrapPgIT 注释 33/32→37/36、mock 注释 3 处 + conflict-rule.md 失实引用清理、role-manage.md last_reviewed 重复短语修复、runbook blockquote 前缀 + 门禁计数消歧 + HS256 措辞软化（RFC 7518 建议非强制）、架构 §14.3 清单表补权威指针。
