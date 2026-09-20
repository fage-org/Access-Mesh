---
doc_type: task
id: T-PERM-072
title: 自动授权物化与共享推导
status: proposed
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
  - "INLINE 属主与派生引用按统一时序回收；grant_dep_id 不作为单边清理依据，实际读者核查后收敛。"
  - "按 M4 复用租户 RESOURCE_ENTITY 共同串行边界，实现编译/新增种子一致性、树锁族序和多角色顺序，覆盖新种子未提交时删边、删除/定义变化交错与事务中断回滚；缓存 afterCommit。"
  - "操作位修改/删除引用拒绝与错误码回写；schema INLINE 注释、引擎流程/实现及正式契约与最终代码一致，真实 SQL/并发/端到端验收完成。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-20
---

# T-PERM-072 自动授权物化与共享推导

## 背景

[简化设计](../design/dependency-auto-grant.md)采用写时物化，取消逐种子逐完整路径存储。授权存续依据是完整 desired 重算，来源解释复用逻辑推导，不另建真相源。

## 当前口径

沿 071 承接 078 的实施前结论；M2 已定保留推导事实与精确去重，真实引擎验证仍须完成，M4 按设计 §8 的共同树锁与入口矩阵实施，禁止仅锁反查到的角色。scope_all/父继承不作种子，权限含义是可独立使用的目标权限。T-PERM-075 的主体有效角色结果需组合核对，但本物化器按角色事实计算，不把 ROLE_MUTEX 混入依赖图或将 075 设为硬前置。

## 范围

共享推导核心、规范化、AUTO_DEP diff、完整生命周期触发、INLINE 回收、并发/事务/缓存失效和审计。沿用权限查询框架与能力包领域服务，不另写鉴权引擎。

## 验收对照

见 acceptance。小型菱形图用于证明没有逐路径写放大；真实引擎差分验证条件/覆盖/互斥语义；交错事务与失败回滚验证撤权。小图实验不代替上述验证。

## 非目标 / 遗留

explain HTTP、预览/界面及后台对账编排归 073。跨系统 export、scope_all 展开、异步重建、完整历史推导回放不进入本卡；不得以取消 support 为由删除锁、同步撤权或审计。
