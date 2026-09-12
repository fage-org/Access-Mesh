---
doc_type: plan
title: 权限查询统一引擎重构
status: archived
domain: permission-center
design_refs:
  - docs/design/permission-center/query-engine-unification.md
  - docs/design/permission-center/implementation.md
tasks:
  - T-PERM-057
  - T-API-003
  - T-PERM-058
  - T-PERM-059
acceptance: "T-PERM-057/T-API-003/T-PERM-058/T-PERM-059 全部 done 或 cancelled；全仓权限查询无引擎外独立管线（canGrant 直查/query-resources AppService 展开/query-scopes AppService 自评管线/getDenied* 手写管线收编清零）；implementation §3 重写为统一引擎版，T-PERM-058/059 design_refs 已重连 implementation §3，query-engine-unification.md 转 superseded"
last_updated: 2026-09-10   # 计划归档：四任务全 done（T-PERM-059 收尾触发，用户拍板「随本卡收尾归档」）；query-engine-unification.md 同日转 superseded
---

# 权限查询统一引擎重构

> **计划已归档（2026-09-10）**：四任务（T-PERM-057/T-API-003/T-PERM-058/T-PERM-059）全部 done；acceptance 各项达成——统一引擎落地、canGrant/query-resources/query-scopes/getDenied* 独立管线收编清零、implementation §3 为唯一权威、query-engine-unification.md 已转 superseded。T-PERM-059 分期出的「视图/排查新形态设计」另立任务承接（待用户启动 grill）。

> 状态：archived（2026-09-10 归档，四任务全 done；定案正文见 [query-engine-unification.md](../../design/permission-center/query-engine-unification.md)——已转 superseded，本计划仅存编排追溯）
> 关联：T-PERM-045 已取消（范围并入 T-PERM-057）；T-PERM-054 维持暂缓（方向已定、方案未定）。

## 目标

- 权限查询收敛为**一个引擎、一套入参、一个结果模型**，消除六套执行形态分叉（query() 六工厂 / getDenied\* 手写管线 / query-resources AppService 展开 / deleteRoles 局部规则 / canGrant 直查管线 / query-scopes AppService 自评管线）。
- 判定面继承（目标闭包「父授权覆盖子」）在引擎层落地并按默认值矩阵启用；目标模式三态判别（TYPE_LEVEL/INSTANCE/LIST）消除「无实例目标」二义。
- 管理面门禁条件评估拉平；冲突过滤入参化；两级互斥过滤点统一归属。

## 非目标

- API 授权模式改造（接口权限由操作权限关联派生）——方向已定（T-PERM-054），方案与实施后置。
- depend_on 单点闭合的具体语义——独立设计任务 T-PERM-058。
- 权限视图/排查的新设计——T-PERM-059 只定删除边界与重设计范围。
- 快照架构改造（保留）、scopeMode 四态、query-scopes 契约形态（维持）。

## 准入条件

- 已满足：2026-09-09 定案登记（decision-registry 五行）+ 终态设计落盘（query-engine-unification.md）。

## 任务清单

| ID | 标题 | 状态快照 |
|---|---|---|
| [T-PERM-057](tasks/T-PERM-057.md) | 权限查询统一引擎重构（收编六套形态 + 目标模式三态 + 判定面继承 + 评估拉平） | ✅ done |
| [T-API-003](tasks/T-API-003.md) | check 族三端点结果记录全量回传（推翻 T-API-002 check 族裁剪） | ✅ done |
| [T-PERM-058](tasks/T-PERM-058.md) | depend_on 子权限单点门禁闭合（单点主资源上下文 + 四面排除） | ✅ done |
| [T-PERM-059](tasks/T-PERM-059.md) | 权限视图/排查删除重设计（删除收口；新形态另立任务） | ✅ done |

> 顺序建议：T-PERM-057 → T-API-003（非硬依赖，但先统一引擎可避免 check 管线二次触碰）→ T-PERM-058（依赖 057）→ T-PERM-059（独立，可并行）。

## 归档条件

四个任务全部 done/cancelled；implementation §3 重写为统一引擎版；T-PERM-058/059 的 design_refs 重连 implementation §3 后，query-engine-unification.md 转 superseded；关联 skill 双副本与 rule 同步完成。

## 当前进度

- 2026-09-09：grill 定案（Q1-Q15）+ D1/D2 补充定案；现状断言经代码级核验修订后落盘设计（目标模式三态化、codeType 归位目标三元组、query-scopes 自评管线补计为第六套形态、evolution superseded 时点定于计划收口）。
- 2026-09-10：**T-API-003 收口 done**——三端点（check/batch-check/check-interface）按 2026-09-09 推翻定案恢复 T-API-002 裁剪前线格式（双副本 DTO 六文件 + PermResultUtils/batchCheck 组装层，PermResult 数据面零引擎改动）；回归锁分族改写（check 族正向快照 + 负向锁收窄 Query\* 五 record + matched 填充真锁）；api-contract §5.7/§6.1/§6.2 与 core-flows §10/§15 分族口径回写；registry 实施定案行登记。
- 2026-09-10：**T-PERM-059 删除收口 done（分期形态）**——三项用户定案（全删 8 端点含 explain（codex 候选明细遗留随之消亡）/ 前端排查页+两 API 文件一并删 T-FE-043 cancel / 新设计方向另立任务）；连动面全清（bootstrap 固定图两行+菜单种子、perm-common SDK 三 DTO 副本+Feign 方法、evaluateDetailed/filterPermMutexWithDrops、契约 §6.8 整节与交叉引用）；「新设计方向产出」验收项按分期口径由后续任务承接。
- 2026-09-10：**T-PERM-058 收口 done**——四项用户定案（单点 fail-closed 不计入 + DEPENDENT_NOT_IN_PARENT_CONTEXT 拒绝原因 / TYPE_LEVEL 仅读侧排除（scopeAll 子行形态保留）/ 清单与快照两处排除 / 一并实施）；引擎 INSTANCE 路径 filterDependentEntries（惰性父判定，LazyParentCheck 两阶段共享）+ TYPE_LEVEL/getDenied\* 管线/组装层排除（SQL 零改动，全部内存过滤）；check/batch-check 增主资源上下文四可选字段（双副本 DTO 同形）；回归锁：引擎单测 5 例 + 契约快照双侧 + 组装层 2 例 + PgIT 真库 1 例。
- 2026-09-09：**T-PERM-057 收口 done**——三条实施定案（角色互斥不归引擎+授权校验另立项 / PermEvalContext 多层条件上下文 / 闭包止步同类型+禁止跨类型改进项登记，registry 三行）；六套形态收编清零 + forScopeQuery 实例条目不可达缺陷修复；回归锁三态单测 3 例 + TargetModeClosurePgIT 5 例 + golden 收敛；全量回归 -T 1C 含 E2E 全绿（BUILD SUCCESS）；implementation §3 重写为统一引擎版、T-PERM-058/059 design_refs 已重连 implementation §3。
