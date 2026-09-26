---
doc_type: task
id: T-PERM-086
title: （R2-T07）父受控子项与 GRANT_LIST 完整事实
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §4.5/§4.6
depends_on:
  - T-PERM-085
blocks: []
acceptance:
  - "ParentRequirement 一层结构、惰性父判定（无子候选不判父、父结果 RunState 内按完整规范化要求记忆）、父项复用阶段函数不递归公开 execute、父固定 SELF/ALLOW/EVALUATE+ENFORCE"
  - "P01~P08 全绿：无父排除子行、有父仅主授权不触发父查询、父失败主行仍生效、GRANT_LIST 父失败整集合 PARENT_DENIED、父 scopeAll 命中即绑定不扩读、共享父一次计算+证据映射全部受影响项、父操作空集不解释为不限操作、根 PRESERVE/SKIP 时父仍 FULL 评估"
  - "G01/G06/G07：无父 GRANT_LIST 子行按存储事实参与原清单流程（装配后隐藏契约保持）；页面筛选 A 且 A-B 互斥时后置过滤不让 A 复活；FACTS 收全所选阶段不漏"
  - "接入角色快照读取的整体 execute 验证 I05（冷/热/混合 miss、首次令牌不重置），复用 T-PERM-084 部件与 T-PERM-085 最小 StageFacts 输出；DATABASE 不读写角色快照"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-086 （R2-T07）父受控子项与 GRANT_LIST 完整事实

## 背景

设计 §4.5 父上下文绑定父授权记录（非父资源）、§4.6 GRANT_LIST 事实完成目标不被短路或分页破坏（报告临时编号 R2-T07）。绑定语义=子行 dependOn ∈ 父命中权限 ID 集（matchedPermissionIds）。

## 范围

- GRANT_LIST 流程：装载→必要父整集合门禁→rawAfterContext→评估→retained→输出后置筛选/范围分桶/展示/分页；本版不把 queryResources 的白名单/过滤提前到授权 SQL（G06 反例）。
- 为操作描述保留 raw 超集（retained 为空不缺操作定义）；描述实际装载与投影沿 T-PERM-085 已定边界由 T-PERM-087 接入。

## 当前口径

- 父要求按 `ParentRequirement` 完整字段值记忆，沿既有构造前归一约定；内部父项使用独立执行表，复用 TYPE_GRANT/INSTANCE 阶段，不递归公开 execute、不调用旧核心。父项固定 SELF/ALLOW/FULL，只认主授权，命中权限 ID 不依赖公开输出开关。
- TARGET_SET 仅有子候选时触发父判定，父失败仍保留独立主授权；无父 GRANT_LIST 保留子行参与完整条件/互斥评估，有父且非空清单先过整体父门禁，绑定后才形成 raw。
- 父门禁拒绝返回 PARENT_DENIED，与成功但无事实区分；按设计 §3.3 的实际执行覆盖约束，GRANT_LIST 记为跳过、事实未收全，不把未运行的清单评估记为完成。该信息为引擎内部执行说明，不新增用户界面或更改外部错误响应。
- 父阶段事实、真实互斥规则与受影响根项关联保存在同一 RunState，查询结束释放；审计提交与 TRACE 仍由 T-PERM-088 接入。

## 验收对照

- P01～P08：QueryStagesTest 覆盖无父排除、父惰性、主行保留、整清单拒绝、scopeAll 绑定不扩读、共享父、空操作拒绝与父固定 FULL；补父 SELF/主行限制、实例回退及父子固定时刻/条件记忆。
- G01/G06/G07、R03：清单保留子行参与互斥，先评估全集再输出；显式角色不做用户角色互斥；目标 FACTS 收全类型与实例阶段，沿用既有阶段反例。
- I05：整体 execute 冷/热/混合角色快照验证、跨 SQL 分块首次令牌复用、剩余 TTL 递减与耗尽不回填；DATABASE 模式零角色快照读写。
- QueryExecutionPgIT：真实 SQL/Redis 验证热清单下父权限数据库拒绝、互斥筛空不污染原始快照、事务内 DATABASE 读到本事务写入且不修改快照。

## 非目标 / 遗留

- queryScopes 外层父对象存在性预检查（OBJECT_KEY_NOT_FOUND）行为保持，属迁移卡 T-PERM-090。
- 输出描述/有效操作/展示由 T-PERM-087、根审计提交/TRACE 由 T-PERM-088、生产消费者接线由 T-PERM-089 起承接；新引擎仍不注册 Bean。

## 完成记录

- 2026-09-26：父要求与 GRANT_LIST 已接入新 execute；复用既有阶段/领域评估及 QueryReadSupport，未增加生产接线、外部协议或缓存目录。
- 反例验证：首批新增父/清单路径在旧骨架上有 10 项因未实现而失败；实现后 `mvn test -pl access-service -Dtest=QueryStagesTest,QueryExecutionEngineTest,QueryExecutionPgIT` 定向通过 58 项单测与 13 项真实 PG/Redis 测试。追加的父实例回退/父子时刻用例由下述全量覆盖。
- 收口回归：`mvn test -T 1C`，2026-09-26 16:43 完成，2192 项、零失败/错误/跳过；access-service 单测 1532、容器 382（含 heavy）、跨服务 E2E 16，11 模块全部成功。完整命令输出落盘后按各 execution 汇总核对。
- 代码轨实证核对通过：父/子候选隔离、固定父策略、主行保留、空集 SQL 守卫、快照原始载荷与令牌、状态释放、内部项隔离及技术异常传播；无未解决缺陷、待决策项或过度设计可裁剪项。
- 文档轨实证核对通过：设计 §4.1/§4.3/§4.5/§4.6/§5.4、任务验收/后续边界、计划/看板状态与残留扫描一致；无未解决缺陷或待决策项。所属计划仍活跃，本卡保留原位。
