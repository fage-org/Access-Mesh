---
doc_type: task
id: T-PERM-058
title: depend_on 子权限单点门禁闭合设计——单点查询的主资源上下文语义
status: proposed
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/query-engine-unification.md#§3
  - docs/design/permission-center/api-contract.md#§6.1
depends_on:
  - T-PERM-057
blocks: []
acceptance:
  - "缝隙定案：现状 selectInstancePermsByBitsBatch 不看 depend_on——单点查子权限行绕过父绑定照放行（query-scopes 面有主资源上下文判定、单点面没有）；设计产出闭合语义并定案"
  - "设计要点（grill 预定向，待本卡定案确认）：可选 parentContext（类型+编码+操作）入参；不传时 depend_on 行是否计入为开放决策点（倾向不计入=fail-closed 自动闭合，需核对存量调用面后定案）"
  - "线格式：/auth/check、batch-check 的 parentContext 入参（若定案引入）+ api-contract §6.1 回写；depend_on 行 id 不进线格式（引擎内部解析）"
  - "实施分期：本卡可先出设计定案，实施随 T-PERM-057 落地后的引擎入参扩展（或独立小卡）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-058 depend_on 子权限单点门禁闭合设计

> 状态：proposed（2026-09-09 grill Q9 定案任务化：闭合缝隙的具体设计后置）
> 依赖：T-PERM-057（主资源上下文一等入参的引擎形态先落地）

## 背景

子权限（depend_on）语义为「只在特定业务实例上下文生效」：query-scopes 面已实现主资源上下文判定（父权限命中集合内的子行才参与范围计算，父行条件不过时子行自动失效）。**单点面（/auth/check、hasPermissionByCode）不感知主资源上下文**——直查数据范围类型会绕过父绑定（基础授权=福建、子权限=广东挂 depend_on=报表A:VIEW 时，单点问广东照放行，正确语义应为「在报表A上下文里才放行」）。

## 范围

- 闭合语义设计定案：parentContext 可选入参形态 + 不传时 depend_on 行计入/不计入的取舍（fail-closed 方向 vs 兼容现状）。
- 定案后落 api-contract 与 query-engine-unification.md §3（或其并入后的 implementation §3）。

## 非目标 / 遗留

- AUTO_DEP 依赖自动补全的写入侧接线（生产无产出通道，写入侧概念另行定案，不属查询面）。
