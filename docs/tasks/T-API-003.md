---
doc_type: task
id: T-API-003
title: check 族三端点结果记录全量回传（推翻 T-API-002 check 族裁剪）
status: proposed
plan: docs/plans/permission-query-unification-plan.md
domain: cross-service
design_refs:
  - docs/design/permission-center/api-contract.md#§6.1
  - docs/design/permission-center/api-contract.md#§6.2
  - docs/design/permission-center/api-contract.md#§6.6
  - docs/design/permission-center/core-flows.md#§15
  - docs/tasks/T-API-002.md
depends_on: []
blocks: []
acceptance:
  - "三端点同口径全量回传：/auth/check、/auth/batch-check、/auth/check-interface 响应恢复结果记录（matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 回传线格式；PermResult 内部已持有数据面）；Query* 六字段裁剪维持不变（推翻范围仅 check 族）"
  - "双副本 DTO 同批改：access-service permission/dto/resp 与 perm-sdk/perm-common dto/resp 的 AuthCheckResp/BatchAuthCheckResp/CheckInterfaceResp 两份同形；perm-common 先 install 再编译下游（SNAPSHOT 陷阱）"
  - "回归锁改写：CheckFamilyWireShapeTest 的 RETIRED_ID_FIELDS 防回潮负向锁、containsExactly 快照、双副本同形锁按新线格式改写；PermissionFeignClientContractTest 契约快照更新"
  - "文档回写：api-contract §6.1/§6.2/§6.6 已删除字段条目改写为回传口径 + §5.7 注记更新；core-flows §15 检查点改写；decision-registry 已推翻节迁移收尾核对；T-FE-043 登记项（来源类展示走业务键）随重设计处置"
  - "e2e/gateway 断言面核对（现仅断言 allowed 信封，预期零影响）；收口回归 mvn test -T 1C"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-API-003 check 族三端点结果记录全量回传

> 状态：proposed（2026-09-09 grill Q10 + D1 定案：推翻 T-API-002 的 check 族裁剪，三端点一并回传）
> 依赖：无（建议 T-PERM-057 之后实施以避免 check 管线二次触碰；非硬依赖）

## 背景

2026-09-06 T-API-002 定案裁剪 SDK 直连端点内部 id（check/batch-check/check-interface 的 matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId）。2026-09-09 统一引擎定案（消费方模型：调用方根据结果记录自行判定）推翻该裁剪的 check 族部分，三端点同口径恢复全量回传；Query* 响应族六字段裁剪不在推翻范围。

## 范围

- 三端点响应 DTO（双副本）恢复结果记录字段；契约测试与负向锁按新口径改写；文档回写。
- registry 处置核对：2026-09-06 行的 check 族部分已移入「已推翻」节（2026-09-09 登记），本卡落地后核对新口径行为与登记一致。

## 非目标 / 遗留

- Query* 响应族（query-resources/query-scopes 等）线格式不变。
- 权限视图/排查端点（T-PERM-059 删除面）不含在内。
