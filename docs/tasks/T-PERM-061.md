---
doc_type: task
id: T-PERM-061
title: EXT-7：batchCheck 逐条 engine.query 收敛——异构项分组批量化设计
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§3
  - docs/design/permission-center/api-contract.md#§6.1
depends_on: []
blocks: []
acceptance:
  - "背景（审计 S-024 EXT-7，2026-09-11 立项核实）：PermissionCheckAppServiceImpl.batchCheck（:118）逐 item 构造 PermQuery.forAuthCheck 调 engine.query——每 item 一次完整管线（主体角色解析/类型解析/权限行装载/条件互斥评估），N item = N 次全量管线，主体与类型解析部分重复装载 N 次"
  - "2026-09-11 立项核实结论：循环仍在（rg 实核）；EXT-8（enqueueAll）宿主已随内部同步子系统删除，失效不立项"
  - "设计待定点（实施前须先定）：item 异构（resourceTypeCode/resourceCode/operationCode/codeType/domainCode/inheritMode/parentResource 各异）——分组键是否限定 (type, operation, 语义修饰符集)？同组内走引擎批量判定（getDeniedResourceCodes 形态）还是新增引擎批量入口？拒绝原因逐 item 语义（USER_NOT_FOUND 等前置短路）如何与分组结果合并"
  - "性能项非阻断：当前无已知大 item 批量消费方；SDK batch-check 契约（api-contract §6.1）不变"
  - "回归要求：batchCheck 语义锁（逐 item 结果与现行一致，含部分失败 item 拒绝原因）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-11
---

# T-PERM-061 EXT-7：batchCheck 逐条 engine.query 收敛——异构项分组批量化设计

> 状态：proposed（2026-09-11 立项；T-PERM-060 批次内核实登记）
> 依赖：无硬依赖

## 背景

审计 S-024 登记的 EXT-7 无主性能项：`PermissionCheckAppServiceImpl.batchCheck` 对
`req.items()` 逐条构造 `PermQuery.forAuthCheck` 并调用 `engine.query`。每次 engine.query
是完整管线（角色解析、类型解析、权限行装载、条件/互斥评估），批量 N 项 = N 次全量管线，
其中主体角色集与类型解析对同一 (tenant, user) 重复执行 N 次。

T-PERM-057 统一引擎后引擎已具备同类型同操作的批量判定形态（getDeniedResourceCodes 族），
但 batchCheck 的 item 是异构的（类型/操作/修饰符逐项不同），需要先做分组设计再实施，
故独立立项不在 T-PERM-060 小批次内实施。

## 范围

- `PermissionCheckAppServiceImpl.batchCheck`（access-service permission 域）
- 可能涉及的引擎侧批量入口扩展（implementation §3 设计先行）
- api-contract §6.1 batch-check 契约不变（行为等价改造）
