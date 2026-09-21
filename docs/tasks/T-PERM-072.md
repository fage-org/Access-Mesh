---
doc_type: task
id: T-PERM-072
title: 自动授权物化与共享推导
status: done
plan: —（无所属计划；自动授权实施序列）
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md#data-model
  - docs/design/dependency-auto-grant.md#materializer
  - docs/design/dependency-auto-grant.md#triggers
  - docs/design/dependency-auto-grant.md#consistency
  - docs/design/engine/core-flows.md
  - docs/design/engine/implementation.md
  - docs/design/schema/access-service.sql
  - docs/design/access-service-api-contract.md
depends_on:
  - T-PERM-071
blocks: []
acceptance:
  - "按角色 MANUAL 实例主授权种子完整重算，单操作触发、批量装载共享图、条件变体与 desired/actual diff 落地；不建 support 表、不存全路径、不做二遍路径补录。"
  - "物化/解释/对账复用同一纯推导与精确去重核心；逻辑节点按事实键定位，不依赖物理 AUTO_DEP ID，内存记录直接推导关系，不持久化、不要求枚举完整路径。"
  - "M2 仅按同角色资源/操作/条件身份精确去重，保留各操作与条件（含 NULL）变体，不做操作覆盖或条件支配压缩，也不因 MANUAL/类型级覆盖省略自动结果；中间事实继续传播。"
  - "真实引擎验证不同操作 NULL、多条件 OR、宽窄来源撤销、非传递/相互覆盖及互斥；同时推导 VIEW/UPDATE 时保留两行，依入口既有互斥集合与审计口径处理，不以删行规避冲突，不凭 OR 小图声称与覆盖压缩等价。"
  - "共享下游撤销单源保留、撤销末源回收、独立 MANUAL 保留、定义/声明变更及资源删除回收同事务完成；AUTO_DEP 单操作、只读、canGrant=false、depend_on=NULL。"
  - "完整触发面落地；资源单条 DELETE/FULL 缺失删除（含合法完整空清单）同事务回收依赖贡献与受影响自动授权，无其他来源才回收下游结果，不要求客户端额外发 manifest 才撤权；外部失效 dirty 与重传恢复验证。"
  - "仅资源 DISABLE/恢复及 UPSERT/FULL 的 status 切换不改变自动传播或结果；覆盖源/目标/中间资源，停用期间新增合法种子仍可推导，恢复不重建，显式撤权/资源删除/声明变更仍正常收缩；不借此修改资源运行时鉴权语义。"
  - "INLINE 属主与派生引用按统一时序回收；grant_dep_id 定案保留不写不读（Q-022），物化永不写入。"
  - "按 M4 复用租户 RESOURCE_ENTITY 共同串行边界，实现编译/新增种子一致性、树锁族序和多角色顺序，覆盖新种子未提交时删边、删除/定义变化交错与事务中断回滚；缓存 afterCommit。"
  - "操作位修改/删除引用拒绝与错误码回写；schema INLINE 注释、引擎流程/实现及正式契约与最终代码一致，真实 SQL/并发/端到端验收完成。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-21（终态）
---

# T-PERM-072 自动授权物化与共享推导

## 背景

[简化设计](../design/dependency-auto-grant.md)采用写时物化，取消逐种子逐完整路径存储。授权存续依据是完整 desired 重算，来源解释复用逻辑推导，不另建真相源。

## 当前口径

沿 071 承接 078 的实施前结论已全部落地：M2 保留推导事实与精确去重经单测 + PgIT 真实引擎验证；M4 共同树锁与入口矩阵六入口接线核对（apply-grant-plan 与角色删除为新增挂锁入口）。scope_all/父继承不作种子，权限含义是可独立使用的目标权限。T-PERM-075 的主体有效角色结果需组合核对，但本物化器按角色事实计算，不把 ROLE_MUTEX 混入依赖图或将 075 设为硬前置。

## 范围

共享推导核心、规范化、AUTO_DEP diff、完整生命周期触发、INLINE 回收、并发/事务/缓存失效和审计。沿用权限查询框架与能力包领域服务，不另写鉴权引擎。

## 验收对照

见 acceptance。小型菱形图用于证明没有逐路径写放大；真实引擎差分验证条件/覆盖/互斥语义；交错事务与失败回滚验证撤权。小图实验不代替上述验证。

## 完成记录

- **交付物**：共享推导核心 `AutoGrantDerivation`（纯函数：BFS 闭包、事实键=资源+canonical 操作位+条件身份精确去重、条件直传 NULL 并存、前驱 DAG 供 073 解释/对账复用）；物化器 `AutoGrantMaterializationDomainService`（`recompute` 完整 desired 重算 + actual diff 同事务落库、审计 AUTO_DEP_DIFF、INLINE 归零回收统一时序、`recomputeByResourceEntities`/`recycleRoleGrants` 触发面形态）；触发面六入口接线（apply-grant-plan 加 RESOURCE_ENTITY 锁、manifest fullSync、资源单条 DELETE/FULL 缺失删除、操作位/掩码变更与删除守卫 20069、类型删除/所有权变更、角色删除拍板 A 全回收含 AUTHORITY_ROOT + ABSTRACT_ROLE→RESOURCE_ENTITY 全序补锁）。
- **测试**：`AutoGrantDerivationTest` 11 用例（§6.3.1 全表）+ `AutoGrantMaterializationPgIT` 17 用例（真实 PG/Redis 写链路：种子生命周期/条件变体含 NULL 并存/共享来源单源保留末源回收/AUTO-MANUAL 并存/中间传播/停用不变传播/资源 DELETE 与 FULL 缺失删除含完整空清单/manifest 撤依赖/操作守卫 20069 与 inheritMask 解锁重算/类型 20056 拒绝与删除收尾/角色删除含 AUTHORITY_ROOT 分支/INLINE 统一时序回收/事务中断回滚/M4 提交闸门）。
- **回归证据**：`mvn test -pl access-service -DskipTestcontainers=true`（2026-09-21，1308 项 0 失败）；`mvn test -pl access-service -Dtest=AutoGrantMaterializationPgIT`（2026-09-21，17 项 0 失败）；收口全量 `mvn clean test -T 1C`（2026-09-21，BUILD SUCCESS——单测 1308 + 容器 286 + E2E 15 全绿，处置批后复跑全量同为 BUILD SUCCESS）。
- **启动拍板**（registry 2026-09-21 行）：grant_dep_id 保留不写不读（Q-022）；角色删除全回收含授权根（拍板 A）；删类型所有者角色无守卫留观（Q-023）。
- **双轨评审处置**：代码轨 P2×1（deleteRoles 漏 RESOURCE_ENTITY 锁——已补锁）+ P3×6（recompute 装载分批/GrantSource javadoc/INLINE 注释如实化/闸门会合点/覆盖缺口补 5 用例/审计 changeSource 上下文区分——全修）；文档轨 P1×1+P2×4+P3×6（设计 §3.4/§7 定案回写、契约 §11.4/§12.1 矛盾、AGENTS 授权根边界、将来时残留 10+类推 6 处、任务卡/Q-022 口径——全修）；AUTO_DEP 行形状补 DDL CHECK 评审存疑项未采纳（物化器唯一写者 + uk 已限重复，维持现状）。

## 非目标 / 遗留

explain HTTP、预览/界面及后台对账编排归 073。跨系统 export、scope_all 展开、异步重建、完整历史推导回放不进入本卡；不得以取消 support 为由删除锁、同步撤权或审计。grant_dep_id 定案保留不写不读（死列留观 Q-022）；删类型所有者角色无守卫留观（Q-023，授权能力锁死可经 updateType 迁移恢复）。
