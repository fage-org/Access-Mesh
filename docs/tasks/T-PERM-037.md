---
doc_type: task
id: T-PERM-037
title: 跨页共性接口改造 + api-contract 回写收尾
status: proposed
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
depends_on:
  - T-PERM-022
  - T-PERM-023
  - T-PERM-024
  - T-PERM-025
  - T-PERM-026
  - T-PERM-027
  - T-PERM-028
  - T-PERM-029
  - T-PERM-030
  - T-PERM-031
  - T-PERM-032
  - T-PERM-033
  - T-PERM-034
blocks: []
acceptance:
  - ~~"查询/展示投影轨全局操作位合并（T-PERM-034 评审登记，2026-08-30）"~~ **已失效（2026-08-30 全局操作概念退役，T-PERM-049）**：判定/投影两侧均回归类型专属操作，无投影轨缺口，本条不再实施
  - "处理跨页共用的接口改造（多页共用同一接口时，统一调整一次，不重复逐页改）"
  - "核对 T-PERM-022~034 逐页改造是否覆盖各页 API 核对清单的 🔧❌ 项，补漏缺失项"
  - "改造完成后回写各 Phase 1 前端任务的 API 核对状态（✅）"
  - "设计回写：接口变更统一回写 api-contract.md（避免逐页任务分散回写造成不一致）"
  - "不重复 T-PERM-022~034 已完成的逐页改造，仅做共性收尾与契约回写"
design_writeback:
  required: true
  status: pending
last_updated: 2026-06-29
---

# T-PERM-037 跨页共性接口改造 + api-contract 回写收尾

> 状态：proposed
> 准入：T-PERM-022~034 逐页后端改造完成后
> 职责边界：**不重复逐页改造**。逐页接口实现归 T-PERM-022~034；本任务只管跨页共性接口 + 契约回写收尾。

## 背景

T-PERM-022~034 按页面逐个实现后端接口改造。但有些接口被多页共用（如 `type-definition/*` 被 5.1 业务域 + 6.1 类型定义共用），逐页任务可能各自调整造成不一致。本任务做共性收尾：统一共用接口、补漏、集中回写 api-contract.md。

## 工作方式

1. 汇总 T-PERM-022~034 各任务的改造结果
2. 识别跨页共用接口，统一调整（避免逐页重复改同一接口）
3. 核对各页 🔧❌ 清单是否已全覆盖，补漏
4. 集中回写 api-contract.md + 回写各前端任务核对状态

## 验收标准

见 acceptance。无自动授权 / 动态数据权限内容（分别归 T-PERM-035/036 暂缓项）。
