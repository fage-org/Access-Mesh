---
doc_type: task
id: T-ACCESS-065
title: 树过滤完整性与状态口径逐子项定案修复
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §8（org 树）+ §12（资源树查询语义）
depends_on: []
blocks: []
acceptance:
  - "三子项逐项拍板（不可用一次修改假称全解决）：① TreeBuilder.buildTrees 父被 status/enabledOnly 过滤而子保留时子支不可达——定祖先保留或提升为根的策略，公共 TreeBuilder 改动覆盖各消费树（实例裁剪的 V∪祖先链先例已规避该维度，不重复）；② selectResourceTree 固定 status=1 与 selectResourceListPaged/Count 不滤状态的两面——定统一口径；③ 前端父组织名依赖过滤后树显示「未知」——OrgResp 补 parentOrgName 或前端回退策略（若动契约 §8 同步）"
  - "拍板按 decision-question-protocol 举例上报用户；各子项定案与实施可分批落地、但同卡收口"
  - "回归锁逐子项：对应旧形态在旧实现下失败/误显实证；子项③若改动前端，vitest 全绿 + typecheck/lint/build 0（同批 T-FE 卡口径）"
  - "契约 §8/§12 对应树查询语义同步；未发现越权路径的边界结论维持（本项为展示完整性）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ACCESS-065 树过滤完整性与状态口径逐子项定案修复

## 背景

承接 [Q-038](../pending-problems.md#q-038)（合并 Q-039/Q-019）：三子项——① TreeBuilder（infrastructure/util/TreeBuilder.java:57-67）只从 parentId==null 起建树，父被状态过滤而子保留时子支不可达（实例裁剪已规避，状态/类型过滤仍可触发）；② 资源树固定 status=1 而管理列表不滤状态——停用资源管理列表可见、授权树不可见；③ user/index.vue 信息卡与表单从过滤后 orgTree 查父名，父被 orgType 筛选或权限排除时显示「未知」，OrgResp 未提供 parentOrgName。数据和权限过滤后的展示不完整，未发现越权路径。

## 范围

三子项定案+实施；公共 TreeBuilder 消费面盘点（各消费树覆盖核对）。

## 当前口径

子项独立决策：① 的策略空间是「过滤时保留祖先链（推荐候选，与实例裁剪先例同构）或过滤后提升孤儿为根」；② 统一方向待定（树也显示停用+标注，或列表也滤）；③ 服务端补字段或前端回退。状态统一与否、父缺失是否提升为根属不同子项，分别拍。

## 非目标 / 遗留

- 实例裁剪链路（已规避该维度，验证即可）。
