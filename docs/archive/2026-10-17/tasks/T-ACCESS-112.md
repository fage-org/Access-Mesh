---
doc_type: task
id: T-ACCESS-112
title: 已完成导出产物领取边界
status: done
plan: docs/archive/2026-10-17/permission-lifecycle-boundaries-plan.md
domain: example-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/services/example-service.md
  - docs/design/extension-guide.md
depends_on: []
acceptance:
  - 契约明确生成完成后的独立领取边界与身份要求
  - 示例与接入指南不再将异步最终检查泛化到已完成产物领取
  - 已有测试覆盖本人领取和跨用户跨租户拒绝，行为保持
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-073](../../../pending-problems.md#q-073)，实施范围已于 2026-10-17 确认。

## 范围

已完成产物允许原提交者领取，之后源报表 EXPORT 撤权不撤回产物领取资格；保留接口准入、租户与用户归属校验以及提交和执行时检查。不新增领取时 EXPORT 检查。

## 当前口径

以 design_refs 中的现行契约及本任务范围为准；新增设计取舍必须由用户决策。

## 验收对照

- [x] 契约明确生成完成后的独立领取边界与身份要求
- [x] 示例与接入指南不再将异步最终检查泛化到已完成产物领取
- [x] 已有测试覆盖本人领取和跨用户跨租户拒绝，行为保持

## 非目标 / 遗留

本卡不扩大到其他待解决问题。


## 完成记录

2026-10-17：后端根命令 `mvn test -T 1C` 2944 项通过，零失败/错误/跳过（含 E2E/heavy）；前端 611 项与类型、lint、45 组 DTO 对账、构建通过。双轨评审及定向证据见[验收记录](evidence/lifecycle-boundaries/verification.md)。
