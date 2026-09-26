---
doc_type: task
id: T-PERM-086
title: （R2-T07）父受控子项与 GRANT_LIST 完整事实
status: proposed
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
  status: pending
last_updated: 2026-09-26
---

# T-PERM-086 （R2-T07）父受控子项与 GRANT_LIST 完整事实

## 背景

设计 §4.5 父上下文绑定父授权记录（非父资源）、§4.6 GRANT_LIST 事实完成目标不被短路或分页破坏（报告临时编号 R2-T07）。绑定语义=子行 dependOn ∈ 父命中权限 ID 集（matchedPermissionIds）。

## 范围

- GRANT_LIST 流程：装载→必要父整集合门禁→rawAfterContext→评估→retained→输出后置筛选/范围分桶/展示/分页；本版不把 queryResources 的白名单/过滤提前到授权 SQL（G06 反例）。
- 操作描述装载源用 raw 超集（retained 为空不缺操作定义——沿现行 grok P1 修复口径）。

## 非目标 / 遗留

- queryScopes 外层父对象存在性预检查（OBJECT_KEY_NOT_FOUND）行为保持，属迁移卡 T-PERM-090。
