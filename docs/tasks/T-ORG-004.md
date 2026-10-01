---
doc_type: task
id: T-ORG-004
title: 组织树配置根节点重叠守卫
status: proposed
plan: docs/plans/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/default-org-tree-user-lifecycle.md §7/§7.1（默认组织树治理/组织树归属解析约定）
depends_on: []
blocks: []
acceptance:
  - "createOrgTreeConfig 把现有树的中间节点设为另一树根时，统一核对所有现有树根的祖先/后代关系，重叠拒绝（错误码定案入册；回归锁以「中间节点另立根」场景实证旧实现放行）"
  - "updateOrgTreeConfig 非默认配置改根路径（orgId 变更，现仅默认树有归属保护、非默认直 set 无守卫）同批补重叠核对——只守 create 兑现不了「多根命中不再可达」，全部根变更写入口（create+update 改根）统一走共享守卫（2026-10-01 codex 复评补；既有 updateNonDefaultConfigRootSkipsGuard 用例随守卫语义重写）"
  - "存量重叠处理定案（订正语句入 runbook 或维持现状+登记），按 decision-question-protocol 举例上报用户后拍板；不假称新写入守卫就消除了存量"
  - "并发窗口判定落卡：两个 createOrgTreeConfig（或 create/update 交错）并发产生互相重叠的根配置（check-then-act 双过检）时，复用 §17 树写锁或唯一约束兜底（并发用例并入回归锁）或拍板无需兜底（依据写明）"
  - "resolveTreeRootExternalId(s) 多根同时命中的依赖遍历顺序问题随守卫收敛后不再可达；若拍板维持存量重叠，则解析口径写明确定性规则"
  - "default-org-tree-user-lifecycle.md §7/§7.1 守卫与解析口径同步；T-ORG-002 默认树归属保护（guardDefaultTreeRescope）不扩围不弱化"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-01
---

# T-ORG-004 组织树配置根节点重叠守卫

## 背景

承接 [Q-024](../pending-problems.md#q-024)：createOrgTreeConfig 可把现有树的中间节点设为另一树根，未检查祖先/后代重叠。多个根同时命中时，`resolveTreeRootExternalId(s)` 的结果依赖遍历顺序——同步树归属解析可能错桶。创建非默认配置不直接改变默认身份池；当前无 create UI，入口为有权限的 API；T-ORG-002 最小守卫安排保持（本项是其外的留观项）。

## 范围

createOrgTreeConfig 新建守卫 + updateOrgTreeConfig 非默认配置改根守卫（全部根变更写入口共享同一重叠核对）；存量重叠处置定案；解析口径同步。

## 当前口径

守卫在写入口（create）；重叠判定覆盖所有现有树根（不只默认树）。

## 非目标 / 遗留

- 默认树自身的删除/恢复守卫（T-ORG-002 已收口）。
