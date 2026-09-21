---
doc_type: task
id: T-PERM-073
title: 按需来源解释、授权界面与对账
status: done
plan: —（无所属计划；自动授权实施序列）
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md#explain
  - docs/design/dependency-auto-grant.md#admin-ui
  - docs/design/dependency-auto-grant.md#reconciliation
  - docs/design/access-service-api-contract.md
  - docs/design/extension-guide.md
  - docs/design/frontend/resource-dependency.md
  - docs/design/frontend/permission-grant.md
depends_on:
  - T-PERM-072
blocks: []
acceptance:
  - "explain 按 M5 一致视图调用 072 共享核心，输出各操作/条件事实、直接推导与显式来源，覆盖关系仅解释触发匹配，不解释删行压制；区分 desired 与 actual 及漂移，逻辑节点不依赖物理 AUTO_DEP ID。"
  - "角色业务键入参、DEPENDENCY:VIEW 类型级服务端门禁、管理 API 固定图注册，服务凭证不得调 explain；不恢复已删除的通用用户排查端点族。"
  - "多来源、窄条件后到、宽来源撤销后窄来源仍可追溯；完整路径按需展开，分页/截断显式标识，不限制真实物化计算；不使用持久 support/回填/格式版本门禁。"
  - "角色授权页区分显式/自动，自动只读且来源定位到显式授权；预览显示授撤影响与其他来源保留结果并标明仅供参考，保存由服务端按最新事实校验与重算，影响变化不要求重新预览确认，不新增强制预览凭证或版本匹配。"
  - "preview-grant-plan 复用纯计划准备与校验，禁止直接调用会写 INLINE 的现役 prevalidate 或写后回滚模拟；新条件用请求条目临时身份，既有 INLINE 就地编辑沿用身份且预览不落库。"
  - "验证 A/C 支持 B：预览撤 A 保留 B，保存前 C 被撤销，保存撤 A 实际回收 B 并刷新真实结果；保存后的刷新失败与预览失败均显式标识，不能把旧预览或零影响冒充实际结果；既有保存门禁与失败回滚保持。"
  - "UI 当场说明独立目标权限、scope_all/父继承不触发及资源停用不暂停自动传播的边界，不把自动授权存续展示为运行时必然放行；依赖页只读，以同步状态/失败诊断为主，变更指向所属服务 manifest 发布，不提供手工新增/编辑/删除或跨 owner override。"
  - "对账复用同一推导检查声明/编译/结果和 desired/actual，复用既有任务设施；不检查 support、不另写闭包算法、不以对账替代正常撤权。"
  - "接入指南区分资源独立同步、依赖独立发布与 SDK 可选协调，正式 explain/预览契约与前端设计回写，来源读取/预览失败/权限不足/截断等适用场景验证。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-21
---

# T-PERM-073 按需来源解释、授权界面与对账

## 背景

[已采纳简化设计](../design/dependency-auto-grant.md)不持久化完整来源路径。管理员从授权页理解自动结果，来源解释按需计算，对账检查实际落库与应有结果。

## 当前口径

依赖 072 的共享推导/精确去重核心。M5 已定预览仅供参考、保存按最新事实重算，其单次一致视图、纯计划准备、预览入口/门禁与展示协议按设计 §11/§12 和契约 §11.4.1/§12.3.1 实施；M3 已确定依赖管理只读，写入口关闭由 071 完成。解释自动授权生成来源，不承诺用户当下 allowed/denied 或历史全路径回放。

## 范围

来源解释端点、授撤影响预览接线、角色授权展示、依赖诊断页、对账与接入指南。沿既有端点/组件演进，规则以设计与正式契约为准。

## 验收对照

见 acceptance。对源权限 A/C 同时支持 B 的场景，展示“撤 A 后 B 仍由 C 保留”；来源读取失败不能误报无来源，desired/actual 不一致不能冒充已生效。

## 非目标 / 遗留

不新建 support 表、推导历史事件库、通用权限排查或后台队列框架。自动修复若引入新写接口须先明确其授权与事务范围，不隐藏在只读对账中。

## 完成记录（2026-09-21）

**启动定案（四项，registry 2026-09-21 同日登记）**：①对账=bootstrap 种子默认停用 sys_job（手动触发/按需启用，默认零后台负载）；②依赖页声明诊断=新增 declaration-status 只读端点；③explain 前端入口=仅依赖页查看器（授权页 AUTO_DEP 维持标注）；④预览触发=变更清单手动「预览影响」按钮+抽屉（保存独立）。

**后端**：
- `PermissionGrantPlanDomainService` 拆纯准备：`prepare`（与保存完全同源校验，INLINE 以负数合成 ID 暂挂）+ `prevalidate` = prepare + `materializeInlineConditions`（保存阶段物化 + 合成 ID 残留断言）；畸形计划不再产生瞬态内联行；既有 93 项计划域/AppService 单测全过（行为保持）。
- `AutoGrantInsightDomainService`（grant 域）：一致视图装载（rows/图/操作/声明/渲染上下文，分批防参数上限）+ explain（target 反向闭包 / 全集 BFS 深度限额、nodeKey 按输出 FactKey 元组排序、边声明引用按编译键匹配+触发判定与推导核心同口径含 inheritMask、漂移=角色级 desired/actual）+ preview（prepare + before/after/affectedOld 三次共享推导：removed/added/retained + 根来源经直接前驱 DAG 拓扑传播 + maxItems 全局预算）。
- 端点：`role-resource-permission/preview-grant-plan`（ROLE:MANAGE+启用角色+REPEATABLE_READ 只读）；`resource-dependency/explain`（DEPENDENCY:VIEW+角色业务键 20001+target 解析 20004/20005/20008/20006/20007）；`resource-dependency/declaration-status`（发布状态+声明行含 REJECTED 原因，载荷损坏降级不遮蔽状态面）。
- 对账：`AutoGrantReconcileDomainService`（声明↔编译边一致/失效残留/逐角色 desired diff，种子与防御层口径同物化器，只读不修复）+ `AutoGrantReconcileJob`（@JobInvocable，invokeTarget=autoGrantReconcileInvoker.reconcile）；任务设施最小扩展——`JobInvokeDomainService.invoke` 返回 Object，String 返回值作任务日志成功消息（诊断摘要通道，void 维持缺省）。
- Bootstrap：三条新 ApiRoute（preview/explain/declaration-status，DEPENDENCY:VIEW 族零新增业务门禁）+ `jobSeeds()` sys_job 种子（insert-if-absent 不覆盖管理员配置）；AccessBootstrapPgIT 计数断言 87→90/86→89/132→135。
- Mapper 增补：selectValidRoleIds / declaration.selectByTenantId / manifestSync.selectByTenantId / sysJob.selectValidByInvokeTarget（均 XML 同步）；声明读取面经 `DependencyCompilationDomainService.loadDeclarations` 暴露（跨包不互读 mapper）。

**前端**：
- 依赖页（只读诊断两抽屉，2026-09-21 定案入口）：`DeclarationStatusDrawer`（服务发布状态表+声明表 REJECTED 原因中文释义）+ `ExplainViewerDrawer`（角色业务键输入+可选目标级联+节点四象限状态+点选节点沿直接边本地展开+截断/漂移显式提示）；纯函数 `utils/explain-view.ts`。
- 授权页：底部保存条「预览影响」按钮（edit 态+草稿非空）→ `GrantPreviewDrawer`（三组影响+仅供参考横幅+截断/既有漂移独立提示+M5 边界说明四要素+读取时间）；保存独立——成功响应即真实结果、失败保留标红整体重试（既有语义），预览失败显式「无法预览」；纯函数 `utils/preview-impact.ts`。
- API 层：`api/resource-dependency.ts`（explain/declaration-status 全类型）、`api/permission-grant.ts`（previewGrantPlan 全类型）；不重新引入已退役 mock 层（页面全真实接口）。

**验证**：
- `AutoGrantInsightPgIT`（真实 PG+Redis，14 用例全绿）：explain 全集 DAG/声明引用/target 闭包/截断 totals/漂移与孤立 actual/门禁与角色 20001/目标解析 20004·20005·20006；preview retained（A/C 双源撤 A 保留 B，seed=c 显式授权）/removed（末源撤销）/added+PREVIEW_INLINE 临时身份（creates[0]，零残留）/零数据库写入/门禁与停用角色 20003/既有漂移独立标识；declaration-status（REJECTED RESOURCE_MISSING+发布状态行）；对账（干净态→直插无来源 AUTO 行+直改库删编译边→漂移与图问题均报出、二次对账仍报=只读不修复）。
- 受影响单测：PermissionGrantPlanDomainServiceImplTest/PermissionGrantAppServiceImplTest/DependencyAppServiceImplTest/AutoGrantDerivationTest 86 全过（重构行为保持）。
- 前端：新增 vitest 22（preview-impact 12 + explain-view 10）+ 全量 vitest 406 全过；vue-tsc/eslint/stylelint/vite build 全过。

**双轨评审与处置（2026-09-21）**：代码轨（P1×1+P2×1+P3×4+减法×2）/文档轨（P1×1+P2×1+P3×6）。四项用户拍板（registry 同日登记）：①内联定义校验前移 prepare（预览/保存同源，畸形规则预览即拒）；②sys_job 种子并发双插窗口接受现状+注记（只读双跑无害，不动 DDL/迁移面）；③删 PlannedGrantPlan.updateItemRefs 与 PendingInline*.syntheticId 两处零消费数据面；④explain 无 target 全集收窄为参与推导的种子（契约 §12.3.1 措辞同批修订，PgIT 补非参与种子排除用例）。直接修复：前端 DataView 图标变量、bootstrap 任务种子回归锁（status=0/cron/invokeTarget 经 @JobInvocable 真实解析+String 摘要回传）、对账全量角色扫描去重、loadCompiledEdges javadoc 锁口径、契约 §11.4 头部与两处 071/072「待实施」残留、ConditionRef 形状行补 conditionCode 与 nullable 口径、设计 §12 将来时两句、前端两设计旧句/失效锚/「待实施」标题。任务卡计数口径维持批次惯例（全仓统一另议）。全量回归收口形态两轮 BUILD SUCCESS（第二轮含 E2E，快照补三条路径+签名后），评审修复后受影响面定向复跑全绿。

**文档回写**：契约 §11.4.1/§12.3.1 转已落地+落地注记、§12.3 表登记两端点+declaration-status 正文、frontmatter 链；设计 dependency-auto-grant 交付状态/§11 落地注记/§13 对账定案/§16 M5 行/last_reviewed；前端 resource-dependency §5 已实现回写、permission-grant 演进段回写；extension-guide §4 状态行+§5.3 注记；本卡转 done。CHANGELOG 未触碰（无现役条目惯例变更）。
